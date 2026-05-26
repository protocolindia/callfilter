package pro.onephone.callfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.SmsManager;
import android.util.Log;

/**
 * Automatically sends a configurable SMS reply when a call is blocked.
 *
 * Settings stored in SharedPreferences:
 *   sms_auto_enabled  — "true" / "false"
 *   sms_auto_message  — the message text
 */
public class SmsAutoResponder {

    private static final String TAG   = "SmsAutoResponder";
    private static final String PREFS = "sms_auto_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_MESSAGE = "message";

    public static final String DEFAULT_MESSAGE =
        "Sorry, I'm currently unavailable and your call has been filtered. Please send a message.";

    private final SharedPreferences prefs;
    private static SmsAutoResponder instance;

    public static synchronized SmsAutoResponder getInstance(Context ctx) {
        if (instance == null) instance = new SmsAutoResponder(ctx.getApplicationContext());
        return instance;
    }

    private SmsAutoResponder(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ── Settings ─────────────────────────────────────────────────────────

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).commit();
    }

    public String getMessage() {
        return prefs.getString(KEY_MESSAGE, DEFAULT_MESSAGE);
    }

    public void setMessage(String msg) {
        if (msg == null || msg.trim().isEmpty()) msg = DEFAULT_MESSAGE;
        prefs.edit().putString(KEY_MESSAGE, msg.trim()).commit();
    }

    // ── Send ─────────────────────────────────────────────────────────────

    /**
     * Send an auto-reply SMS to the given number if auto-reply is enabled.
     * Runs on a background thread — safe to call from any thread.
     * @param number  The caller's number (e.g. "+919876543210")
     * @param reason  Why the call was blocked (shown in log only, not the SMS)
     */
    public void sendIfEnabled(final String number, final String reason) {
        if (!isEnabled()) return;
        if (number == null || number.isEmpty()) return;
        // Don't SMS private/unknown numbers
        if (number.equalsIgnoreCase("Unknown") || number.equalsIgnoreCase("Private")) return;

        final String msg = getMessage();
        new Thread(() -> {
            try {
                SmsManager sms = SmsManager.getDefault();
                // Split long messages automatically
                if (msg.length() > 160) {
                    sms.sendMultipartTextMessage(number, null,
                        sms.divideMessage(msg), null, null);
                } else {
                    sms.sendTextMessage(number, null, msg, null, null);
                }
                Log.d(TAG, "Auto-SMS sent to " + number + " (reason: " + reason + ")");
            } catch (Exception e) {
                Log.w(TAG, "Auto-SMS failed to " + number + ": " + e.getMessage());
            }
        }).start();
    }
}
