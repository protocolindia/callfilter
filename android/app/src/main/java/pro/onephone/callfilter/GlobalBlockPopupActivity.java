package pro.onephone.callfilter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.*;
import java.io.File;

/**
 * Popup overlay shown when a call is blocked via Global Blocklist.
 * Shows: admin's image + block reason + admin display name + Dismiss button.
 * Appears over the lock screen immediately after call is rejected.
 */
public class GlobalBlockPopupActivity extends Activity {

    public static final String EXTRA_REASON     = "reason";
    public static final String EXTRA_ADMIN_NAME = "admin_name";
    public static final String EXTRA_IMAGE_PATH = "image_path";
    public static final String EXTRA_NUMBER     = "number";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over lock screen
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED  |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON    |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_global_block_popup);

        String reason    = getIntent().getStringExtra(EXTRA_REASON);
        String adminName = getIntent().getStringExtra(EXTRA_ADMIN_NAME);
        String imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        String number    = getIntent().getStringExtra(EXTRA_NUMBER);

        // Dismiss button
        Button btnDismiss = findViewById(R.id.popupBtnDismiss);
        btnDismiss.setOnClickListener(v -> finish());

        // Reason text
        TextView tvReason = findViewById(R.id.popupReason);
        tvReason.setText(reason != null ? reason : "Blocked");

        // Admin name
        TextView tvAdmin = findViewById(R.id.popupAdminName);
        tvAdmin.setText(adminName != null ? adminName : "Global Blocklist");

        // Number
        TextView tvNumber = findViewById(R.id.popupNumber);
        tvNumber.setText(number != null ? number : "Unknown");

        // Image
        ImageView imgView = findViewById(R.id.popupImage);
        if (imagePath != null) {
            try {
                File imgFile = new File(imagePath);
                if (imgFile.exists()) {
                    imgView.setImageBitmap(BitmapFactory.decodeFile(imagePath));
                    imgView.setVisibility(android.view.View.VISIBLE);
                } else {
                    imgView.setVisibility(android.view.View.GONE);
                }
            } catch (Exception e) {
                imgView.setVisibility(android.view.View.GONE);
            }
        } else {
            imgView.setVisibility(android.view.View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        finish(); // dismiss on back press too
    }
}
