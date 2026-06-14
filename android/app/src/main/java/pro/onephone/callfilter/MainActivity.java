package pro.onephone.callfilter;

import android.Manifest;
import androidx.appcompat.app.AlertDialog;
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
import androidx.appcompat.widget.SwitchCompat;
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
    private SwitchCompat contactsOnlySwitch;
    private ScoreRingView scoreRing;
    private TextView scoreNumber, scoreTag, scoreStatusLine;

    private View blockedCallsCard, cardSchedules, cardGlobalBlocklist;
    private TextView globalBlocklistSummary, globalBlocklistBadge;

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

        // Warm up contacts cache immediately so lookups are fast
        ContactsCacheManager.getInstance(this).warmUp();
        rulesManager = RulesManager.getInstance(this);
        bindViews();
        setupListeners();
        refreshUI();
        checkBlockingStatus();

        SyncManager.getInstance(this).syncRulesAsync();
        SyncManager.getInstance(this).syncContactsAsync();
    }

    private void bindViews() {
        topBarUserInfo   = findViewById(R.id.topBarUserInfo);
        View btnProfileTop = findViewById(R.id.btnProfileTop);
        if (btnProfileTop != null) {
            btnProfileTop.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ProfileActivity.class)));
        }
        statAccept       = findViewById(R.id.statAccept);
        statReject       = findViewById(R.id.statReject);
        statTotal        = findViewById(R.id.statTotal);
        blockedCallsCount = findViewById(R.id.blockedCallsCount);
        blockedCallsCard = findViewById(R.id.blockedCallsCard);
        contactsOnlySwitch = findViewById(R.id.contactsOnlySwitch);
        btnBlockAll          = findViewById(R.id.btnBlockAll);
        btnBlockAllCountdown = findViewById(R.id.btnBlockAllCountdown);
        blockAllBanner       = findViewById(R.id.blockAllBanner);
        blockAllStatus       = findViewById(R.id.blockAllStatus);
        btnStopBlockAll      = findViewById(R.id.btnStopBlockAll);
        cardSchedules        = findViewById(R.id.cardSchedules);
        cardGlobalBlocklist  = findViewById(R.id.cardGlobalBlocklist);
        globalBlocklistSummary = findViewById(R.id.globalBlocklistSummary);
        globalBlocklistBadge   = findViewById(R.id.globalBlocklistBadge);
        schedulesSummary = findViewById(R.id.schedulesSummary);
        schedulesBadge   = findViewById(R.id.schedulesActiveBadge);

        scoreRing       = findViewById(R.id.scoreRing);
        scoreNumber     = findViewById(R.id.scoreNumber);
        scoreTag        = findViewById(R.id.scoreTag);
        scoreStatusLine = findViewById(R.id.scoreStatusLine);

        // Bottom navigation
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav =
            findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    return true;
                } else if (id == R.id.nav_activity) {
                    startActivity(new Intent(this, RecentCallsActivity.class));
                    return true;
                } else if (id == R.id.nav_rules) {
                    startActivity(new Intent(this, RulesActivity.class));
                    return true;
                } else if (id == R.id.nav_sms) {
                    startActivity(new Intent(this, SmsProtectionActivity.class));
                    return true;
                }
                return false;
            });
        }
    }

    private void setupListeners() {
        contactsOnlySwitch.setOnCheckedChangeListener((b, checked) -> {
            rulesManager.setContactsOnlyMode(checked);
        });

        blockedCallsCard.setOnClickListener(v ->
            startActivity(new Intent(MainActivity.this, BlockedCallsActivity.class)));

        cardSchedules.setOnClickListener(v ->
            startActivity(new Intent(MainActivity.this, SchedulesActivity.class)));

        if (cardGlobalBlocklist != null) {
            cardGlobalBlocklist.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, GlobalBlocklistActivity.class)));
        }

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
    }






    /** Compute a 0-100 protection score from current settings and animate the ring. */
    private void updateProtectionScore(int ruleCount) {
        if (scoreRing == null) return;
        int score = 0;
        boolean loggedIn = AuthManager.getInstance(this).isLoggedIn();
        if (loggedIn) score += 35;
        if (ruleCount > 0 || rulesManager.isContactsOnlyMode()) score += 20;

        // Global blocklist active when at least one reason is enabled
        try {
            GlobalBlocklistManager g = GlobalBlocklistManager.getInstance(this);
            if (!g.getEnabledReasons().isEmpty()) score += 25;
        } catch (Exception ignored) {}

        // Active subscription
        try {
            if (SubscriptionManager.getInstance(this).isActive()) score += 20;
        } catch (Exception ignored) {}

        if (score > 100) score = 100;
        final int finalScore = score;

        scoreRing.setScore(finalScore);

        // Animate the number to match the ring
        android.animation.ValueAnimator na = android.animation.ValueAnimator.ofInt(0, finalScore);
        na.setDuration(1400);
        na.addUpdateListener(a -> scoreNumber.setText(String.valueOf(a.getAnimatedValue())));
        na.start();

        if (finalScore >= 80) {
            scoreTag.setText("PROTECTED");
            scoreTag.setTextColor(getResources().getColor(R.color.accept, null));
            scoreStatusLine.setText("Your device is well protected");
        } else if (finalScore >= 50) {
            scoreTag.setText("PARTIAL");
            scoreTag.setTextColor(0xFFF59E0B);
            scoreStatusLine.setText("Enable more protections to raise your score");
        } else {
            scoreTag.setText("AT RISK");
            scoreTag.setTextColor(getResources().getColor(R.color.reject, null));
            scoreStatusLine.setText("Turn on blocking to protect your device");
        }
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
        String who = !auth.getName().isEmpty() ? auth.getName() : auth.getFullNumber();
        topBarUserInfo.setText("Signed in: " + who);

        List<Rule> rules = rulesManager.getRules();
        int acc = 0, rej = 0;
        for (Rule r : rules) {
            if (Rule.ACTION_ACCEPT.equals(r.getAction())) acc++;
            else rej++;
        }
        statAccept.setText(String.valueOf(acc));
        statReject.setText(String.valueOf(rej));
        statTotal.setText(String.valueOf(rules.size()));

        updateProtectionScore(rules.size());

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

        // Global Blocklist tile
        if (globalBlocklistSummary != null && globalBlocklistBadge != null) {
            GlobalBlocklistManager glm = GlobalBlocklistManager.getInstance(this);
            int totalG  = glm.getTotalEntries();
            int activeG = glm.getEnabledEntryCount();
            int reasonG = glm.getEnabledReasons().size();
            // Badge = ON if ANY reason is enabled (even before numbers are synced)
            if (reasonG > 0) {
                if (totalG == 0) {
                    globalBlocklistSummary.setText(reasonG + " reason" + (reasonG==1?"":"s") + " enabled — sync to download numbers");
                } else {
                    globalBlocklistSummary.setText(activeG + " blocking · " + reasonG + " reason" + (reasonG==1?"":"s") + " enabled");
                }
                globalBlocklistBadge.setText("ON");
                globalBlocklistBadge.setTextColor(0xFFFFFFFF);
            } else if (totalG == 0) {
                globalBlocklistSummary.setText("Not synced — tap to set up");
                globalBlocklistBadge.setText("OFF");
                globalBlocklistBadge.setTextColor(getResources().getColor(R.color.subtext, null));
            } else {
                globalBlocklistSummary.setText(totalG + " numbers available — none enabled");
                globalBlocklistBadge.setText("OFF");
                globalBlocklistBadge.setTextColor(getResources().getColor(R.color.subtext, null));
            }
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
        // Record background-start for auto-lock
        getSharedPreferences("ui_prefs", MODE_PRIVATE).edit()
            .putLong("bg_at_ms", System.currentTimeMillis()).apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Keep Home highlighted when returning from another tab
        com.google.android.material.bottomnavigation.BottomNavigationView bn = findViewById(R.id.bottomNav);
        if (bn != null && bn.getSelectedItemId() != R.id.nav_home) {
            bn.getMenu().findItem(R.id.nav_home).setChecked(true);
        }
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
        // Refresh contacts cache in background (fast lookup for incoming calls)
        ContactsCacheManager.getInstance(this).refreshAsync();
        // Pull schedules from cloud on every resume (picks up admin-added schedules)
        ScheduleManager.getInstance(this).pullFromCloud();
        // Sync global blocklist in background
        SyncManager.getInstance(this).syncGlobalBlocklistAsync();
        // Subscribe to FCM topic so the server can wake us to refresh the
        // blocklist. No-op if Firebase isn't configured (no google-services.json).
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .subscribeToTopic("global_blocklist");
        } catch (Throwable ignored) {}
        // Pull per-user enabled reasons (restores settings after logout/reinstall)
        GlobalBlocklistManager.getInstance(this).pullEnabledReasonsAsync();
        // Pull blocked-call history from cloud (restores after reinstall/new version)
        SyncManager.getInstance(this).pullBlockedCallsFromCloud();
        // Pull SMS auto-reply templates from cloud
        SmsAutoResponder.getInstance(this).pullFromCloudAsync();
        // Refresh SMS phishing/spam detection rules from cloud
        SmsThreatDetector.getInstance(this).syncRulesAsync();
        // Restore phone book from cloud on a fresh device (writes to Contacts)
        SyncManager.getInstance(this).restoreContactsFromCloudAsync();
        // Refresh the admin contacts-sync policy; if disabled, stop local syncing.
        SyncManager.getInstance(this).fetchSyncConfigAsync(allowed -> {
            if (!allowed) {
                SyncManager.getInstance(MainActivity.this).setContactsOptedIn(false);
            }
        });
        // Refresh the UI after merge completes (HTTP async)
        banner_handler.postDelayed(() -> refreshUI(), 1_500L);
        refreshBlockAllUI();
        banner_handler.postDelayed(banner_tick, 30_000L);
        maybePromptOverlayPermission();
        maybePromptNotificationPermission();
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
        checkBlockingStatus();
        refreshUI();
    }

    private void checkBlockingStatus() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                android.content.SharedPreferences p = getSharedPreferences("ui_prefs", MODE_PRIVATE);
                boolean explained = p.getBoolean("role_explained", false);
                if (!explained) {
                    // First time: explain WHY before sending the system role prompt.
                    p.edit().putBoolean("role_explained", true).apply();
                    new AlertDialog.Builder(this)
                        .setTitle("Allow CyberGuard to block calls")
                        .setMessage("To detect and block unwanted calls, please set CyberGuard "
                            + "as your Caller ID & spam app on the next screen. All call "
                            + "screening happens on your device.")
                        .setPositiveButton("Continue", (d, w) -> requestScreeningRole(rm))
                        .setNegativeButton("Not now", null)
                        .show();
                } else {
                    requestScreeningRole(rm);
                }
            }
        }
        ensureContactsPermission();
    }

    private void requestScreeningRole(RoleManager rm) {
        try {
            Intent intent = rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
            startActivityForResult(intent, REQUEST_ROLE);
        } catch (Exception ignored) {}
    }

    // Contacts is an essential permission (recognise known callers, Contacts-Only
    // Mode). Ask once, with explanation, on first launch.
    private void ensureContactsPermission() {
        if (Build.VERSION.SDK_INT < 23) return;
        android.content.SharedPreferences p = getSharedPreferences("ui_prefs", MODE_PRIVATE);
        if (p.getBoolean("contacts_asked", false)) return;
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) return;
        p.edit().putBoolean("contacts_asked", true).apply();
        new AlertDialog.Builder(this)
            .setTitle("Recognise your contacts")
            .setMessage("CyberGuard uses your contacts to recognise known callers and to power "
                + "Contacts-Only Mode (allow only people you know). Your contacts stay on your "
                + "device unless you turn on cloud sync.")
            .setPositiveButton("Allow", (d, w) ->
                requestPermissions(new String[]{ Manifest.permission.READ_CONTACTS }, 106))
            .setNegativeButton("Skip", null)
            .show();
    }

    private void showAccountMenu(View anchor) {
        SyncManager sm = SyncManager.getInstance(this);
        final boolean optedIn = sm.isContactsOptedIn();
        final boolean syncAllowed = sm.isSyncAllowedByAdmin();
        final String contactsItem = !syncAllowed
            ? "📇 Cloud contact sync: Disabled by admin"
            : (optedIn ? "📇 Cloud contact sync: ON" : "📇 Cloud contact sync: OFF");
        final String[] items = { "🔐 Change PIN", contactsItem, "🚪 Logout" };
        new AlertDialog.Builder(this)
            .setTitle("Account")
            .setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    if (which == 0) {
                        startActivity(new Intent(MainActivity.this, ChangePinActivity.class));
                    } else if (which == 1) {
                        if (!syncAllowed) {
                            Toast.makeText(MainActivity.this,
                                "Contact sync has been disabled by the administrator.",
                                Toast.LENGTH_LONG).show();
                        } else if (optedIn) {
                            confirmContactsOptOut();
                        } else {
                            confirmContactsOptIn();
                        }
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
                "• You can turn this off any time. Your synced contacts stay safely in the cloud so they're available when you sign in on another device.\n\n" +
                "By continuing you agree to our Privacy Policy at https://onephone.pro/privacy.")
            .setPositiveButton("Turn on", (d, w) -> {
                if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                        != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.WRITE_CONTACTS)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_CONTACTS}, 105);
                    return;
                }
                SyncManager.getInstance(MainActivity.this).setContactsOptedIn(true);
                SyncManager.getInstance(MainActivity.this).restoreContactsFromCloudAsync();
                SyncManager.getInstance(MainActivity.this).syncContactsAsync();
                Toast.makeText(MainActivity.this, "\u2713 Contact sync enabled", Toast.LENGTH_SHORT).show();
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
