package pro.onephone.callfilter;

import android.Manifest;
import androidx.appcompat.app.AlertDialog;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CallLog;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recent-calls screen. Reads the device call log (READ_CALL_LOG permission),
 * groups calls by number, sorts by call count desc (Q4 spec — "nearest by
 * number / group by number"), and lets the user tap a row to add a REJECT
 * rule for that number.
 */
public class RecentCallsActivity extends AppCompatActivity {

    private static final int REQ_READ_CALL_LOG = 4101;

    private LinearLayout container;
    private TextView emptyView;
    private final List<NumberGroup> groups = new ArrayList<>();

    private static final int SORT_FREQUENT = 0;
    private static final int SORT_RECENT   = 1;
    private int sortMode = SORT_FREQUENT;
    private TextView sortFrequent, sortRecent, sortHint;

    private static class NumberGroup {
        String number;
        String name;       // cached contact name if known
        int callCount;
        long mostRecentMs;
        String mostRecentType;  // "incoming" | "outgoing" | "missed"
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recent_calls);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        container    = findViewById(R.id.recentCallsList);
        emptyView    = findViewById(R.id.recentCallsEmpty);
        sortFrequent = findViewById(R.id.sortFrequent);
        sortRecent   = findViewById(R.id.sortRecent);
        sortHint     = findViewById(R.id.sortHint);
        sortFrequent.setOnClickListener(v -> { sortMode = SORT_FREQUENT; applySortAndRender(); });
        sortRecent.setOnClickListener(v   -> { sortMode = SORT_RECENT;   applySortAndRender(); });
        updateSortToggleUi();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CALL_LOG}, REQ_READ_CALL_LOG);
        } else {
            loadCallLog();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_READ_CALL_LOG) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadCallLog();
            } else {
                Toast.makeText(this,
                    "Call log permission needed to show recent calls",
                    Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void loadCallLog() {
        groups.clear();
        Map<String, NumberGroup> byNumber = new HashMap<>();
        Cursor c = null;
        try {
            String[] proj = {
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            };
            c = getContentResolver().query(CallLog.Calls.CONTENT_URI, proj,
                null, null, CallLog.Calls.DATE + " DESC");
            if (c != null) {
                int max = 500;  // limit memory
                while (c.moveToNext() && max-- > 0) {
                    String num = c.getString(0);
                    if (num == null || num.isEmpty()) continue;
                    String norm = normalize(num);
                    String name = c.getString(1);
                    int type    = c.getInt(2);
                    long date   = c.getLong(3);

                    NumberGroup g = byNumber.get(norm);
                    if (g == null) {
                        g = new NumberGroup();
                        g.number = num;   // preserve formatting of most-recent display
                        g.name = name;
                        g.mostRecentMs = date;
                        g.mostRecentType = typeLabel(type);
                        byNumber.put(norm, g);
                    }
                    g.callCount++;
                    if (date > g.mostRecentMs) {
                        g.mostRecentMs = date;
                        g.mostRecentType = typeLabel(type);
                        if (name != null && !name.isEmpty()) g.name = name;
                    }
                }
            }
        } finally { if (c != null) c.close(); }

        groups.addAll(byNumber.values());
        applySortAndRender();
    }

    private void applySortAndRender() {
        Collections.sort(groups, new Comparator<NumberGroup>() {
            public int compare(NumberGroup a, NumberGroup b) {
                if (sortMode == SORT_RECENT) {
                    if (a.mostRecentMs != b.mostRecentMs)
                        return Long.compare(b.mostRecentMs, a.mostRecentMs);
                    return b.callCount - a.callCount;
                } else {
                    if (a.callCount != b.callCount) return b.callCount - a.callCount;
                    return Long.compare(b.mostRecentMs, a.mostRecentMs);
                }
            }
        });
        updateSortToggleUi();
        renderList();
    }

    private void updateSortToggleUi() {
        int whiteColor = getResources().getColor(R.color.white, null);
        int dimColor   = getResources().getColor(R.color.subtext, null);
        boolean isFreq = sortMode == SORT_FREQUENT;
        sortFrequent.setBackgroundResource(isFreq ? R.drawable.btn_type_active : R.drawable.btn_type_inactive);
        sortFrequent.setTextColor(isFreq ? whiteColor : dimColor);
        sortRecent.setBackgroundResource(isFreq ? R.drawable.btn_type_inactive : R.drawable.btn_type_active);
        sortRecent.setTextColor(isFreq ? dimColor : whiteColor);
        if (sortHint != null) {
            sortHint.setText(isFreq
                ? "Highest call count first. Tap BLOCK to add a REJECT rule."
                : "Latest call first. Tap BLOCK to add a REJECT rule.");
        }
    }

    private void renderList() {
        container.removeAllViews();
        if (groups.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        emptyView.setVisibility(View.GONE);

        LayoutInflater inf = LayoutInflater.from(this);
        for (final NumberGroup g : groups) {
            View row = inf.inflate(R.layout.recent_call_row, container, false);
            TextView nameView   = row.findViewById(R.id.recentCallName);
            TextView numberView = row.findViewById(R.id.recentCallNumber);
            TextView metaView   = row.findViewById(R.id.recentCallMeta);
            TextView countBadge = row.findViewById(R.id.recentCallCount);
            TextView blockBtn   = row.findViewById(R.id.btnBlockThis);

            String display = (g.name != null && !g.name.isEmpty()) ? g.name : g.number;
            nameView.setText(display);
            if (g.name != null && !g.name.isEmpty()) {
                numberView.setText(g.number);
                numberView.setVisibility(View.VISIBLE);
            } else {
                numberView.setVisibility(View.GONE);
            }
            countBadge.setText(g.callCount + "\u00D7");
            metaView.setText(g.mostRecentType + "  \u00B7  "
                + DateUtils.getRelativeTimeSpanString(g.mostRecentMs,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));

            // Already a reject rule for this number?
            final boolean alreadyBlocked = isAlreadyBlocked(g.number);
            if (alreadyBlocked) {
                blockBtn.setText("\u2717 BLOCKED");
                blockBtn.setEnabled(false);
                blockBtn.setAlpha(0.5f);
            } else {
                blockBtn.setText("\u2717 BLOCK");
                blockBtn.setEnabled(true);
                blockBtn.setAlpha(1f);
                blockBtn.setOnClickListener(v -> showBlockConfirm(g));
            }

            container.addView(row);
        }
    }

    private boolean isAlreadyBlocked(String number) {
        if (number == null) return false;
        String norm = normalize(number);
        for (Rule r : RulesManager.getInstance(this).getRules()) {
            if (!Rule.ACTION_REJECT.equals(r.getAction())) continue;
            // Same-number reject (PREFIX matching that exact number)
            String pat = r.getPattern().replaceAll("[^0-9+]", "");
            if (normalize(pat).equals(norm)) return true;
            if (r.matches(number)) return true;
        }
        return false;
    }

    private void showBlockConfirm(final NumberGroup g) {
        String label = (g.name != null && !g.name.isEmpty())
            ? g.name + " (" + g.number + ")"
            : g.number;
        new AlertDialog.Builder(this)
            .setTitle("Block this number?")
            .setMessage("Add a REJECT rule for " + label + "?\n\n"
                + "Future calls from this number will be silently rejected.")
            .setPositiveButton("Block", (d, w) -> {
                // Route through BlockReasonPickerActivity — it'll add the rule
                // (blockNow=true), record the block, then prompt for a reason.
                String norm = g.number.startsWith("+") ? g.number : g.number;
                android.content.Intent picker = new android.content.Intent(this,
                    BlockReasonPickerActivity.class);
                picker.putExtra(BlockReasonPickerActivity.EXTRA_NUMBER, norm);
                picker.putExtra(BlockReasonPickerActivity.EXTRA_BLOCK_NOW, true);
                startActivity(picker);
                SyncManager.getInstance(this).syncRulesAsync();
                renderList();  // refresh to show "BLOCKED" state
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private static String typeLabel(int t) {
        switch (t) {
            case CallLog.Calls.INCOMING_TYPE: return "Incoming";
            case CallLog.Calls.OUTGOING_TYPE: return "Outgoing";
            case CallLog.Calls.MISSED_TYPE:   return "Missed";
            case CallLog.Calls.REJECTED_TYPE: return "Rejected";
            case CallLog.Calls.BLOCKED_TYPE:  return "Blocked";
            default: return "Call";
        }
    }

    private static String normalize(String n) {
        if (n == null) return "";
        return n.replaceAll("[^0-9+]", "");
    }
}
