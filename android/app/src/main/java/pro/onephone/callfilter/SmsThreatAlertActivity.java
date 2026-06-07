package pro.onephone.callfilter;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Full-screen warning shown when a suspicious SMS arrives, styled like an
 * incoming-call screen. Launched via a full-screen-intent notification so it
 * appears even over the lock screen.
 */
public class SmsThreatAlertActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE   = "title";
    public static final String EXTRA_SENDER  = "sender";
    public static final String EXTRA_REASONS = "reasons";
    public static final String EXTRA_PREVIEW = "preview";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over the lock screen and turn the screen on (like a call).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        setContentView(R.layout.activity_sms_threat_alert);

        String title   = getIntent().getStringExtra(EXTRA_TITLE);
        String sender  = getIntent().getStringExtra(EXTRA_SENDER);
        String reasons = getIntent().getStringExtra(EXTRA_REASONS);
        String preview = getIntent().getStringExtra(EXTRA_PREVIEW);

        if (title != null)  ((TextView) findViewById(R.id.alertTitle)).setText(title);
        ((TextView) findViewById(R.id.alertSender)).setText("From: " + (sender == null ? "Unknown" : sender));
        if (reasons != null && !reasons.isEmpty())
            ((TextView) findViewById(R.id.alertReasons)).setText(reasons);
        if (preview != null)
            ((TextView) findViewById(R.id.alertPreview)).setText(preview);

        findViewById(R.id.alertDismiss).setOnClickListener(v -> finish());
        findViewById(R.id.alertViewList).setOnClickListener(v -> {
            startActivity(new Intent(this, FlaggedSmsActivity.class));
            finish();
        });
    }
}
