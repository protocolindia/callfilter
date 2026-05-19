package pro.onephone.callfilter;

import android.Manifest;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ROLE = 101;

    private RulesManager rulesManager;
    private TextView topBarUserInfo, statAccept, statReject, statTotal;
    private TextView blockedCallsCount, schedulesSummary, schedulesBadge;
    private ImageButton btnTopMenu;
    private Switch contactsOnlySwitch;

    // Add rule form
    private Spinner countryDial;
    private EditText patternInput;
    private Button btnTypePrefix, btnTypeBetween, btnTypeSuffix;
    private Button btnAccept, btnReject, btnAddRule;
    private LinearLayout rulesContainer;
    private TextView rulesCountLabel;
    private View blockedCallsCard, cardSchedules;

    private String currentType = Rule.TYPE_PREFIX;
    private String currentAction = Rule.ACTION_REJECT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AuthManager auth = AuthManager.getInstance(this);
        if (!auth.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        SubscriptionManager subMgr = SubscriptionManager.getInstance(this);
        if (subMgr.hasBeenChecked() && !subMgr.isActive()) {
            startActivity(new Intent(this, PaywallActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        rulesManager = RulesManager.getInstance(this);
        bindViews();
        setupCountrySpinner();
        setupListeners();
        refreshUI();
        checkBlockingStatus();
        maybeDetectCountryFromGeoIP();

        SyncManager.getInstance(this).syncRulesAsync();
        SyncManager.getInstance(this).syncContactsAsync();
    }

    private void bindViews() {
        topBarUserInfo   = findViewById(R.id.topBarUserInfo);
        statAccept       = findViewById(R.id.statAccept);
        statReject       = findViewById(R.id.statReject);
        statTotal        = findViewById(R.id.statTotal);
        blockedCallsCount = findViewById(R.id.blockedCallsCount);
        blockedCallsCard = findViewById(R.id.blockedCallsCard);
        contactsOnlySwitch = findViewById(R.id.contactsOnlySwitch);
        countryDial      = findViewById(R.id.countryDialSpinner);
        patternInput     = findViewById(R.id.patternInput);
        btnTypePrefix    = findViewById(R.id.btnTypePrefix);
        btnTypeBetween   = findViewById(R.id.btnTypeBetween);
        btnTypeSuffix    = findViewById(R.id.btnTypeSuffix);
        btnAccept        = findViewById(R.id.btnAccept);
        btnReject        = findViewById(R.id.btnReject);
        btnAddRule       = findViewById(R.id.btnAddRule);
        rulesContainer   = findViewById(R.id.rulesContainer);
        rulesCountLabel  = findViewById(R.id.rulesCountLabel);
        btnTopMenu       = findViewById(R.id.btnTopMenu);
        cardSchedules    = findViewById(R.id.cardSchedules);
        schedulesSummary = findViewById(R.id.schedulesSummary);
        schedulesBadge   = findViewById(R.id.schedulesActiveBadge);
    }

    private void setupCountrySpinner() {
        ArrayAdapter<CountryData> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, CountryData.LIST);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        countryDial.setAdapter(adapter);
        countryDial.setSelection(CountryData.findIndexByIso("IN"));
    }

    private void maybeDetectCountryFromGeoIP() {
        GeoIPHelper.detectAsync(new GeoIPHelper.Callback() {
            public void onCountry(String iso) {
                if (iso != null && !iso.isEmpty()) {
                    countryDial.setSelection(CountryData.findIndexByIso(iso));
                }
            }
        });
    }

    private void setupListeners() {
        btnTypePrefix.setOnClickListener(v -> selectType(Rule.TYPE_PREFIX));
        btnTypeBetween.setOnClickListener(v -> selectType(Rule.TYPE_BETWEEN));
        btnTypeSuffix.setOnClickListener(v -> selectType(Rule.TYPE_SUFFIX));
        btnAccept.setOnClickListener(v -> selectAction(Rule.ACTION_ACCEPT));
        btnReject.setOnClickListener(v -> selectAction(Rule.ACTION_REJECT));
        btnAddRule.setOnClickListener(v -> addRule());

        contactsOnlySwitch.setOnCheckedChangeListener((b, checked) -> {
            rulesManager.setContactsOnlyMode(checked);
        });

        blockedCallsCard.setOnClickListener(v ->
            startActivity(new Intent(MainActivity.this, BlockedCallsActivity.class)));

        cardSchedules.setOnClickListener(v ->
            startActivity(new Intent(MainActivity.this, SchedulesActivity.class)));

        btnTopMenu.setOnClickListener(v -> showAccountMenu(v));

        selectType(Rule.TYPE_PREFIX);
        selectAction(Rule.ACTION_REJECT);
    }

    private void selectType(String type) {
        currentType = type;
        btnTypePrefix.setBackgroundResource(
            type.equals(Rule.TYPE_PREFIX)  ? R.drawable.btn_type_active : R.drawable.btn_type_inactive);
        btnTypeBetween.setBackgroundResource(
            type.equals(Rule.TYPE_BETWEEN) ? R.drawable.btn_type_active : R.drawable.btn_type_inactive);
        btnTypeSuffix.setBackgroundResource(
            type.equals(Rule.TYPE_SUFFIX)  ? R.drawable.btn_type_active : R.drawable.btn_type_inactive);
        patternInput.setHint(
            type.equals(Rule.TYPE_BETWEEN) ? "e.g. 9000000000-9999999999" : "e.g. 9494");
    }

    private void selectAction(String action) {
        currentAction = action;
        btnAccept.setBackgroundResource(
            action.equals(Rule.ACTION_ACCEPT) ? R.drawable.btn_accept_active : R.drawable.btn_accept_inactive);
        btnReject.setBackgroundResource(
            action.equals(Rule.ACTION_REJECT) ? R.drawable.btn_reject_active : R.drawable.btn_reject_inactive);
    }

    private void addRule() {
        String pat = patternInput.getText().toString().trim();
        if (pat.isEmpty()) {
            Toast.makeText(this, "Please enter a pattern", Toast.LENGTH_SHORT).show();
            return;
        }
        CountryData cd = (CountryData) countryDial.getSelectedItem();
        String full;
        if (Rule.TYPE_BETWEEN.equals(currentType) && pat.contains("-")) {
            // range — assume user typed both endpoints
            int dash = pat.indexOf('-');
            full = cd.dialCode + pat.substring(0, dash) + "-" + cd.dialCode + pat.substring(dash + 1);
        } else {
            full = cd.dialCode + pat;
        }
        rulesManager.addRule(full, currentType, currentAction);
        patternInput.setText("");
        refreshUI();
        SyncManager.getInstance(this).syncRulesAsync();
    }

    private void refreshUI() {
        AuthManager auth = AuthManager.getInstance(this);
        SubscriptionManager sub = SubscriptionManager.getInstance(this);
        topBarUserInfo.setText("Signed in: " + auth.getFullNumber() + "  ·  " + sub.getStatusLabel());

        List<Rule> rules = rulesManager.getRules();
        int acc = 0, rej = 0;
        for (Rule r : rules) {
            if (Rule.ACTION_ACCEPT.equals(r.getAction())) acc++;
            else rej++;
        }
        statAccept.setText(String.valueOf(acc));
        statReject.setText(String.valueOf(rej));
        statTotal.setText(String.valueOf(rules.size()));

        contactsOnlySwitch.setOnCheckedChangeListener(null);
        contactsOnlySwitch.setChecked(rulesManager.isContactsOnlyMode());
        contactsOnlySwitch.setOnCheckedChangeListener((b, checked) -> {
            rulesManager.setContactsOnlyMode(checked);
        });

        int blocked = BlockedCallsManager.getInstance(this).getTotalCount();
        blockedCallsCount.setText(String.valueOf(blocked));

        // Schedules summary
        List<Schedule> all = ScheduleManager.getInstance(this).getAll();
        Schedule active = ScheduleManager.getInstance(this).getActiveSchedule(System.currentTimeMillis());
        if (active != null) {
            schedulesSummary.setText("\"" + active.name + "\" active now");
            schedulesBadge.setVisibility(View.VISIBLE);
        } else if (all.isEmpty()) {
            schedulesSummary.setText("Time-based blocking — tap to add");
            schedulesBadge.setVisibility(View.GONE);
        } else {
            schedulesSummary.setText(all.size() + " schedule" + (all.size() == 1 ? "" : "s")
                                     + " — none active now");
            schedulesBadge.setVisibility(View.GONE);
        }

        rulesCountLabel.setText("ACTIVE RULES (" + rules.size() + ")");
        rulesContainer.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        for (final Rule r : rules) {
            View item = inf.inflate(R.layout.rule_item, rulesContainer, false);
            TextView typeBadge   = item.findViewById(R.id.ruleTypeBadge);
            TextView patternView = item.findViewById(R.id.rulePattern);
            TextView actionBadge = item.findViewById(R.id.ruleActionBadge);
            Button   delBtn      = item.findViewById(R.id.btnDelete);

            typeBadge.setText(r.getType().toUpperCase());
            patternView.setText(r.getPattern());
            actionBadge.setText(Rule.ACTION_ACCEPT.equals(r.getAction()) ? "✓ Accept" : "✗ Reject");
            actionBadge.setTextColor(getResources().getColor(
                Rule.ACTION_ACCEPT.equals(r.getAction()) ? R.color.accept : R.color.reject, null));
            delBtn.setOnClickListener(v -> {
                rulesManager.removeRule(r.getId());
                refreshUI();
                SyncManager.getInstance(MainActivity.this).syncRulesAsync();
            });
            rulesContainer.addView(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SubscriptionManager subMgr = SubscriptionManager.getInstance(this);
        if (subMgr.hasBeenChecked() && !subMgr.isActive()) {
            startActivity(new Intent(this, PaywallActivity.class));
            finish();
            return;
        }
        subMgr.refreshAsync();
        checkBlockingStatus();
        refreshUI();
    }

    private void checkBlockingStatus() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                Intent intent = rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
                try {
                    startActivityForResult(intent, REQUEST_ROLE);
                } catch (Exception ignored) {}
            }
        }
    }

    private void showAccountMenu(View anchor) {
        SyncManager sm = SyncManager.getInstance(this);
        final boolean optedIn = sm.isContactsOptedIn();
        final String contactsItem = optedIn
            ? "📇 Cloud contact sync: ON"
            : "📇 Cloud contact sync: OFF";
        final String[] items = { "🔐 Change PIN", contactsItem, "🚪 Logout" };
        new AlertDialog.Builder(this)
            .setTitle("Account")
            .setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    if (which == 0) {
                        startActivity(new Intent(MainActivity.this, ChangePinActivity.class));
                    } else if (which == 1) {
                        if (optedIn) confirmContactsOptOut();
                        else         confirmContactsOptIn();
                    } else if (which == 2) {
                        confirmLogout();
                    }
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmContactsOptIn() {
        new AlertDialog.Builder(this)
            .setTitle("Sync contacts to cloud?")
            .setMessage(
                "This will upload the names and phone numbers in your address book to our " +
                "secure servers, so your block-rules and contacts list stay in sync if you " +
                "switch devices.\n\n" +
                "• Off by default — your contacts never leave the device unless you turn this on.\n" +
                "• You can turn this off any time, and we'll delete your uploaded contacts.\n\n" +
                "By continuing you agree to our Privacy Policy at https://onephone.pro/privacy.")
            .setPositiveButton("Turn on", (d, w) -> {
                if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                    != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 105);
                    return;
                }
                SyncManager.getInstance(MainActivity.this).setContactsOptedIn(true);
                SyncManager.getInstance(MainActivity.this).syncContactsAsync();
                Toast.makeText(MainActivity.this, "✓ Contact sync enabled", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmContactsOptOut() {
        new AlertDialog.Builder(this)
            .setTitle("Turn off cloud contact sync?")
            .setMessage("Your previously uploaded contacts will be deleted from our servers.")
            .setPositiveButton("Turn off", (d, w) -> {
                SyncManager.getInstance(MainActivity.this).setContactsOptedIn(false);
                Toast.makeText(MainActivity.this, "Contact sync turned off", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("You'll need to enter your PIN to sign back in. Continue?")
            .setPositiveButton("Logout", (d, w) -> {
                AuthManager.getInstance(this).logout();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 105) {
            boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                SyncManager.getInstance(this).setContactsOptedIn(true);
                SyncManager.getInstance(this).syncContactsAsync();
                Toast.makeText(this, "✓ Contact sync enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                    "Contacts permission denied — sync stays off.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
