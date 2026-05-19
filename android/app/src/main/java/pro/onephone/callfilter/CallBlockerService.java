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

        // Subscription gate
        SubscriptionManager sub = SubscriptionManager.getInstance(this);
        if (sub.hasBeenChecked() && !sub.isActive()) {
            Log.d(TAG, "Subscription inactive — letting call through");
            respondToCall(callDetails, new CallResponse.Builder().build());
            return;
        }

        boolean shouldReject = false;
        String rType = "", rPattern = "", rAction = "reject";

        // Evaluate rules
        RulesManager rules = RulesManager.getInstance(this);
        String verdict = rules.evaluate(number);
        if ("reject".equals(verdict)) {
            shouldReject = true;
            for (Rule r : rules.getRules()) {
                if (r.matches(number) && "reject".equals(r.getAction())) {
                    rType = r.getType(); rPattern = r.getPattern(); rAction = "reject";
                    break;
                }
            }
        } else if ("accept".equals(verdict)) {
            // explicit accept — bypass everything else
            respondToCall(callDetails, new CallResponse.Builder().build());
            return;
        }

        // Contacts-only mode
        if (!shouldReject && rules.isContactsOnlyMode()) {
            if (!ContactsHelper.isContactNumber(this, number)) {
                shouldReject = true;
                rType = "contacts_only"; rPattern = ""; rAction = "reject";
            }
        }

        // Schedule check — Option C: existing rules + schedule allowlist
        if (!shouldReject) {
            Schedule activeSchedule = ScheduleManager.getInstance(this)
                .getActiveSchedule(System.currentTimeMillis());
            if (activeSchedule != null && !activeSchedule.isCallerAllowed(number)) {
                Log.d(TAG, "Schedule \"" + activeSchedule.name + "\" — rejecting " + number);
                shouldReject = true;
                rType = "schedule"; rPattern = activeSchedule.name; rAction = "reject";
            }
        }

        if (shouldReject) {
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
