package pro.onephone.callfilter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.File;

/**
 * Shows a floating overlay when a call is blocked by the global blocklist.
 * Uses TYPE_APPLICATION_OVERLAY (same as PostCallBlockOverlay) — the only
 * reliable way to display UI from a background service on all Android versions.
 *
 * Requires: SYSTEM_ALERT_WINDOW permission (already declared + typically granted
 * since PostCallBlockOverlay uses the same permission and works).
 */
public class GlobalBlockPopupManager {

    private static final String TAG           = "GblPopup";
    private static final long   AUTO_DISMISS  = 12_000L; // 12 seconds

    public static void show(final Context ctx,
                            final String number,
                            final String reason,
                            final String adminName,
                            final String imagePath) {

        Log.d(TAG, "show() called: number=" + number
            + " reason=" + reason
            + " adminName=" + adminName
            + " imagePath=" + imagePath);

        // Must inflate and add view on main thread
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // Check SYSTEM_ALERT_WINDOW permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !Settings.canDrawOverlays(ctx)) {
                    Log.w(TAG, "canDrawOverlays=false — cannot show popup");
                    return;
                }

                WindowManager wm =
                    (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                if (wm == null) { Log.w(TAG, "WindowManager null"); return; }

                // Inflate the popup view
                View view = LayoutInflater.from(ctx)
                    .inflate(R.layout.layout_global_block_popup, null);

                // Set data
                TextView tvNumber = view.findViewById(R.id.gbpNumber);
                TextView tvReason = view.findViewById(R.id.gbpReason);
                TextView tvAdmin  = view.findViewById(R.id.gbpAdminName);
                ImageView imgView = view.findViewById(R.id.gbpImage);
                Button    btnDis  = view.findViewById(R.id.gbpDismiss);

                if (tvNumber != null) tvNumber.setText(number);
                if (tvReason != null) tvReason.setText(reason != null ? reason : "Blocked");
                if (tvAdmin  != null) tvAdmin.setText(adminName != null ? adminName : "Global Blocklist");

                // Load image
                if (imgView != null && imagePath != null) {
                    File imgFile = new File(imagePath);
                    if (imgFile.exists()) {
                        Bitmap bmp = BitmapFactory.decodeFile(imagePath);
                        if (bmp != null) {
                            imgView.setImageBitmap(bmp);
                            imgView.setVisibility(View.VISIBLE);
                            Log.d(TAG, "Image loaded: " + imagePath);
                        } else {
                            imgView.setVisibility(View.GONE);
                            Log.w(TAG, "BitmapFactory returned null for: " + imagePath);
                        }
                    } else {
                        imgView.setVisibility(View.GONE);
                        Log.w(TAG, "Image file not found: " + imagePath);
                    }
                } else if (imgView != null) {
                    imgView.setVisibility(View.GONE);
                }

                // Window params — same approach as PostCallBlockOverlay
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.CENTER;

                // Auto-dismiss runnable
                final Runnable autoDismiss = () -> {
                    try { wm.removeViewImmediate(view); }
                    catch (Exception ignored) {}
                };

                Handler h = new Handler(Looper.getMainLooper());

                // Dismiss button
                View.OnClickListener dismiss = v -> {
                    h.removeCallbacks(autoDismiss);
                    try { wm.removeViewImmediate(view); }
                    catch (Exception ignored) {}
                };

                if (btnDis != null) btnDis.setOnClickListener(dismiss);

                // Add to window
                wm.addView(view, lp);
                h.postDelayed(autoDismiss, AUTO_DISMISS);
                Log.d(TAG, "Overlay added to WindowManager successfully");

            } catch (Exception e) {
                Log.e(TAG, "show() exception: " + e.getMessage(), e);
            }
        });
    }
}
