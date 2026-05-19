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

        SubscriptionManager sub = SubscriptionManager.getInstance(this);
        if (sub.hasBeenChecked() && !sub.isActive()) {
            Log.d(TAG, "Subscription inactive — letting call through");
            respondToCall(callDetails, new CallResponse.Builder().build());
            return;
        }

        boolean shouldReject = false;
        String rType = "", rPattern = "", rAction = "reject";
        Schedule activeSchedule = null;

        // 1. Block All Now (global)
        BlockAllManager blockAll = BlockAllManager.getInstance(this);
        if (blockAll.isActive() && !blockAll.isCallerAllowed(this, number)) {
            shouldReject = true;
            rType = "block_all"; rPattern = blockAll.getMode();
        }

        // 2. Block rules
        if (!shouldReject) {
            RulesManager rules = RulesManager.getInstance(this);
            String verdict = rules.evaluate(number);
            if ("reject".equals(verdict)) {
                shouldReject = true;
                for (Rule r : rules.getRules()) {
                    if (r.matches(number) && "reject".equals(r.getAction())) {
                        rType = r.getType(); rPattern = r.getPattern();
                        break;
                    }
                }
            } else if ("accept".equals(verdict)) {
                respondToCall(callDetails, new CallResponse.Builder().build());
                return;
            }
        }

        // 3. Contacts-only
        if (!shouldReject) {
            RulesManager rules = RulesManager.getInstance(this);
            if (rules.isContactsOnlyMode()
                && !ContactsHelper.isContactNumber(this, number)) {
                shouldReject = true;
                rType = "contacts_only";
            }
        }

        // 4. Schedule allowlist
        activeSchedule = ScheduleManager.getInstance(this)
            .getActiveSchedule(System.currentTimeMillis());
        if (!shouldReject && activeSchedule != null
            && !activeSchedule.isCallerAllowed(number)) {
            shouldReject = true;
            rType = "schedule"; rPattern = activeSchedule.name;
        }

        // 5. Frequency-bypass — overrides ALL rejections (Q3 = bypass wins).
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
