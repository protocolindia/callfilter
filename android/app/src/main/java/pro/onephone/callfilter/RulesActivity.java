package pro.onephone.callfilter;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

/**
 * Dedicated screen showing the full list of active block/accept rules.
 * Replaces the inline list that used to live at the bottom of MainActivity.
 */
public class RulesActivity extends AppCompatActivity {

    private LinearLayout container;
    private TextView countLabel, emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rules);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        container  = findViewById(R.id.rulesListContainer);
        countLabel = findViewById(R.id.rulesScreenCount);
        emptyView  = findViewById(R.id.rulesScreenEmpty);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        RulesManager rm = RulesManager.getInstance(this);
        rm.reload();
        List<Rule> rules = rm.getRules();
        countLabel.setText(rules.size() + (rules.size() == 1 ? " rule" : " rules"));

        container.removeAllViews();
        if (rules.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        emptyView.setVisibility(View.GONE);

        LayoutInflater inf = LayoutInflater.from(this);
        for (final Rule r : rules) {
            View item = inf.inflate(R.layout.rule_item, container, false);
            TextView typeBadge   = item.findViewById(R.id.ruleTypeBadge);
            TextView patternView = item.findViewById(R.id.rulePattern);
            TextView actionBadge = item.findViewById(R.id.ruleActionBadge);
            TextView delBtn      = item.findViewById(R.id.btnDelete);

            typeBadge.setText(prettyType(r.getType()));
            patternView.setText(r.getPattern());

            boolean isAccept = Rule.ACTION_ACCEPT.equals(r.getAction());
            actionBadge.setText(isAccept ? "\u2713 ACCEPT" : "\u2717 REJECT");
            actionBadge.setBackgroundResource(isAccept ? R.drawable.badge_accept : R.drawable.badge_reject);

            delBtn.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle("Delete rule?")
                    .setMessage(prettyType(r.getType()) + " " + r.getPattern())
                    .setPositiveButton("Delete", (d, w) -> {
                        rm.removeRule(r.getId());
                        SyncManager.getInstance(this).syncRulesAsync();
                        refresh();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
            container.addView(item);
        }
    }

    private static String prettyType(String t) {
        if (Rule.TYPE_RANGE.equals(t) || Rule.TYPE_BETWEEN.equals(t)) return "RANGE";
        return t == null ? "" : t.toUpperCase();
    }
}
