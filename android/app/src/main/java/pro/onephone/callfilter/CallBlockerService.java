package pro.onephone.callfilter;

import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

public class CallBlockerService extends CallScreeningService {
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

        Log.d(TAG, "Incoming call from: " + number);

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

        // 3. REJECT rules
        if (!shouldReject) {
            String verdict = rules.evaluateReject(number);
            if ("reject".equals(verdict)) {
                shouldReject = true;
                for (Rule r : rules.getRules()) {
                    if (r.matches(number) && "reject".equals(r.getAction())) {
                        rType = r.getType(); rPattern = r.getPattern();
                        break;
                    }
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

        // 6. Frequency-bypass — overrides ALL rejections (Q3 = bypass wins).
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
            FrequencyTracker.getInstance(this)
                .recordRejection(number, System.currentTimeMillis());
            BlockedCallsManager.getInstance(this)
                .recordBlock(number, rType, rPattern, rAction);
            CallResponse.Builder b = new CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(true)
                .setSkipNotification(true);
            respondToCall(callDetails, b.build());
        } else {
            respondToCall(callDetails, new CallResponse.Builder().build());
        }
    }
}
