package pro.onephone.callfilter;

import androidx.appcompat.app.AlertDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CallLog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rules screen: create new PREFIX/SUFFIX/RANGE rules (form at top) and view,
 * search, and delete the active rules below. Shows the matched contact name
 * (phone book first, then recent-call cached names).
 */
public class RulesActivity extends AppCompatActivity {

    private LinearLayout container;
    private TextView countLabel, emptyView;
    private EditText searchField;
    private String filter = "";

    // Add-rule form
    private Spinner countryDial;
    private EditText patternInput;
    private TextView btnTypePrefix, btnTypeSuffix, btnTypeRange;
    private TextView btnAccept, btnReject;
    private View rangeInputsRow;
    private EditText rangeBeforeInput, rangeAfterInput;
    private TextView rangeSummary;
    private String currentType = Rule.TYPE_PREFIX;

    private RulesManager rulesManager;

    // normalized number -> name, harvested from the call log (matches Recent Calls)
    private final Map<String, String> callLogNames = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rules);

        rulesManager = RulesManager.getInstance(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        container   = findViewById(R.id.rulesListContainer);
        countLabel  = findViewById(R.id.rulesScreenCount);
        emptyView   = findViewById(R.id.rulesScreenEmpty);
        searchField = findViewById(R.id.rulesSearch);

        // Add-rule form views
        countryDial      = findViewById(R.id.countryDialSpinner);
        patternInput     = findViewById(R.id.patternInput);
        btnTypePrefix    = findViewById(R.id.btnTypePrefix);
        btnTypeSuffix    = findViewById(R.id.btnTypeSuffix);
        btnTypeRange     = findViewById(R.id.btnTypeRange);
        rangeInputsRow   = findViewById(R.id.rangeInputsRow);
        rangeBeforeInput = findViewById(R.id.rangeBeforeInput);
        rangeAfterInput  = findViewById(R.id.rangeAfterInput);
        rangeSummary     = findViewById(R.id.rangeSummary);
        btnAccept        = findViewById(R.id.btnAccept);
        btnReject        = findViewById(R.id.btnReject);

        setupCountrySpinner();
        setupFormListeners();

        searchField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                filter = s.toString().trim().toLowerCase();
                refresh();
            }
            public void afterTextChanged(Editable s) {}
        });

        maybeDetectCountryFromGeoIP();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCallLogNames();
        refresh();
    }

    // ---------- Add-rule form ----------

    private void setupCountrySpinner() {
        ArrayAdapter<CountryData> adapter = new ArrayAdapter<>(this,
            R.layout.spinner_item, CountryData.LIST);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        countryDial.setAdapter(adapter);
        countryDial.setSelection(CountryData.findIndexByIso("IN"));
    }

    private void maybeDetectCountryFromGeoIP() {
        GeoIPHelper.detectAsync(new GeoIPHelper.Callback() {
            public void onCountry(String iso) {
                if (iso != null && !iso.isEmpty()) {
                    runOnUiThread(() -> countryDial.setSelection(CountryData.findIndexByIso(iso)));
                }
            }
        });
    }

    private void setupFormListeners() {
        btnTypePrefix.setOnClickListener(v -> selectType(Rule.TYPE_PREFIX));
        btnTypeSuffix.setOnClickListener(v -> selectType(Rule.TYPE_SUFFIX));
        btnTypeRange.setOnClickListener(v -> selectType(Rule.TYPE_RANGE));

        TextWatcher tw = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c2) {}
            public void onTextChanged(CharSequence s, int a, int b, int c2) {}
            public void afterTextChanged(Editable e) { updateRangeSummary(); }
        };
        rangeBeforeInput.addTextChangedListener(tw);
        rangeAfterInput.addTextChangedListener(tw);
        patternInput.addTextChangedListener(tw);

        btnAccept.setOnClickListener(v -> addRule(Rule.ACTION_ACCEPT));
        btnReject.setOnClickListener(v -> addRule(Rule.ACTION_REJECT));

        selectType(Rule.TYPE_PREFIX);
    }

    private void selectType(String type) {
        currentType = type;
        int whiteColor = getResources().getColor(R.color.white, null);
        int dimColor   = getResources().getColor(R.color.subtext, null);

        boolean isPrefix = type.equals(Rule.TYPE_PREFIX);
        boolean isSuffix = type.equals(Rule.TYPE_SUFFIX);
        boolean isRange  = type.equals(Rule.TYPE_RANGE);

        btnTypePrefix.setBackgroundResource(isPrefix
            ? R.drawable.btn_type_active : R.drawable.btn_type_inactive);
        btnTypePrefix.setTextColor(isPrefix ? whiteColor : dimColor);
        btnTypeSuffix.setBackgroundResource(isSuffix
            ? R.drawable.btn_type_active : R.drawable.btn_type_inactive);
        btnTypeSuffix.setTextColor(isSuffix ? whiteColor : dimColor);
        btnTypeRange.setBackgroundResource(isRange
            ? R.drawable.btn_type_active : R.drawable.btn_type_inactive);
        btnTypeRange.setTextColor(isRange ? whiteColor : dimColor);

        countryDial.setVisibility(isSuffix ? View.GONE : View.VISIBLE);
        rangeInputsRow.setVisibility(isRange ? View.VISIBLE : View.GONE);
        rangeSummary.setVisibility(isRange ? View.VISIBLE : View.GONE);

        if (isSuffix) {
            patternInput.setHint("e.g. 9494 (matches any country)");
        } else if (isRange) {
            patternInput.setHint("anchor number, e.g. 9876543210");
        } else {
            patternInput.setHint("e.g. 9494");
        }
        updateRangeSummary();
    }

    private void updateRangeSummary() {
        if (!Rule.TYPE_RANGE.equals(currentType)) return;
        String pat = patternInput.getText().toString().trim();
        if (pat.isEmpty()) { rangeSummary.setText(""); return; }
        int before = parseIntSafe(rangeBeforeInput.getText().toString(), 0);
        int after  = parseIntSafe(rangeAfterInput.getText().toString(), 0);
        if (before == 0 && after == 0) { rangeSummary.setText("Enter how many numbers to block"); return; }
        try {
            CountryData cd = (CountryData) countryDial.getSelectedItem();
            String anchor = cd.dialCode + pat;
            String preview = Rule.buildRangePattern(anchor, before, after);
            int dash = preview.indexOf('-');
            String s = preview.substring(0, dash);
            String e = preview.substring(dash + 1);
            rangeSummary.setText("Will block " + (before + after + 1) + " numbers: "
                + s + " \u2192 " + e);
        } catch (Exception ex) { rangeSummary.setText(""); }
    }

    private static int parseIntSafe(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ex) { return dflt; }
    }

    private void addRule(String action) {
        String pat = patternInput.getText().toString().trim();
        if (pat.isEmpty()) {
            Toast.makeText(this, "Please enter a pattern first", Toast.LENGTH_SHORT).show();
            patternInput.requestFocus();
            return;
        }
        String storedPattern;
        String storedType = currentType;

        if (Rule.TYPE_SUFFIX.equals(currentType)) {
            storedPattern = pat;
        } else if (Rule.TYPE_RANGE.equals(currentType)) {
            int before = parseIntSafe(rangeBeforeInput.getText().toString(), 0);
            int after  = parseIntSafe(rangeAfterInput.getText().toString(), 0);
            if (before == 0 && after == 0) {
                Toast.makeText(this, "Enter how many numbers to block before and after",
                    Toast.LENGTH_LONG).show();
                rangeBeforeInput.requestFocus();
                return;
            }
            CountryData cd = (CountryData) countryDial.getSelectedItem();
            String anchor = cd.dialCode + pat;
            storedPattern = Rule.buildRangePattern(anchor, before, after);
        } else {
            CountryData cd = (CountryData) countryDial.getSelectedItem();
            storedPattern = cd.dialCode + pat;
        }

        boolean added = rulesManager.addRule(storedPattern, storedType, action);
        if (!added) {
            Toast.makeText(this,
                "\u26A0 A " + storedType.toUpperCase() + " rule for " + storedPattern + " already exists",
                Toast.LENGTH_LONG).show();
            return;
        }
        patternInput.setText("");
        rangeSummary.setText("");
        Toast.makeText(this,
            (Rule.ACTION_ACCEPT.equals(action) ? "\u2713 ACCEPT rule added: " : "\u2717 REJECT rule added: ")
                + storedPattern,
            Toast.LENGTH_SHORT).show();
        refresh();
        SyncManager.getInstance(this).syncRulesAsync();
    }

    // ---------- Name resolution ----------

    private void loadCallLogNames() {
        callLogNames.clear();
        try {
            String[] proj = { CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME };
            try (Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI, proj,
                    null, null, CallLog.Calls.DATE + " DESC")) {
                if (c != null) {
                    int max = 500;
                    while (c.moveToNext() && max-- > 0) {
                        String num  = c.getString(0);
                        String name = c.getString(1);
                        if (num == null || name == null || name.isEmpty()) continue;
                        String key = ContactsCacheManager.normalize(num);
                        if (!callLogNames.containsKey(key)) callLogNames.put(key, name);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private String resolveName(String number) {
        ContactsCacheManager contacts = ContactsCacheManager.getInstance(this);
        String n = contacts.getName(number);
        if (n != null && !n.isEmpty()) return n;
        return callLogNames.get(ContactsCacheManager.normalize(number));
    }

    // ---------- Rules list ----------

    private void refresh() {
        rulesManager.reload();
        List<Rule> all = rulesManager.getRules();

        List<Rule> rules = new ArrayList<>();
        for (Rule r : all) {
            if (filter.isEmpty()) { rules.add(r); continue; }
            String pat = r.getPattern() == null ? "" : r.getPattern().toLowerCase();
            String nm  = resolveName(r.getPattern());
            String nml = nm == null ? "" : nm.toLowerCase();
            if (pat.contains(filter) || nml.contains(filter)) rules.add(r);
        }

        countLabel.setText(all.size() + (all.size() == 1 ? " rule" : " rules"));

        container.removeAllViews();
        if (rules.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            emptyView.setText(filter.isEmpty()
                ? "No rules yet. Add one using the form above."
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

            String contactName = resolveName(r.getPattern());
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
                        rulesManager.removeRule(r.getId());
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
