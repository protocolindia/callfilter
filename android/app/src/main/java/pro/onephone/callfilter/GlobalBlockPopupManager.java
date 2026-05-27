package pro.onephone.callfilter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Launches the GlobalBlockPopupActivity reliably on all Android versions.
 *
 * Android 10+ blocks startActivity() from background services/receivers.
 * Solution: send a high-priority notification with setFullScreenIntent — the
 * OS automatically launches the activity as a "heads-up" full-screen overlay,
 * exactly like an incoming call screen.
 */
public class GlobalBlockPopupManager {

    private static final String TAG     = "GblPopup";
    private static final String CHANNEL = "global_block_popup";
    private static final int    NOTIF_ID = 9001;

    public static void show(Context ctx, String number, String reason,
                            String adminName, String imagePath) {
        try {
            NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            // Create notification channel (Android 8+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(
                    CHANNEL,
                    "Blocked Call Alert",
                    NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Popup shown when a global blocklist call is rejected");
                ch.enableLights(false);
                ch.enableVibration(false);
                ch.setSound(null, null);
                nm.createNotificationChannel(ch);
            }

            // Build intent for the popup activity
            Intent intent = new Intent(ctx, GlobalBlockPopupActivity.class);
            intent.putExtra(GlobalBlockPopupActivity.EXTRA_NUMBER,     number);
            intent.putExtra(GlobalBlockPopupActivity.EXTRA_REASON,     reason);
            intent.putExtra(GlobalBlockPopupActivity.EXTRA_ADMIN_NAME, adminName);
            intent.putExtra(GlobalBlockPopupActivity.EXTRA_IMAGE_PATH, imagePath);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                          | Intent.FLAG_ACTIVITY_CLEAR_TOP
                          | Intent.FLAG_ACTIVITY_SINGLE_TOP
                          | Intent.FLAG_ACTIVITY_NO_HISTORY);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                flags |= PendingIntent.FLAG_IMMUTABLE;

            PendingIntent pi = PendingIntent.getActivity(ctx, NOTIF_ID, intent, flags);

            // High-priority notification with full-screen intent
            Notification.Builder builder = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Call Blocked")
                .setContentText(reason + " — " + (adminName != null ? adminName : "Global Blocklist"))
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(pi, true)   // ← this launches the activity reliably
                .setAutoCancel(true)
                .setOngoing(false);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setPriority(Notification.PRIORITY_MAX);
            }

            nm.notify(NOTIF_ID, builder.build());

            // Cancel the notification after 6 seconds (activity is already visible by then)
            new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> nm.cancel(NOTIF_ID), 6000);

            Log.d(TAG, "Popup notification sent for: " + number + " reason=" + reason);
        } catch (Exception e) {
            Log.e(TAG, "show() failed: " + e.getMessage());
        }
    }
}
