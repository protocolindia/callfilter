package pro.onephone.callfilter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Full-screen popup shown after a call is blocked by the global blocklist.
 *
 * Shows:
 *   - Custom image uploaded by the Global DB Admin
 *   - Block reason text
 *   - Admin organisation name
 *   - Dismiss button
 *
 * Launched by CallStateReceiver when rType = "global_list" and
 * the matched reason has a PopupConfig with has_image = true.
 */
public class GlobalBlocklistPopupActivity extends Activity {

    private static final String TAG = "GBLPopup";

    public static final String EXTRA_REASON     = "reason";
    public static final String EXTRA_ADMIN_NAME = "admin_name";
    public static final String EXTRA_ADMIN_ID   = "admin_id";
    public static final String EXTRA_IMAGE_URL  = "image_url";

    /** Launch this activity from any context. */
    public static void show(Context ctx, String reason,
                            String adminName, int adminId, String imageUrl) {
        Intent intent = new Intent(ctx, GlobalBlocklistPopupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                      | Intent.FLAG_ACTIVITY_CLEAR_TOP
                      | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_REASON,     reason);
        intent.putExtra(EXTRA_ADMIN_NAME, adminName);
        intent.putExtra(EXTRA_ADMIN_ID,   adminId);
        intent.putExtra(EXTRA_IMAGE_URL,  imageUrl);
        ctx.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make it look like a dialog
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_global_blocklist_popup);

        // Keep screen on while popup is visible
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                           | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                           | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        String reason    = getIntent().getStringExtra(EXTRA_REASON);
        String adminName = getIntent().getStringExtra(EXTRA_ADMIN_NAME);
        int    adminId   = getIntent().getIntExtra(EXTRA_ADMIN_ID, 0);
        String imageUrl  = getIntent().getStringExtra(EXTRA_IMAGE_URL);

        ImageView ivBanner  = findViewById(R.id.popupBanner);
        TextView  tvReason  = findViewById(R.id.popupReason);
        TextView  tvAdmin   = findViewById(R.id.popupAdminName);
        Button    btnDismiss = findViewById(R.id.popupDismiss);

        tvReason.setText("🛑 Blocked: " + (reason != null ? reason : "Unknown reason"));
        tvAdmin.setText("By: " + (adminName != null && !adminName.isEmpty() ? adminName : "Global Safety Team"));

        btnDismiss.setOnClickListener(v -> finish());

        // Load image from cache or download
        if (imageUrl != null && !imageUrl.isEmpty()) {
            loadImage(ivBanner, adminId, imageUrl);
        }
    }

    private void loadImage(ImageView iv, int adminId, String imageUrl) {
        // Check cache first
        File cacheFile = new File(getCacheDir(), "popup_" + adminId + ".jpg");
        if (cacheFile.exists() && cacheFile.length() > 0) {
            Bitmap bm = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
            if (bm != null) { iv.setImageBitmap(bm); return; }
        }

        // Download in background
        new Thread(() -> {
            try {
                // Build full URL from relative path
                String fullUrl = imageUrl.startsWith("http")
                    ? imageUrl
                    : AuthManager.BACKEND_URL + imageUrl;

                HttpURLConnection conn = (HttpURLConnection) new URL(fullUrl).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    try (InputStream is = conn.getInputStream();
                         FileOutputStream fos = new FileOutputStream(cacheFile)) {
                        byte[] buf = new byte[4096]; int n;
                        while ((n = is.read(buf)) > 0) fos.write(buf, 0, n);
                    }
                    final Bitmap bm = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                    if (bm != null) {
                        runOnUiThread(() -> iv.setImageBitmap(bm));
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "Image download failed: " + e.getMessage());
            }
        }, "PopupImageDownload").start();
    }
}
