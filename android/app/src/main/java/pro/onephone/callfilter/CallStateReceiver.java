package pro.onephone.callfilter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.lang.reflect.Method;

public class CallStateReceiver extends BroadcastReceiver {
    private static final String TAG = "CallStateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        if (!TelephonyManager.EXTRA_STATE_RINGING.equals(state)) return;
        if (number == null || number.isEmpty()) return;

        SubscriptionManager sub = SubscriptionManager.getInstance(context);
        if (sub.hasBeenChecked() && !sub.isActive()) return;

        boolean shouldReject = false;
        String rType = "", rPattern = "", rAction = "reject";
        Schedule activeSchedule = null;

        RulesManager rules = RulesManager.getInstance(context);

        // 1. ACCEPT rules first — explicit accept wins over Block All Now too
        if ("accept".equals(rules.evaluateAccept(number))) {
            Log.d(TAG, "ACCEPT rule matched — letting " + number + " through");
            return;
        }

        // 2. Block All Now
        BlockAllManager blockAll = BlockAllManager.getInstance(context);
        if (blockAll.isActive() && !blockAll.isCallerAllowed(context, number)) {
            shouldReject = true;
            rType = "block_all"; rPattern = blockAll.getMode();
        }

        // 3. Reject rules
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
            && !ContactsHelper.isContactNumber(context, number)) {
            shouldReject = true;
            rType = "contacts_only";
        }

        // 5. Schedule allowlist
        activeSchedule = ScheduleManager.getInstance(context)
            .getActiveSchedule(System.currentTimeMillis());
        if (!shouldReject && activeSchedule != null
            && !activeSchedule.isCallerAllowed(number)) {
            shouldReject = true;
            rType = "schedule"; rPattern = activeSchedule.name;
        }

        // 6. Frequency-bypass
        if (shouldReject && activeSchedule != null && activeSchedule.freqEnabled) {
            FrequencyTracker ft = FrequencyTracker.getInstance(context);
            if (ft.shouldBypass(number, System.currentTimeMillis(),
                    activeSchedule.freqCount, activeSchedule.freqWindowMin)) {
                Log.d(TAG, "Frequency-bypass for " + number);
                return;
            }
        }

        if (shouldReject) {
            FrequencyTracker.getInstance(context)
                .recordRejection(number, System.currentTimeMillis());
            BlockedCallsManager.getInstance(context)
                .recordBlock(number, rType, rPattern, rAction);
            endCall(context);
        }
    }

    private void endCall(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            Method m = TelephonyManager.class.getDeclaredMethod("getITelephony");
            m.setAccessible(true);
            Object tel = m.invoke(tm);
            Method end = tel.getClass().getDeclaredMethod("endCall");
            end.setAccessible(true);
            end.invoke(tel);
        } catch (Exception e) {
            Log.w(TAG, "endCall failed: " + e.getMessage());
        }
    }
}
