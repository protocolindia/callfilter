package pro.onephone.callfilter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.lang.reflect.Method;

public class CallStateReceiver extends BroadcastReceiver {
    private static final String TAG = "CallStateReceiver";

    // Track previous state across broadcasts so we can detect "call ended" transitions
    private static String lastState = TelephonyManager.EXTRA_STATE_IDLE;
    private static String lastNumber = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        // If signed out, don't show any post-call popups.
        if (!AuthManager.getInstance(context).isLoggedIn()) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        Log.d(TAG, "PHONE_STATE event: state=" + state + " number="
            + (number != null && !number.isEmpty() ? "(present)" : "(missing)"));

        // Detect call-ended (RINGING|OFFHOOK -> IDLE) and offer the block popup
        if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            boolean wasActive = TelephonyManager.EXTRA_STATE_RINGING.equals(lastState)
                            || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(lastState);
            if (wasActive) {
                final String captured = lastNumber;  // closure for delayed lookup
                final Context appCtx = context.getApplicationContext();
                // Delay briefly to let the CallLog entry get written
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    String popupNumber = captured;
                    // 1. Try the broadcast extra (works on pre-10)
                    // 2. Fall back to the SharedPrefs stash from CallBlockerService (post-10,
                    //    only when we're the call screener)
                    if (popupNumber == null || popupNumber.isEmpty()) {
                        android.content.SharedPreferences sp = appCtx.getSharedPreferences(
                            "post_call_state", android.content.Context.MODE_PRIVATE);
                        long stashTs = sp.getLong("last_number_ts", 0L);
                        if (System.currentTimeMillis() - stashTs < 5L * 60 * 1000L) {
                            popupNumber = sp.getString("last_number", null);
                        }
                        sp.edit().remove("last_number").remove("last_number_ts").apply();
                    }
                    // 3. Final fallback: read the latest CallLog entry. Works even when
                    //    we're NOT the active call screener and Android 10+ stripped the
                    //    EXTRA_INCOMING_NUMBER from the broadcast.
                    if (popupNumber == null || popupNumber.isEmpty()) {
                        popupNumber = readLatestCallLogNumber(appCtx);
                        if (popupNumber != null && !popupNumber.isEmpty()) {
                            Log.d(TAG, "Got number from CallLog fallback: " + popupNumber);
                        }
                    }
                    if (popupNumber != null && !popupNumber.isEmpty()) {
                        Log.d(TAG, "Call ended — offering popup for " + popupNumber);
                        try { PostCallBlockOverlay.offer(appCtx, popupNumber); }
                        catch (Exception e) { Log.w(TAG, "post-call overlay failed: " + e); }

                        // Show popup if global_list block had an image configured
                        try {
                            android.content.SharedPreferences popupState = appCtx
                                .getSharedPreferences("gbl_popup_state", android.content.Context.MODE_PRIVATE);
                            if (popupState.getBoolean("pending", false)) {
                                String glNumber = popupState.getString("last_number", "");
                                String glReason = popupState.getString("last_reason", "");
                                popupState.edit().putBoolean("pending", false).commit();
                                GlobalBlocklistManager.AdminConfig cfg =
                                    GlobalBlocklistManager.getInstance(appCtx)
                                        .getAdminConfigForNumber(glNumber);
                                if (cfg != null && cfg.popupImagePath != null) {
                                    GlobalBlockPopupManager.show(appCtx, glNumber, glReason,
                                        cfg.displayName, cfg.popupImagePath);
                                }
                            }
                        } catch (Exception e) { Log.w(TAG, "gbl popup failed: " + e); }
                    } else {
                        Log.d(TAG, "Call ended but no number available — popup skipped");
                    }
                }, 1200L);
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

        // 6. Global blocklist
        if (!shouldReject) {
            String globalReason = GlobalBlocklistManager.getInstance(context).isNumberBlocked(number);
            if (globalReason != null) {
                shouldReject = true; rType = "global_list"; rPattern = globalReason;
            }
        }

        // 7. Frequency-bypass
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
            // Auto-send SMS reply if enabled (Block All Now only)
            SmsAutoResponder.getInstance(context).sendIfEnabled(number, rType);
            // Show global block popup if this was a global_list block
            if ("global_list".equals(rType)) {
                try {
                    GlobalBlocklistManager.AdminConfig cfg =
                        GlobalBlocklistManager.getInstance(context).getAdminConfigForNumber(number);
                    if (cfg != null && cfg.popupImagePath != null) {
                        GlobalBlockPopupManager.show(context, number, rPattern,
                            cfg.displayName, cfg.popupImagePath);
                    }
                } catch (Exception ex) { android.util.Log.w("CallStateReceiver", "popup: "+ex); }
            }
            // Flag for post-call popup if this was a global_list block
            if ("global_list".equals(rType)) {
                context.getSharedPreferences("gbl_popup_state", android.content.Context.MODE_PRIVATE)
                    .edit().putString("last_reason", rPattern).putBoolean("pending", true).commit();
            }
            endCall(context);
            // Store the last global_list block info for popup after call disconnects
            if ("global_list".equals(rType)) {
                context.getSharedPreferences("gbl_popup_state", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_reason",  rPattern)
                    .putBoolean("pending",      true)
                    .commit();
            }
        }
    }

    /** Reads the most recent CallLog entry's NUMBER as a last-ditch fallback.
     *  Requires READ_CALL_LOG (we already have it for the Recent Calls screen). */
    private static String readLatestCallLogNumber(Context ctx) {
        try {
            // Permission check — silently skip if not granted
            if (androidx.core.content.ContextCompat.checkSelfPermission(ctx,
                    android.Manifest.permission.READ_CALL_LOG)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            android.database.Cursor cur = ctx.getContentResolver().query(
                android.provider.CallLog.Calls.CONTENT_URI,
                new String[] { android.provider.CallLog.Calls.NUMBER,
                               android.provider.CallLog.Calls.DATE },
                null, null,
                android.provider.CallLog.Calls.DATE + " DESC LIMIT 1");
            if (cur == null) return null;
            try {
                if (cur.moveToFirst()) {
                    int idx = cur.getColumnIndex(android.provider.CallLog.Calls.NUMBER);
                    if (idx >= 0) {
                        String n = cur.getString(idx);
                        if (n != null) n = n.trim();
                        return (n == null || n.isEmpty()) ? null : n;
                    }
                }
            } finally { cur.close(); }
        } catch (Exception e) {
            Log.w(TAG, "readLatestCallLogNumber failed: " + e.getMessage());
        }
        return null;
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
