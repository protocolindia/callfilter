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
            if (isGlobal) {
                globeBadge.setVisibility(View.VISIBLE);
                String reason = (e.rulePattern != null && !e.rulePattern.isEmpty()) ? e.rulePattern : "Global list";
                ruleView.setText("GLOBAL · " + reason);
                reasonPill.setText(reason);
                reasonPill.setVisibility(View.VISIBLE);
            } else {
                globeBadge.setVisibility(View.GONE);
                ruleView.setText(e.ruleType.toUpperCase() + (e.rulePattern.isEmpty() ? "" : " · " + e.rulePattern));
                if (e.reason != null && !e.reason.isEmpty()) {
                    reasonPill.setText(e.reason); reasonPill.setVisibility(View.VISIBLE);
                } else { reasonPill.setVisibility(View.GONE); }
            }
            container.addView(row);
        }
    }
}
