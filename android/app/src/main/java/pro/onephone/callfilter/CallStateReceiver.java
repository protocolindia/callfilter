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
        if (sub.hasBeenChecked() && !sub.isActive()) {
            Log.d(TAG, "Subscription inactive — not blocking " + number);
            return;
        }

        boolean shouldReject = false;
        String rType = "", rPattern = "", rAction = "reject";

        RulesManager rules = RulesManager.getInstance(context);
        String verdict = rules.evaluate(number);
        if ("reject".equals(verdict)) {
            shouldReject = true;
            for (Rule r : rules.getRules()) {
                if (r.matches(number) && "reject".equals(r.getAction())) {
                    rType = r.getType(); rPattern = r.getPattern(); break;
                }
            }
        } else if ("accept".equals(verdict)) {
            return;
        }

        if (!shouldReject && rules.isContactsOnlyMode()) {
            if (!ContactsHelper.isContactNumber(context, number)) {
                shouldReject = true;
                rType = "contacts_only";
            }
        }

        // Schedule check
        if (!shouldReject) {
            Schedule active = ScheduleManager.getInstance(context)
                .getActiveSchedule(System.currentTimeMillis());
            if (active != null && !active.isCallerAllowed(number)) {
                shouldReject = true;
                rType = "schedule"; rPattern = active.name;
            }
        }

        if (shouldReject) {
            BlockedCallsManager.getInstance(context).recordBlock(number, rType, rPattern, rAction);
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
