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
    private TextView btnTypePrefix, btnTypeSuffix, btnTypeRange;
    private TextView btnAccept, btnReject;
    private View rangeInputsRow;
    private EditText rangeBeforeInput, rangeAfterInput;
    private TextView rangeSummary;
    // Cards (replacing inline rules list)
    private View cardActiveRules, cardRecentCalls;
    private TextView rulesSummary, rulesCount;
    private View blockedCallsCard, cardSchedules;

    private String currentType = Rule.TYPE_PREFIX;

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
        btnTypeSuffix    = findViewById(R.id.btnTypeSuffix);
        btnTypeRange     = findViewById(R.id.btnTypeRange);
        rangeInputsRow   = findViewById(R.id.rangeInputsRow);
        rangeBeforeInput = findViewById(R.id.rangeBeforeInput);
        rangeAfterInput  = findViewById(R.id.rangeAfterInput);
        rangeSummary     = findViewById(R.id.rangeSummary);
        cardActiveRules  = findViewById(R.id.cardActiveRules);
        cardRecentCalls  = findViewById(R.id.cardRecentCalls);
        rulesSummary     = findViewById(R.id.rulesSummary);
        rulesCount       = findViewById(R.id.rulesCount);
        btnAccept        = findViewById(R.id.btnAccept);
        btnReject        = findViewById(R.id.btnReject);
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
            R.layout.spinner_item, CountryData.LIST);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
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
        btnTypeSuffix.setOnClickListener(v -> selectType(Rule.TYPE_SUFFIX));
        btnTypeRange.setOnClickListener(v -> selectType(Rule.TYPE_RANGE));

        cardActiveRules.setOnClickListener(v ->
            startActivity(new Intent(MainActivity.this, RulesActivity.class)));
        cardRecentCalls.setOnClickListener(v ->
            startActivity(new Intent(MainActivity.this, RecentCallsActivity.class)));

        // Live range summary as user types
        android.text.TextWatcher tw = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c2) {}
            public void onTextChanged(CharSequence s, int a, int b, int c2) {}
            public void afterTextChanged(android.text.Editable e) { updateRangeSummary(); }
        };
        rangeBeforeInput.addTextChangedListener(tw);
        rangeAfterInput.addTextChangedListener(tw);
        patternInput.addTextChangedListener(tw);
        // Action buttons COMMIT the rule directly — no separate Add button.
        btnAccept.setOnClickListener(v -> addRule(Rule.ACTION_ACCEPT));
        btnReject.setOnClickListener(v -> addRule(Rule.ACTION_REJECT));

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

        // SUFFIX: hide country spinner (suffix matches by tail digits regardless of country)
        countryDial.setVisibility(isSuffix ? View.GONE : View.VISIBLE);

        // RANGE: show before/after inputs + summary
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

    /** Updates the "Will block N numbers from X to Y" preview under the RANGE inputs. */
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
                + s + " → " + e);
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
            // SUFFIX: store just the digits the user typed (no country code).
            // Rule.matches() will match any number whose tail digits equal this.
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
            // PREFIX (default)
            CountryData cd = (CountryData) countryDial.getSelectedItem();
            storedPattern = cd.dialCode + pat;
        }

        boolean added = rulesManager.addRule(storedPattern, storedType, action);
        if (!added) {
            Toast.makeText(this,
                "⚠ A " + storedType.toUpperCase() + " rule for " + storedPattern + " already exists",
                Toast.LENGTH_LONG).show();
            return;
        }
        patternInput.setText("");
        rangeSummary.setText("");
        Toast.makeText(this,
            (Rule.ACTION_ACCEPT.equals(action) ? "✓ ACCEPT rule added: " : "✗ REJECT rule added: ")
                + storedPattern,
            Toast.LENGTH_SHORT).show();
        refreshUI();
        SyncManager.getInstance(this).syncRulesAsync();
    }

    private void refreshUI() {
        AuthManager auth = AuthManager.getInstance(this);
        android.view.View dBanner = findViewById(R.id.disabledBanner);
        if (dBanner != null) {
            dBanner.setVisibility(auth.isAccountDisabled() ? android.view.View.VISIBLE
                                                            : android.view.View.GONE);
        }
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

        // Active rules card — just show the count + summary; full list lives in RulesActivity
        rulesCount.setText(String.valueOf(rules.size()));
        if (rules.isEmpty()) {
            rulesSummary.setText("None yet — add one above");
        } else {
            int rejects = 0, accepts = 0;
            for (Rule r : rules) {
                if (Rule.ACTION_REJECT.equals(r.getAction())) rejects++;
                else if (Rule.ACTION_ACCEPT.equals(r.getAction())) accepts++;
            }
            StringBuilder sb = new StringBuilder();
            if (rejects > 0) sb.append(rejects).append(" block");
            if (accepts > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(accepts).append(" allow");
            }
            rulesSummary.setText(sb.toString() + " · tap to view");
        }
    }

    /**
     * Prompts the user once to grant "Display over other apps" so the post-call
     * Block popup can appear over the dialer. If they decline, the app falls
     * back to a notification — but we don't keep asking on every launch.
     */
    private void maybePromptOverlayPermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return;
        if (android.provider.Settings.canDrawOverlays(this)) return;
        android.content.SharedPreferences sp = getSharedPreferences("ui_prefs", MODE_PRIVATE);
        if (sp.getBoolean("overlay_prompt_shown", false)) return;
        sp.edit().putBoolean("overlay_prompt_shown", true).apply();
        new AlertDialog.Builder(this)
            .setTitle("Allow post-call popup?")
            .setMessage("To show a 'Block this number?' popup right after a call ends, "
                + "Call Filter needs permission to draw over other apps.\n\n"
                + "If you skip, you'll get a notification instead.")
            .setPositiveButton("Open Settings", (d, w) -> {
                try {
                    Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception ignored) {}
            })
            .setNegativeButton("Skip", null)
            .show();
    }

    /** Shown when the admin has disabled this user's account. */
    private boolean disabledDialogShown = false;
    private void showAccountDisabledDialog() {
        if (disabledDialogShown) return;
        disabledDialogShown = true;
        new AlertDialog.Builder(this)
            .setTitle("Account disabled")
            .setMessage("Your account has been disabled by the administrator.\n\n"
                + "Calls will still be screened with your existing rules, but you "
                + "won't receive new updates or be able to manage your subscription.\n\n"
                + "Contact support@onephone.pro to reactivate.")
            .setCancelable(false)
            .setPositiveButton("OK", null)
            .show();
    }

/** Android 13+ requires runtime POST_NOTIFICATIONS for the notification
     *  fallback of the post-call popup to actually appear. */
    private void maybePromptNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return;  // TIRAMISU
        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.POST_NOTIFICATIONS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        try {
            androidx.core.app.ActivityCompat.requestPermissions(this,
                new String[]{ android.Manifest.permission.POST_NOTIFICATIONS },
                7401);
        } catch (Exception ignored) {}
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
                && resultCode == RESULT_OK && data != null) {
            try {
                java.util.ArrayList<String> nums = data.getStringArrayListExtra("selected_numbers");
                java.util.ArrayList<String> names = data.getStringArrayListExtra("selected_names");
                if (nums == null) nums = new java.util.ArrayList<>();
                if (names == null) names = new java.util.ArrayList<>();
                if (blockAllDialog != null) {
                    blockAllDialog.resumeWithPickedContacts(nums, names);
                } else {
                    // Dialog state was lost (process death while picker open).
                    // Tell the user instead of crashing.
                    android.widget.Toast.makeText(this,
                        "Selection lost — please try Block All again.",
                        android.widget.Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "picker result failed", e);
                android.widget.Toast.makeText(this,
                    "Could not save contacts: " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        banner_handler.removeCallbacks(banner_tick);
        // Record background-start for auto-lock
        getSharedPreferences("ui_prefs", MODE_PRIVATE).edit()
            .putLong("bg_at_ms", System.currentTimeMillis()).apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Auto-lock check — if enabled and we were in background > 5 min, lock now
        android.content.SharedPreferences uiPrefs = getSharedPreferences("ui_prefs", MODE_PRIVATE);
        if (uiPrefs.getBoolean("auto_lock", false)) {
            long bg = uiPrefs.getLong("bg_at_ms", 0L);
            if (bg > 0 && System.currentTimeMillis() - bg > 5L * 60_000L) {
                AuthManager.getInstance(this).lock();
                Intent i = new Intent(this, LoginActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
                return;
            }
        }
        BlockAllManager.getInstance(this).pullFromCloud();
        // Verify account still exists + check if admin disabled it
        AuthManager.getInstance(this).verifyAccountStillExists(new AuthManager.AccountCheckCallback() {
            public void onResult(boolean stillExists) {
                if (!stillExists) {
                    runOnUiThread(() -> {
                        Intent i = new Intent(MainActivity.this, SignupActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        finish();
                    });
                }
            }
            @Override
            public void onAccountDisabled() {
                runOnUiThread(() -> showAccountDisabledDialog());
            }
        });
        // Merge cloud-only rules into local (handles admin-added rules)
        SyncManager.getInstance(this).mergeRulesFromCloud();
        // Refresh the UI after merge completes (HTTP async)
        banner_handler.postDelayed(() -> refreshUI(), 1_500L);
        refreshBlockAllUI();
        banner_handler.postDelayed(banner_tick, 30_000L);
        // NOTE: do NOT pull rules on resume — that races with just-added local
        // rules and wipes them. Initial cloud pull happens only on login
        // (LoginActivity.handleLogin) and is gated by the initial-sync flag.
        SubscriptionManager subMgr = SubscriptionManager.getInstance(this);
        if (subMgr.hasBeenChecked() && !subMgr.isActive()) {
            startActivity(new Intent(this, PaywallActivity.class));
            finish();
            return;
        }
        subMgr.refreshAsync();
        // checkBlockingStatus() removed from onResume — was triggering the
        // "Default call screening app" prompt over other system dialogs.
        // The user manages this from Profile → Permissions now.
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
