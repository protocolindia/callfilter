package pro.onephone.callfilter;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class BlockedCallsActivity extends AppCompatActivity {

    private LinearLayout container;
    private TextView totalCount, emptyView;
    private Button btnClear;
    private ImageButton btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked_calls);
        container  = findViewById(R.id.blockedList);
        totalCount = findViewById(R.id.totalBlockedCount);
        emptyView  = findViewById(R.id.emptyBlockedView);
        btnClear   = findViewById(R.id.btnClearBlocked);
        btnBack    = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        btnClear.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("Clear log?")
                .setMessage("This will delete the blocked-call log on this device.")
                .setPositiveButton("Clear", (d, w) -> { BlockedCallsManager.getInstance(this).clearAll(); refresh(); })
                .setNegativeButton("Cancel", null).show());
        refresh();
    }

    private void refresh() {
        List<BlockedCallsManager.Entry> entries = BlockedCallsManager.getInstance(this).getEntries();
        totalCount.setText(String.valueOf(entries.size()));
        container.removeAllViews();
        if (entries.isEmpty()) { emptyView.setVisibility(View.VISIBLE); return; }
        emptyView.setVisibility(View.GONE);
        LayoutInflater inf = LayoutInflater.from(this);
        for (BlockedCallsManager.Entry e : entries) {
            View row = inf.inflate(R.layout.list_item_blocked, container, false);
            TextView numView    = row.findViewById(R.id.blockedNumber);
            TextView ruleView   = row.findViewById(R.id.blockedRule);
            TextView timeView   = row.findViewById(R.id.blockedTime);
            TextView globeBadge = row.findViewById(R.id.globalBadge);
            TextView reasonPill = row.findViewById(R.id.blockedReasonPill);
            String cName = ContactsCacheManager.getInstance(this).getName(e.number);
            if (cName != null && !cName.isEmpty()) {
                numView.setText(cName + "  ·  " + e.number);
            } else {
                numView.setText(e.number);
            }
            timeView.setText(DateUtils.getRelativeTimeSpanString(
                e.blockedAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
            boolean isGlobal = "global_list".equals(e.ruleType);
            String by = (e.blockedBy != null && !e.blockedBy.isEmpty()) ? e.blockedBy : null;
            if (isGlobal) {
                globeBadge.setVisibility(View.VISIBLE);
                String reason = (e.rulePattern != null && !e.rulePattern.isEmpty()) ? e.rulePattern : "Global list";
                ruleView.setText("GLOBAL · " + reason
                    + (by != null ? "  ·  by " + by : "  ·  by Global blocklist"));
                reasonPill.setText(reason);
                reasonPill.setVisibility(View.VISIBLE);
            } else {
                globeBadge.setVisibility(View.GONE);
                ruleView.setText(e.ruleType.toUpperCase() + (e.rulePattern.isEmpty() ? "" : " · " + e.rulePattern)
                    + (by != null ? "  ·  by " + by : ""));
                if (e.reason != null && !e.reason.isEmpty()) {
                    reasonPill.setText(e.reason); reasonPill.setVisibility(View.VISIBLE);
                } else { reasonPill.setVisibility(View.GONE); }
            }
            container.addView(row);

            final String reportNumber = e.number;
            final String reportReason = isGlobal
                ? (e.rulePattern != null ? e.rulePattern : "Global list")
                : (e.reason != null ? e.reason : (e.ruleType != null ? e.ruleType : ""));
            TextView reportBtn = row.findViewById(R.id.btnReportFraud);
            if (isGlobal) {
                // Global-blocklist numbers are already known centrally — no need
                // for the user to report them.
                reportBtn.setVisibility(View.GONE);
            } else {
                reportBtn.setVisibility(View.VISIBLE);
                reportBtn.setOnClickListener(v -> confirmReportFraud(reportNumber, reportReason));
            }
        }
    }

    private void confirmReportFraud(final String number, final String blockReason) {
        AuthManager auth = AuthManager.getInstance(this);
        if (!auth.isBackendEnabled()) {
            android.widget.Toast.makeText(this, "Reporting needs an internet connection",
                android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        // Fetch categories, then show a single-select picker.
        BackendClient.get(AuthManager.BACKEND_URL + "/api/fraud-categories",
            new BackendClient.Callback() {
                public void onResult(boolean ok, org.json.JSONObject resp, String err) {
                    runOnUiThread(() -> {
                        org.json.JSONArray arr = resp != null ? resp.optJSONArray("categories") : null;
                        if (!ok || arr == null || arr.length() == 0) {
                            // No categories configured: fall back to a simple confirm.
                            new androidx.appcompat.app.AlertDialog.Builder(BlockedCallsActivity.this, R.style.DarkDialog)
                                .setTitle("Report fraud number?")
                                .setMessage("Report " + number + " as fraud/scam?")
                                .setPositiveButton("Report", (d, w) ->
                                    sendFraudReport(number, 0, blockReason))
                                .setNegativeButton("Cancel", null).show();
                            return;
                        }
                        final int n = arr.length();
                        final String[] names = new String[n];
                        final int[] ids = new int[n];
                        for (int i = 0; i < n; i++) {
                            org.json.JSONObject o = arr.optJSONObject(i);
                            names[i] = o != null ? o.optString("name", "Category") : "Category";
                            ids[i]   = o != null ? o.optInt("id", 0) : 0;
                        }
                        final int[] sel = { 0 };
                        new androidx.appcompat.app.AlertDialog.Builder(BlockedCallsActivity.this, R.style.DarkDialog)
                            .setTitle("Report " + number + " as:")
                            .setSingleChoiceItems(names, 0, (d, which) -> sel[0] = which)
                            .setPositiveButton("Report", (d, w) ->
                                sendFraudReport(number, ids[sel[0]], blockReason))
                            .setNegativeButton("Cancel", null)
                            .show();
                    });
                }
            });
    }

    private void sendFraudReport(String number, int categoryId, String blockReason) {
        AuthManager auth = AuthManager.getInstance(this);
        try {
            org.json.JSONObject body = new org.json.JSONObject();
            if (!auth.getUserId().isEmpty()) body.put("user_id", Long.parseLong(auth.getUserId()));
            body.put("number", number);
            if (categoryId > 0) body.put("category_id", categoryId);
            body.put("reporter", auth.getFullNumber());
            if (blockReason != null && !blockReason.isEmpty()) body.put("block_reason", blockReason);
            String cName = ContactsCacheManager.getInstance(this).getName(number);
            if (cName != null && !cName.isEmpty()) body.put("caller_name", cName);
            BackendClient.post(AuthManager.BACKEND_URL + "/api/report-fraud", body,
                new BackendClient.Callback() {
                    public void onResult(boolean ok, org.json.JSONObject resp, String err) {
                        runOnUiThread(() -> android.widget.Toast.makeText(BlockedCallsActivity.this,
                            ok ? "\u2713 Reported. Thank you for helping keep others safe."
                               : "Could not send report. Please try again.",
                            android.widget.Toast.LENGTH_LONG).show());
                    }
                });
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Could not send report",
                android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
