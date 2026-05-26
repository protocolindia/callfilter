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
 * Shows each reason category as a card with:
 *  - Number count in that category
 *  - Toggle switch to enable/disable blocking for that reason
 *
 * When a reason is enabled, every number in the global list tagged
 * with that reason will be blocked (in addition to the user's own rules).
 *
 * A "Sync now" button fetches the latest list from the server.
 */
public class GlobalBlocklistActivity extends AppCompatActivity {

    private LinearLayout reasonsContainer;
    private TextView     tvLastSync, tvTotalEntries, tvActiveCount;
    private Button       btnSync;
    private ImageButton  btnBack;
    private TextView     emptyView;

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

        btnBack.setOnClickListener(v -> finish());

        btnSync.setOnClickListener(v -> {
            btnSync.setEnabled(false);
            btnSync.setText("Syncing…");
            GlobalBlocklistManager.getInstance(this).syncAsync((ok, count, err) -> {
                runOnUiThread(() -> {
                    btnSync.setEnabled(true);
                    btnSync.setText("🔄 Sync Now");
                    if (ok) {
                        Toast.makeText(this,
                            "✓ Synced " + count + " entries from server",
                            Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this,
                            "Sync failed: " + (err != null ? err : "unknown error"),
                            Toast.LENGTH_LONG).show();
                    }
                    refresh();
                });
            });
        });

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        GlobalBlocklistManager mgr = GlobalBlocklistManager.getInstance(this);
        Map<String, Integer>   counts  = mgr.getCountByReason();
        Set<String>            enabled = mgr.getEnabledReasons();

        int total  = mgr.getTotalEntries();
        int active = mgr.getEnabledEntryCount();

        tvTotalEntries.setText(String.valueOf(total));
        tvActiveCount.setText(String.valueOf(active));

        long lastSync = mgr.getLastSyncTs();
        if (lastSync > 0) {
            tvLastSync.setText("Last synced " + DateUtils.getRelativeTimeSpanString(
                lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        } else {
            tvLastSync.setText("Never synced — tap Sync Now");
        }

        reasonsContainer.removeAllViews();

        if (counts.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        emptyView.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String reason = entry.getKey();
            int    count  = entry.getValue();
            boolean isOn  = enabled.contains(reason);

            View card = inflater.inflate(R.layout.global_reason_card, reasonsContainer, false);
            TextView tvReason  = card.findViewById(R.id.reasonCardTitle);
            TextView tvCount   = card.findViewById(R.id.reasonCardCount);
            SwitchCompat sw = card.findViewById(R.id.reasonCardSwitch);
            TextView tvStatus  = card.findViewById(R.id.reasonCardStatus);

            tvReason.setText(reason);
            tvCount.setText(count + (count == 1 ? " number" : " numbers"));
            sw.setChecked(isOn);
            tvStatus.setText(isOn ? "Blocking" : "Off");
            tvStatus.setTextColor(getResources().getColor(isOn ? R.color.reject : R.color.subtext, null));

            sw.setOnCheckedChangeListener((btn, checked) -> {
                GlobalBlocklistManager.getInstance(this).setReasonEnabled(reason, checked);
                tvStatus.setText(checked ? "Blocking" : "Off");
                tvStatus.setTextColor(getResources().getColor(
                    checked ? R.color.reject : R.color.subtext, null));
                // Refresh summary counts
                tvActiveCount.setText(String.valueOf(
                    GlobalBlocklistManager.getInstance(this).getEnabledEntryCount()));
            });

            reasonsContainer.addView(card);
        }
    }
}
