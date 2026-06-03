package pro.onephone.callfilter;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

/**
 * Full list of active block/accept rules.
 * Shows the matched contact name (if the number is in the phone book)
 * and supports live search by number or name.
 */
public class RulesActivity extends AppCompatActivity {

    private LinearLayout container;
    private TextView countLabel, emptyView;
    private EditText searchField;
    private String filter = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rules);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        container   = findViewById(R.id.rulesListContainer);
        countLabel  = findViewById(R.id.rulesScreenCount);
        emptyView   = findViewById(R.id.rulesScreenEmpty);
        searchField = findViewById(R.id.rulesSearch);

        searchField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                filter = s.toString().trim().toLowerCase();
                refresh();
            }
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        RulesManager rm = RulesManager.getInstance(this);
        rm.reload();
        List<Rule> all = rm.getRules();
        ContactsCacheManager contacts = ContactsCacheManager.getInstance(this);

        List<Rule> rules = new ArrayList<>();
        for (Rule r : all) {
            if (filter.isEmpty()) { rules.add(r); continue; }
            String pat = r.getPattern() == null ? "" : r.getPattern().toLowerCase();
            String nm  = contacts.getName(r.getPattern());
            String nml = nm == null ? "" : nm.toLowerCase();
            if (pat.contains(filter) || nml.contains(filter)) rules.add(r);
        }

        countLabel.setText(all.size() + (all.size() == 1 ? " rule" : " rules"));

        container.removeAllViews();
        if (rules.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(filter.isEmpty()
                ? "No rules yet.\n\nGo back to add a PREFIX, SUFFIX, or RANGE rule from the home screen."
                : "No rules match \"" + filter + "\".");
            return;
        }
        emptyView.setVisibility(View.GONE);

        LayoutInflater inf = LayoutInflater.from(this);
        for (final Rule r : rules) {
            View item = inf.inflate(R.layout.rule_item, container, false);
            TextView typeBadge   = item.findViewById(R.id.ruleTypeBadge);
            TextView patternView = item.findViewById(R.id.rulePattern);
            TextView nameView    = item.findViewById(R.id.ruleContactName);
            TextView actionBadge = item.findViewById(R.id.ruleActionBadge);
            TextView delBtn      = item.findViewById(R.id.btnDelete);

            typeBadge.setText(prettyType(r.getType()));
            patternView.setText(r.getPattern());

            String contactName = contacts.getName(r.getPattern());
            if (contactName != null && !contactName.isEmpty()) {
                nameView.setText(contactName);
                nameView.setVisibility(View.VISIBLE);
            } else {
                nameView.setVisibility(View.GONE);
            }

            boolean isAccept = Rule.ACTION_ACCEPT.equals(r.getAction());
            actionBadge.setText(isAccept ? "\u2713 ACCEPT" : "\u2717 REJECT");
            actionBadge.setBackgroundResource(isAccept ? R.drawable.badge_accept : R.drawable.badge_reject);

            delBtn.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                    .setTitle("Delete rule?")
                    .setMessage(prettyType(r.getType()) + " " + r.getPattern())
                    .setPositiveButton("Delete", (d, w) -> {
                        rm.removeRule(r.getId());
                        SyncManager.getInstance(this).syncRulesAsync();
                        refresh();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            container.addView(item);
        }
    }

    private static String prettyType(String t) {
        if (Rule.TYPE_RANGE.equals(t) || Rule.TYPE_BETWEEN.equals(t)) return "RANGE";
        return t == null ? "" : t.toUpperCase();
    }
}
