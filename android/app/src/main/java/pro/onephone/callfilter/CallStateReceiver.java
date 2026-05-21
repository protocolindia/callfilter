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
    // Track previous state across broadcasts so we can detect "call ended" transitions
    private static String lastState = TelephonyManager.EXTRA_STATE_IDLE;
    private static String lastNumber = null;

    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        // Detect call-ended (RINGING|OFFHOOK -> IDLE) and offer the block popup
        if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            boolean wasActive = TelephonyManager.EXTRA_STATE_RINGING.equals(lastState)
                            || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(lastState);
            if (wasActive) {
                // 1. Try the broadcast extra (works on pre-10)
                String popupNumber = lastNumber;
                // 2. Fall back to the number stashed by CallBlockerService (post-10)
                if (popupNumber == null || popupNumber.isEmpty()) {
                    android.content.SharedPreferences sp = context.getSharedPreferences(
                        "post_call_state", android.content.Context.MODE_PRIVATE);
                    long stashTs = sp.getLong("last_number_ts", 0L);
                    // Only use if stash is recent (within 5 minutes)
                    if (System.currentTimeMillis() - stashTs < 5L * 60 * 1000L) {
                        popupNumber = sp.getString("last_number", null);
                    }
                    sp.edit().remove("last_number").remove("last_number_ts").apply();
                }
                if (popupNumber != null && !popupNumber.isEmpty()) {
                    Log.d(TAG, "Call ended — offering post-call popup for " + popupNumber);
                    try { PostCallBlockOverlay.offer(context, popupNumber); }
                    catch (Exception e) { Log.w(TAG, "post-call overlay failed: " + e); }
                } else {
                    Log.d(TAG, "Call ended but no number available for popup");
                }
            }
            lastState = state;
            lastNumber = null;
            return;
        }
        // Remember the number from RINGING for the IDLE transition above
        if (number != null && !number.isEmpty()) lastNumber = number;
        lastState = state;

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
