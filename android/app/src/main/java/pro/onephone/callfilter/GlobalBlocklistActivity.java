package pro.onephone.callfilter;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import java.util.*;

/**
 * Global Blocklist management screen.
 *
 * Shows ALL reason categories (from BlockReasonsCache + any extra reasons
 * in the downloaded global list) — even if a category has 0 entries.
 * Stats tiles (total / active) are conditionally shown based on admin settings.
 */
public class GlobalBlocklistActivity extends AppCompatActivity {

    private LinearLayout reasonsContainer;
    private TextView     tvLastSync, tvTotalEntries, tvActiveCount, emptyView;
    private Button       btnSync;
    private ImageButton  btnBack;
    private LinearLayout statsTotalCard, statsActiveCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_global_blocklist);

        reasonsContainer = findViewById(R.id.globalReasonsContainer);
        tvLastSync       = findViewById(R.id.globalLastSync);
        tvTotalEntries   = findViewById(R.id.globalTotalEntries);
        tvActiveCount    = findViewById(R.id.globalActiveCount);
        btnSync          = findViewById(R.id.btnGlobalSync);
        btnBack          = findViewById(R.id.btnBack);
        emptyView        = findViewById(R.id.globalEmptyView);
        statsTotalCard   = findViewById(R.id.statsTotalCard);
        statsActiveCard  = findViewById(R.id.statsActiveCard);

        btnBack.setOnClickListener(v -> finish());

        // Check SYSTEM_ALERT_WINDOW permission (needed for popup)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                && !android.provider.Settings.canDrawOverlays(this)) {
            android.widget.TextView warn = new android.widget.TextView(this);
            warn.setText("⚠ Grant 'Display over other apps' permission for call block popup");
            warn.setTextColor(0xFFFF9800);
            warn.setTextSize(12);
            warn.setPadding(32,8,32,8);
            // Insert before the sync text
            ((android.view.ViewGroup) tvLastSync.getParent()).addView(warn,
                ((android.view.ViewGroup) tvLastSync.getParent()).indexOfChild(tvLastSync));
        }

        btnSync.setOnClickListener(v -> {
            // If no overlay permission, offer to open settings
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                    && !android.provider.Settings.canDrawOverlays(this)) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("To show blocked call popups, please enable 'Display over other apps' for CyberGuard AI.")
                    .setPositiveButton("Open Settings", (d,w) -> {
                        android.content.Intent i = new android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    })
                    .setNegativeButton("Skip", null)
                    .show();
                return;
            }
            btnSync.setEnabled(false);
            btnSync.setText("Syncing...");
            GlobalBlocklistManager.getInstance(this).syncAsync((ok, count, err) ->
                runOnUiThread(() -> {
                    btnSync.setEnabled(true);
                    btnSync.setText("Sync Now");
                    if (ok) {
                        GlobalBlocklistManager mgr = GlobalBlocklistManager.getInstance(this);
                        // Count admins with popup images
                        int entriesCount = count;
                        Toast.makeText(this,
                            "Synced " + entriesCount + " entries",
                            Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Sync failed: " + err, Toast.LENGTH_LONG).show();
                    }
                    refresh();
                }));
        });
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        GlobalBlocklistManager mgr     = GlobalBlocklistManager.getInstance(this);
        Map<String, Integer>   counts  = mgr.getCountByReason();    // from downloaded list
        Set<String>            enabled = mgr.getEnabledReasons();

        // ── Stats visibility (controlled by admin settings) ────────────
        if (statsTotalCard != null) {
            statsTotalCard.setVisibility(mgr.isShowTotal() ? View.VISIBLE : View.GONE);
        }
        if (statsActiveCard != null) {
            statsActiveCard.setVisibility(mgr.isShowActive() ? View.VISIBLE : View.GONE);
        }

        tvTotalEntries.setText(String.valueOf(mgr.getTotalEntries()));
        tvActiveCount.setText(String.valueOf(mgr.getEnabledEntryCount()));

        long lastSync = mgr.getLastSyncTs();
        tvLastSync.setText(lastSync > 0
            ? "Last synced " + DateUtils.getRelativeTimeSpanString(
                lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            : "Never synced — tap Sync Now");

        // ── Build ALL reasons: merge BlockReasonsCache + global list ───
        // This ensures ALL reasons show even if count = 0
        List<String> allReasons = new ArrayList<>(BlockReasonsCache.getInstance(this).get());
        for (String r : counts.keySet()) {
            if (!allReasons.contains(r)) allReasons.add(r);
        }

        // First time this screen is opened: default every reason to ON.
        if (!mgr.hasInitializedReasons() && !allReasons.isEmpty()) {
            mgr.setAllReasonsEnabled(allReasons, true);
            mgr.markReasonsInitialized();
            enabled = mgr.getEnabledReasons();
        }

        // Master all enable/disable toggle
        SwitchCompat masterSw = findViewById(R.id.masterReasonSwitch);
        TextView masterStatus  = findViewById(R.id.masterToggleStatus);
        if (masterSw != null) {
            final List<String> reasonsForMaster = new ArrayList<>(allReasons);
            boolean allOn = !allReasons.isEmpty() && enabled.containsAll(allReasons);
            masterSw.setOnCheckedChangeListener(null);
            masterSw.setChecked(allOn);
            if (masterStatus != null) {
                masterStatus.setText(allOn ? "All categories blocking" : "Enable or disable every category at once");
            }
            masterSw.setOnCheckedChangeListener((b, checked) -> {
                GlobalBlocklistManager.getInstance(this)
                    .setAllReasonsEnabled(reasonsForMaster, checked);
                refresh();
            });
        }

        reasonsContainer.removeAllViews();
        emptyView.setVisibility(allReasons.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inf = LayoutInflater.from(this);
        for (String reason : allReasons) {
            int    count = counts.getOrDefault(reason, 0);
            boolean isOn = enabled.contains(reason);

            View card = inf.inflate(R.layout.global_reason_card, reasonsContainer, false);
            TextView     tvReason = card.findViewById(R.id.reasonCardTitle);
            TextView     tvCount  = card.findViewById(R.id.reasonCardCount);
            SwitchCompat sw       = card.findViewById(R.id.reasonCardSwitch);
            TextView     tvStatus = card.findViewById(R.id.reasonCardStatus);

            tvReason.setText(reason);
            tvCount.setText(count > 0
                ? count + (count == 1 ? " number" : " numbers")
                : "No numbers yet");
            sw.setChecked(isOn);
            tvStatus.setText(isOn ? "Blocking" : "Off");
            tvStatus.setTextColor(getResources().getColor(
                isOn ? R.color.reject : R.color.subtext, null));

            // Dim card if no numbers in this category
            card.setAlpha(count > 0 ? 1.0f : 0.65f);

            sw.setOnCheckedChangeListener((btn, checked) -> {
                GlobalBlocklistManager.getInstance(this).setReasonEnabled(reason, checked);
                tvStatus.setText(checked ? "Blocking" : "Off");
                tvStatus.setTextColor(getResources().getColor(
                    checked ? R.color.reject : R.color.subtext, null));
                tvActiveCount.setText(String.valueOf(
                    GlobalBlocklistManager.getInstance(this).getEnabledEntryCount()));
            });

            reasonsContainer.addView(card);
        }
    }
}
