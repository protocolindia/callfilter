package pro.onephone.callfilter;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import androidx.core.app.NotificationCompat;

/**
 * Passive SMS scanner (Path A). Listens for incoming SMS, runs the on-device
 * threat detector, and — if the message looks like phishing/spam — raises a
 * warning notification and records it. The SMS itself is left untouched in the
 * user's normal messaging app.
 */
public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";
    private static final String CHANNEL_ID = "sms_threats";
    private static final int NOTIF_BASE = 7000;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction()))
            return;

        // Respect login + the user's protection toggle
        if (!AuthManager.getInstance(context).isLoggedIn()) return;
        SmsThreatDetector detector = SmsThreatDetector.getInstance(context);
        if (!detector.isEnabled()) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        StringBuilder bodyBuilder = new StringBuilder();
        String sender = null;
        try {
            SmsMessage[] msgs = getMessages(intent, bundle);
            for (SmsMessage m : msgs) {
                if (m == null) continue;
                if (sender == null) sender = m.getOriginatingAddress();
                bodyBuilder.append(m.getMessageBody());
            }
        } catch (Exception e) {
            Log.w(TAG, "parse error: " + e.getMessage());
            return;
        }

        final String body = bodyBuilder.toString();
        if (body.isEmpty()) return;
        final String from = sender != null ? sender : "Unknown";

        SmsThreatDetector.Result result = detector.analyze(body);
        if (!result.flagged) return;

        // Record + warn
        FlaggedSmsStore.getInstance(context).record(from, body, result);
        showWarning(context, from, body, result);
        Log.d(TAG, "Flagged SMS from " + from + " score=" + result.score);
    }

    private SmsMessage[] getMessages(Intent intent, Bundle bundle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return android.provider.Telephony.Sms.Intents.getMessagesFromIntent(intent);
        }
        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return new SmsMessage[0];
        SmsMessage[] out = new SmsMessage[pdus.length];
        for (int i = 0; i < pdus.length; i++) {
            out[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
        }
        return out;
    }

    public static void showWarning(Context ctx, String from, String body, SmsThreatDetector.Result r) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                "SMS Threat Warnings", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Alerts when a suspicious SMS is detected");
            nm.createNotificationChannel(ch);
        }

        String title = ("phishing".equals(r.category) ? "\u26A0\uFE0F Possible phishing SMS"
                                                       : "\u26A0\uFE0F Possible spam SMS");
        String reason = r.reasons.isEmpty() ? "Looks suspicious"
                                            : android.text.TextUtils.join(", ", r.reasons);
        String preview = body.length() > 140 ? body.substring(0, 140) + "\u2026" : body;

        // Full-screen alert (like an incoming call)
        Intent full = new Intent(ctx, SmsThreatAlertActivity.class);
        full.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        full.putExtra(SmsThreatAlertActivity.EXTRA_TITLE, title);
        full.putExtra(SmsThreatAlertActivity.EXTRA_SENDER, from);
        full.putExtra(SmsThreatAlertActivity.EXTRA_REASONS, reason);
        full.putExtra(SmsThreatAlertActivity.EXTRA_PREVIEW, preview);
        android.app.PendingIntent fullPi = android.app.PendingIntent.getActivity(
            ctx, (from + body).hashCode(), full,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_shield)
            .setContentTitle(title)
            .setContentText("From " + from + " \u00B7 " + reason)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(
                "From " + from + "\n" + reason + "\n\nScore: " + r.score
                + "/100. Do not tap links or share codes."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(fullPi)
            .setFullScreenIntent(fullPi, true);   // pop full-screen like a call

        try { nm.notify(NOTIF_BASE + (from.hashCode() & 0xFFF), b.build()); }
        catch (SecurityException ignored) {}

        // Also try to launch the full-screen activity directly (best-effort)
        try { ctx.startActivity(full); } catch (Exception ignored) {}
    }
}
