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
    // Block-All Now UI
    private TextView btnBlockAll, btnBlockAllCountdown, blockAllStatus;
    private android.view.View blockAllBanner;
    private android.widget.Button btnStopBlockAll;
    private BlockAllNowDialog blockAllDialog;
    private final android.os.Handler banner_handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable banner_tick = new Runnable() {
        public void run() {
            refreshBlockAllUI();
            banner_handler.postDelayed(this, 30_000L);  // every 30s update countdown
        }
    };
    private TextView blockedCallsCount, schedulesSummary, schedulesBadge;
    private ImageButton btnTopMenu;
    private Switch contactsOnlySwitch;

    // Add rule form
    private Spinner countryDial;
    private EditText patternInput;
    private TextView btnTypePrefix, btnTypeBetween, btnTypeSuffix;
    private TextView btnAccept, btnReject, btnAddRule;
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
        btnBlockAll          = findViewById(R.id.btnBlockAll);
        btnBlockAllCountdown = findViewById(R.id.btnBlockAllCountdown);
        blockAllBanner       = findViewById(R.id.blockAllBanner);
        blockAllStatus       = findViewById(R.id.blockAllStatus);
        btnStopBlockAll      = findViewById(R.id.btnStopBlockAll);
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

        btnTopMenu.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ProfileActivity.class)));

        // Block All Now
        btnBlockAll.setOnClickListener(v -> {
            if (BlockAllManager.getInstance(MainActivity.this).isActive()) {
                // Already active — tap to stop
                stopBlockAllConfirm();
            } else {
                blockAllDialog = new BlockAllNowDialog(MainActivity.this,
                    () -> { refreshBlockAllUI(); refreshUI(); });
                blockAllDialog.show();
            }
        });
        btnStopBlockAll.setOnClickListener(v -> stopBlockAllConfirm());

        selectType(Rule.TYPE_PREFIX);
        selectAction(Rule.ACTION_REJECT);
    }

    private void selectType(String type) {
        currentType = type;
        int whiteColor = getResources().getColor(R.color.white, null);
        int dimColor   = getResources().getColor(R.color.subtext, null);

        boolean isPrefix  = type.equals(Rule.TYPE_PREFIX);
        boolean isBetween = type.equals(Rule.TYPE_BETWEEN);
        boolean isSuffix  = type.equals(Rule.TYPE_SUFFIX);

        btnTypePrefix.setBackgroundResource(isPrefix
            ? R.drawable.btn_type_active : R.drawable.btn_type_inactive);
        btnTypePrefix.setTextColor(isPrefix ? whiteColor : dimColor);

        btnTypeBetween.setBackgroundResource(isBetween
            ? R.drawable.btn_type_active : R.drawable.btn_type_inactive);
        btnTypeBetween.setTextColor(isBetween ? whiteColor : dimColor);

        btnTypeSuffix.setBackgroundResource(isSuffix
            ? R.drawable.btn_type_active : R.drawable.btn_type_inactive);
        btnTypeSuffix.setTextColor(isSuffix ? whiteColor : dimColor);

        patternInput.setHint(
            isBetween ? "e.g. 9000000000-9999999999" : "e.g. 9494");
    }

    private void selectAction(String action) {
        currentAction = action;
        int whiteColor = getResources().getColor(R.color.white, null);
        int dimColor   = getResources().getColor(R.color.subtext, null);

        boolean isAccept = action.equals(Rule.ACTION_ACCEPT);
        boolean isReject = action.equals(Rule.ACTION_REJECT);

        btnAccept.setBackgroundResource(isAccept
            ? R.drawable.btn_accept_active : R.drawable.btn_accept_inactive);
        btnAccept.setTextColor(isAccept ? whiteColor : dimColor);

        btnReject.setBackgroundResource(isReject
            ? R.drawable.btn_reject_active : R.drawable.btn_reject_inactive);
        btnReject.setTextColor(isReject ? whiteColor : dimColor);
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
        // Reload rules in case cloud pull happened in the background
        rulesManager.reload();
        topBarUserInfo.setText("Signed in: " + auth.getFullNumber() + "  \u00B7  " + sub.getStatusLabel());

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
            TextView delBtn      = item.findViewById(R.id.btnDelete);

            typeBadge.setText(r.getType().toUpperCase());
            patternView.setText(r.getPattern());

            boolean isAccept = Rule.ACTION_ACCEPT.equals(r.getAction());
            actionBadge.setText(isAccept ? "✓ ACCEPT" : "✗ REJECT");
            actionBadge.setBackgroundResource(isAccept ? R.drawable.badge_accept : R.drawable.badge_reject);

            delBtn.setOnClickListener(v -> {
                rulesManager.removeRule(r.getId());
                refreshUI();
                SyncManager.getInstance(MainActivity.this).syncRulesAsync();
            });
            rulesContainer.addView(item);
        }
    }

    private void stopBlockAllConfirm() {
        new AlertDialog.Builder(this)
            .setTitle("Stop Block All Now?")
            .setMessage("Calls will follow your normal rules again.")
            .setPositiveButton("Stop", (d, w) -> {
                BlockAllManager.getInstance(this).deactivate();
                refreshBlockAllUI();
                refreshUI();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void refreshBlockAllUI() {
        BlockAllManager ba = BlockAllManager.getInstance(this);
        if (ba.isActive()) {
            // Icon: red countdown chip
            long remain = ba.getExpiresAtMs() - System.currentTimeMillis();
            String label;
            if (ba.getExpiresAtMs() == 0L) {
                label = "ON";
            } else if (remain <= 0) {
                ba.deactivate();
                btnBlockAllCountdown.setVisibility(android.view.View.GONE);
                blockAllBanner.setVisibility(android.view.View.GONE);
                return;
            } else {
                long h = remain / 3_600_000L;
                long m = (remain % 3_600_000L) / 60_000L;
                if (h > 0) label = h + "h " + m + "m";
                else       label = Math.max(1, m) + "m";
            }
            btnBlockAllCountdown.setText(label);
            btnBlockAllCountdown.setVisibility(android.view.View.VISIBLE);

            // Banner
            String modeLabel;
            String m = ba.getMode();
            if (BlockAllManager.MODE_EVERYTHING.equals(m)) modeLabel = "Everything blocked";
            else if (BlockAllManager.MODE_EXCEPT_CONTACTS.equals(m)) modeLabel = "Except contacts";
            else if (BlockAllManager.MODE_EXCEPT_CUSTOM.equals(m)) modeLabel = ba.getAllowNumbers().size() + " allowed";
            else modeLabel = "";
            blockAllStatus.setText(modeLabel + " \u00B7 " + ba.formatStatus());
            blockAllBanner.setVisibility(android.view.View.VISIBLE);
        } else {
            btnBlockAllCountdown.setVisibility(android.view.View.GONE);
            blockAllBanner.setVisibility(android.view.View.GONE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BlockAllNowDialog.REQ_PICK_CONTACTS_FOR_BLOCK_ALL
                && resultCode == RESULT_OK && data != null && blockAllDialog != null) {
            java.util.ArrayList<String> nums = data.getStringArrayListExtra("selected_numbers");
            java.util.ArrayList<String> names = data.getStringArrayListExtra("selected_names");
            if (nums == null) nums = new java.util.ArrayList<>();
            if (names == null) names = new java.util.ArrayList<>();
            blockAllDialog.resumeWithPickedContacts(nums, names);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        banner_handler.removeCallbacks(banner_tick);
    }

    @Override
    protected void onResume() {
        super.onResume();
        BlockAllManager.getInstance(this).pullFromCloud();
        refreshBlockAllUI();
        banner_handler.postDelayed(banner_tick, 30_000L);
        // Pull rules from cloud after login/resume (HTTP async; refresh UI when done)
        SyncManager.getInstance(this).forcePullRulesFromCloud();
        banner_handler.postDelayed(() -> refreshUI(), 2_000L);
        banner_handler.postDelayed(() -> refreshUI(), 5_000L);
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
