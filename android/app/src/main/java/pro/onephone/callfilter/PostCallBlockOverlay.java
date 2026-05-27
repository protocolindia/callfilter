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

        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] autoDismissRef = new Runnable[1];

        View.OnClickListener dismiss = v -> {
            if (autoDismissRef[0] != null) handler.removeCallbacks(autoDismissRef[0]);
            try { wm.removeViewImmediate(view); } catch (Exception ignored) {}
        };

        // Skip just dismisses
        view.findViewById(R.id.popupDismiss).setOnClickListener(dismiss);
        view.findViewById(R.id.popupSkip).setOnClickListener(dismiss);

        // Build reason buttons — each tap = instant block + dismiss
        android.widget.LinearLayout chipsContainer = view.findViewById(R.id.reasonChipsContainer);
        if (chipsContainer != null) {
            java.util.List<String> reasons = BlockReasonsCache.getInstance(ctx).get();
            float dp = ctx.getResources().getDisplayMetrics().density;
            int marginV = (int)(6 * dp);
            int padH = (int)(20 * dp);
            int padV = (int)(14 * dp);

            for (String reason : reasons) {
                android.widget.TextView btn = new android.widget.TextView(ctx);
                btn.setText(reason);
                btn.setTextColor(0xFFFFFFFF);
                btn.setTextSize(15f);
                btn.setTypeface(null, android.graphics.Typeface.BOLD);
                btn.setPadding(padH, padV, padH, padV);
                btn.setClickable(true);
                btn.setFocusable(true);

                // Rounded background
                android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
                bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                bg.setCornerRadius(10 * dp);
                bg.setColor(0xFF1E1E26);
                bg.setStroke((int)(1.5f * dp), 0xFF2D2E36);
                btn.setBackground(bg);

                android.widget.LinearLayout.LayoutParams lp2 =
                    new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                lp2.setMargins(0, marginV, 0, marginV);
                btn.setLayoutParams(lp2);
                btn.setForeground(ctx.obtainStyledAttributes(
                    new int[]{android.R.attr.selectableItemBackground}).getDrawable(0));

                final String thisReason = reason;
                btn.setOnClickListener(v -> {
                    // Highlight selected
                    bg.setColor(0xFF1A2A4A);
                    bg.setStroke((int)(2 * dp), 0xFF4F8EF7);
                    // Block with this reason — no second confirmation
                    handler.postDelayed(() -> {
                        dismiss.onClick(v);
                        RulesManager.getInstance(ctx).addRule(
                            number, Rule.TYPE_PREFIX, Rule.ACTION_REJECT);
                        SyncManager.getInstance(ctx).syncRulesAsync();
                        BlockedCallsManager.getInstance(ctx).recordBlock(
                            number, "manual", thisReason, "reject");
                        SyncManager.getInstance(ctx).syncBlockedCallsAsync();
                        Toast.makeText(ctx,
                            "✗ Blocked: " + number + " (" + thisReason + ")",
                            Toast.LENGTH_SHORT).show();
                    }, 200); // 200ms to show highlight before dismissing
                });

                chipsContainer.addView(btn);
            }
        }

        // Full-screen overlay
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;

        autoDismissRef[0] = () -> {
            try { wm.removeViewImmediate(view); } catch (Exception ignored) {}
        };

        try {
            wm.addView(view, lp);
            handler.postDelayed(autoDismissRef[0], AUTO_DISMISS_MS);
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
