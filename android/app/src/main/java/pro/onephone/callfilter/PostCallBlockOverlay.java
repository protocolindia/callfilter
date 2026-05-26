package pro.onephone.callfilter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

/**
 * Shows a "Block this number?" UI right after a call disconnects.
 *
 * Strategy:
 *   1) If the user has granted SYSTEM_ALERT_WINDOW (Display over other apps),
 *      we draw a floating popup that auto-dismisses after 8 seconds.
 *   2) Otherwise, we post a notification with a "Block" action button.
 *
 * Called by CallStateReceiver when phone state transitions back to IDLE
 * from a RINGING/OFFHOOK state.
 */
public class PostCallBlockOverlay {

    private static final String TAG = "PostCallOverlay";
    private static final String CHANNEL_ID = "post_call_block";
    private static final int NOTIF_ID = 4501;
    private static final long AUTO_DISMISS_MS = 8_000L;

    public static final String ACTION_BLOCK    = "pro.onephone.callfilter.BLOCK_FROM_NOTIF";
    public static final String EXTRA_NUMBER    = "number";

    /** Public entry point — invoked from CallStateReceiver. */
    public static void offer(final Context ctx, final String number) {
        Log.d(TAG, "offer() called with number=" + number);
        if (number == null || number.isEmpty()) {
            Log.d(TAG, "  → skipped: number is null/empty");
            return;
        }

        // Don't offer to block numbers that already have a rule
        for (Rule r : RulesManager.getInstance(ctx).getRules()) {
            if (r.matches(number)) {
                Log.d(TAG, "  → skipped: rule already exists (type=" + r.getType()
                    + " pattern=" + r.getPattern() + ")");
                return;
            }
        }
        // Don't offer to block your own contacts
        if (ContactsHelper.isContactNumber(ctx, number)) {
            Log.d(TAG, "  → skipped: number is in contacts");
            return;
        }

        boolean canOverlay = canDrawOverlay(ctx);
        Log.d(TAG, "  display path: " + (canOverlay ? "OVERLAY" : "NOTIFICATION")
            + " (canDrawOverlays=" + canOverlay + ")");
        if (canOverlay) {
            try {
                showOverlay(ctx, number);
                Log.d(TAG, "  → overlay shown successfully");
            } catch (Exception e) {
                // If overlay creation throws (rare but happens on some OEM builds
                // with strict launch restrictions), fall back to notification
                Log.w(TAG, "  overlay failed: " + e.getMessage() + " — falling back to notification");
                showNotification(ctx, number);
            }
        } else {
            showNotification(ctx, number);
            Log.d(TAG, "  → notification posted");
        }
    }

    private static boolean canDrawOverlay(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return Settings.canDrawOverlays(ctx);
    }

    private static void showOverlay(final Context ctx, final String number) {
        final WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) { showNotification(ctx, number); return; }

        final View view = LayoutInflater.from(ctx).inflate(R.layout.post_call_block_popup, null);
        ((TextView) view.findViewById(R.id.popupNumber)).setText(number);

        int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        int margin = (int) (16 * ctx.getResources().getDisplayMetrics().density);
        lp.y = margin * 3;

        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable autoDismiss = new Runnable() {
            public void run() {
                try { wm.removeViewImmediate(view); } catch (Exception ignored) {}
            }
        };

        View.OnClickListener dismiss = v -> {
            handler.removeCallbacks(autoDismiss);
            try { wm.removeViewImmediate(view); } catch (Exception ignored) {}
        };

        view.findViewById(R.id.popupDismiss).setOnClickListener(dismiss);
        view.findViewById(R.id.popupSkip).setOnClickListener(dismiss);
        view.findViewById(R.id.popupBlock).setOnClickListener(v -> {
            // Dismiss the overlay; the picker activity will do the rule add + record
            dismiss.onClick(v);
            Intent picker = new Intent(ctx, BlockReasonPickerActivity.class);
            picker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            picker.putExtra(BlockReasonPickerActivity.EXTRA_NUMBER, number);
            picker.putExtra(BlockReasonPickerActivity.EXTRA_BLOCK_NOW, true);
            try { ctx.startActivity(picker); }
            catch (Exception e) { Log.e(TAG, "Could not launch reason picker", e); }
        });

        try {
            wm.addView(view, lp);
            handler.postDelayed(autoDismiss, AUTO_DISMISS_MS);
        } catch (Exception e) {
            Log.e(TAG, "addView failed, falling back to notification", e);
            showNotification(ctx, number);
        }
    }

    private static void showNotification(Context ctx, String number) {
        ensureChannel(ctx);

        Intent blockIntent = new Intent(ctx, BlockReasonPickerActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(BlockReasonPickerActivity.EXTRA_NUMBER, number)
            .putExtra(BlockReasonPickerActivity.EXTRA_BLOCK_NOW, true);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent blockPi = PendingIntent.getActivity(
            ctx, (int) System.currentTimeMillis(), blockIntent, pendingFlags);

        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Block this number?")
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(AUTO_DISMISS_MS * 2)
            .addAction(R.mipmap.ic_launcher, "Block", blockPi)
            .build();

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, n);
    }

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID, "Block this number", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("After a call ends, offer to block the number");
        nm.createNotificationChannel(ch);
    }

    /** Broadcast receiver for the "Block" notification action. */
    public static class ActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (!ACTION_BLOCK.equals(intent.getAction())) return;
            String num = intent.getStringExtra(EXTRA_NUMBER);
            if (num == null || num.isEmpty()) return;
            RulesManager.getInstance(ctx).addRule(num, Rule.TYPE_PREFIX, Rule.ACTION_REJECT);
            SyncManager.getInstance(ctx).syncRulesAsync();
            Toast.makeText(ctx, "✗ Blocked " + num, Toast.LENGTH_SHORT).show();
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIF_ID);
        }
    }
}
