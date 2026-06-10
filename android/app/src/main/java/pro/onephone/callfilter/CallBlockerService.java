package pro.onephone.callfilter;

import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

public class CallBlockerService extends CallScreeningService {

    @Override
    public void onCreate() {
        super.onCreate();
        // Keep the global blocklist fresh: pull the latest from the server when
        // the screening service starts, so admin updates reach the device even
        // if the app UI hasn't been opened recently.
        try {
            if (AuthManager.getInstance(this).isLoggedIn()) {
                GlobalBlocklistManager.getInstance(this).syncAsync((ok, count, err) -> {});
            }
        } catch (Exception ignored) {}
    }
    private static final String TAG = "CallBlockerService";

    @Override
    public void onScreenCall(Call.Details callDetails) {
        String number = "";
        try {
            if (callDetails.getHandle() != null) {
                number = callDetails.getHandle().getSchemeSpecificPart();
                if (number == null) number = "";
            }
        } catch (Exception ignored) {}

        // === DIAGNOSTIC: this line proves CallScreeningService is being invoked.
        // If you don't see it in logcat when a call rings, then CallFilter is
        // NOT the default screening app — go to:
        //   Settings → Apps → Default apps → Caller ID & spam app → CallFilter
        Log.d(TAG, "=== onScreenCall ENTERED, number=" + number + " ===");

        // If the user is signed out, do nothing — let every call through.
        if (!AuthManager.getInstance(this).isLoggedIn()) {
            Log.d(TAG, "User signed out — screening disabled, allowing call");
            respondToCall(callDetails, new CallResponse.Builder().build());
            return;
        }

        // Snapshot of state we'll use to decide
        int rulesCount = RulesManager.getInstance(this).getRules().size();
        boolean subActive = SubscriptionManager.getInstance(this).isActive();
        boolean subChecked = SubscriptionManager.getInstance(this).hasBeenChecked();
        Log.d(TAG, "State: rules=" + rulesCount + " subActive=" + subActive
            + " subChecked=" + subChecked);

        // Stash for post-call popup: CallStateReceiver picks this up when state
        // returns to IDLE. On Android 10+, PHONE_STATE broadcasts no longer include
        // EXTRA_INCOMING_NUMBER, so CallScreeningService is the only place we get it.
        if (number != null && !number.isEmpty()) {
            getSharedPreferences("post_call_state", MODE_PRIVATE).edit()
                .putString("last_number", number)
                .putLong("last_number_ts", System.currentTimeMillis())
                .commit();
        }

        SubscriptionManager sub = SubscriptionManager.getInstance(this);
        if (sub.hasBeenChecked() && !sub.isActive()) {
            Log.d(TAG, "Subscription inactive — letting call through");
            respondToCall(callDetails, new CallResponse.Builder().build());
            return;
        }

        boolean shouldReject = false;
        String rType = "", rPattern = "", rAction = "reject";
        Schedule activeSchedule = null;

        RulesManager rules = RulesManager.getInstance(this);

        // 1. ACCEPT rules FIRST — explicit accept overrides everything (except Block All Now).
        //    User policy: accept rules are checked first, then reject rules.
        String acceptVerdict = rules.evaluateAccept(number);
        if ("accept".equals(acceptVerdict)) {
            Log.d(TAG, "ACCEPT rule matched — letting " + number + " through");
            respondToCall(callDetails, new CallResponse.Builder().build());
            return;
        }

        // 2. Block All Now (global panic mode)
        BlockAllManager blockAll = BlockAllManager.getInstance(this);
        if (blockAll.isActive() && !blockAll.isCallerAllowed(this, number)) {
            shouldReject = true;
            rType = "block_all"; rPattern = blockAll.getMode();
        }

        // 3. REJECT rules — log each rule we evaluate so we can see why a
        //    call wasn't matched (or which rule matched it).
        if (!shouldReject) {
            for (Rule r : rules.getRules()) {
                boolean matches = r.matches(number);
                Log.d(TAG, "  rule: type=" + r.getType() + " pattern=" + r.getPattern()
                    + " action=" + r.getAction() + " → matches(" + number + ")=" + matches);
                if (matches && "reject".equals(r.getAction())) {
                    shouldReject = true;
                    rType = r.getType(); rPattern = r.getPattern();
                    break;
                }
            }
        }

        // 4. Contacts-only mode
        if (!shouldReject && rules.isContactsOnlyMode()
            && !ContactsHelper.isContactNumber(this, number)) {
            shouldReject = true;
            rType = "contacts_only";
        }

        // 5. Schedule allowlist
        activeSchedule = ScheduleManager.getInstance(this)
            .getActiveSchedule(System.currentTimeMillis());
        if (!shouldReject && activeSchedule != null
            && !activeSchedule.isCallerAllowed(number)) {
            shouldReject = true;
            rType = "schedule"; rPattern = activeSchedule.name;
        }

        // 6. Global blocklist
        if (!shouldReject) {
            String globalReason = GlobalBlocklistManager.getInstance(this).isNumberBlocked(number);
            if (globalReason != null) {
                shouldReject = true; rType = "global_list"; rPattern = globalReason;
                Log.d(TAG, "GLOBAL BLOCKLIST matched: " + globalReason);
            }
        }

        // 7. Frequency-bypass — overrides ALL rejections (Q3 = bypass wins).
        // Uses the active schedule's frequency config. If no schedule is active
        // but Block All is, frequency-bypass is OFF (Block All has no freq config).
        if (shouldReject && activeSchedule != null && activeSchedule.freqEnabled) {
            FrequencyTracker ft = FrequencyTracker.getInstance(this);
            long now = System.currentTimeMillis();
            if (ft.shouldBypass(number, now,
                    activeSchedule.freqCount, activeSchedule.freqWindowMin)) {
                Log.d(TAG, "Frequency-bypass triggered for " + number
                    + " (>=" + activeSchedule.freqCount + " in "
                    + activeSchedule.freqWindowMin + " min) — letting through");
                respondToCall(callDetails, new CallResponse.Builder().build());
                return;
            }
        }

        if (shouldReject) {
            Log.d(TAG, "VERDICT: REJECT (type=" + rType + " pattern=" + rPattern + ")");
            // Auto-send SMS reply if enabled (Block All Now only)
            SmsAutoResponder.getInstance(this).sendIfEnabled(number, rType);
            // Determine who is responsible for this block (shown in Blocked Calls).
            String blockedBy;
            if ("global_list".equals(rType)) {
                showGlobalBlockPopup(number, rPattern);
                GlobalBlocklistManager.AdminConfig cfg =
                    GlobalBlocklistManager.getInstance(this).getAdminConfigForNumber(number);
                blockedBy = (cfg != null && cfg.displayName != null && !cfg.displayName.isEmpty())
                    ? cfg.displayName : "Global blocklist";
            } else {
                blockedBy = "Your rule";
            }
            FrequencyTracker.getInstance(this)
                .recordRejection(number, System.currentTimeMillis());
            BlockedCallsManager.getInstance(this)
                .recordBlock(number, rType, rPattern, rAction, blockedBy);
            CallResponse.Builder b = new CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(true)
                .setSkipNotification(true);
            respondToCall(callDetails, b.build());
        } else {
            Log.d(TAG, "VERDICT: ALLOW (no matching rule)");
            respondToCall(callDetails, new CallResponse.Builder().build());
        }
    }
    private void showGlobalBlockPopup(String number, String reason) {
        try {
            GlobalBlocklistManager.AdminConfig cfg =
                GlobalBlocklistManager.getInstance(this).getAdminConfigForNumber(number);
            if (cfg == null) {
                android.util.Log.d("CallBlockerService",
                    "No admin config for number: " + number + " (total configs in map might be 0)");
                return;
            }
            if (cfg.popupImagePath == null) {
                android.util.Log.d("CallBlockerService",
                    "Admin has no popup image: " + cfg.displayName);
                return;
            }
            // Use notification full-screen intent — reliable on Android 10+
            GlobalBlockPopupManager.show(this, number, reason,
                cfg.displayName, cfg.popupImagePath);
        } catch (Exception e) {
            android.util.Log.w("CallBlockerService", "showGlobalBlockPopup: " + e);
        }
    }

}
