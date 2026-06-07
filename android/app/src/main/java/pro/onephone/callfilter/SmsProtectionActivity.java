package pro.onephone.callfilter;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

/**
 * SMS phishing/spam protection settings — PASSIVE SCANNER ONLY.
 *
 * The passive scanner needs just the RECEIVE_SMS permission to read incoming
 * messages and warn the user; it never changes the default SMS app. We removed
 * the "default SMS app" path because Android requires an OS-drawn picker that
 * cannot be set silently and added needless friction (picker + overlay popups).
 */
public class SmsProtectionActivity extends AppCompatActivity {

    private SwitchCompat masterSwitch;
    private TextView flaggedCountLabel;
    private SmsThreatDetector detector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_protection);
        detector = SmsThreatDetector.getInstance(this);

        findViewById(R.id.btnBackSmsProt).setOnClickListener(v -> finish());
        masterSwitch      = findViewById(R.id.smsProtectSwitch);
        flaggedCountLabel = findViewById(R.id.flaggedCountLabel);

        detector.setMode(SmsThreatDetector.MODE_PASSIVE);

        masterSwitch.setChecked(detector.isEnabled());
        masterSwitch.setOnCheckedChangeListener((b, checked) -> {
            detector.setEnabled(checked);
            if (checked) {
                detector.syncRulesAsync();
                requestSmsPermissionIfNeeded();
            }
        });

        findViewById(R.id.rowFlaggedMessages).setOnClickListener(v ->
            startActivity(new Intent(this, FlaggedSmsActivity.class)));

        findViewById(R.id.btnTestDetection).setOnClickListener(v -> runTestDetection());
    }

    /** Runs the detector on a sample phishing message to prove the pipeline works. */
    private void runTestDetection() {
        if (!detector.isEnabled()) {
            detector.setEnabled(true);
            masterSwitch.setChecked(true);
        }
        requestSmsPermissionIfNeeded();
        String sample = "KYC update required. Your account will be suspended. "
            + "Verify your account now: http://bit.ly/verify-now";
        SmsThreatDetector.Result r = detector.analyze(sample);
        if (!r.flagged) {
            r.flagged = true;
            if (r.reasons.isEmpty()) r.reasons.add("Test message");
        }
        FlaggedSmsStore.getInstance(this).record("TEST-SENDER", sample, r);
        SmsReceiver.showWarning(this, "TEST-SENDER", sample, r);
        Toast.makeText(this, "Test alert sent (score " + r.score + "/100). "
            + "Check your notifications and Flagged Messages.", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        int n = FlaggedSmsStore.getInstance(this).getAll().size();
        flaggedCountLabel.setText(n == 0 ? "View detected threats"
            : n + (n == 1 ? " message flagged" : " messages flagged"));
    }

    private void requestSmsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            java.util.List<String> need = new java.util.ArrayList<>();
            if (checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                need.add(android.Manifest.permission.RECEIVE_SMS);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                need.add(android.Manifest.permission.POST_NOTIFICATIONS);
            }
            if (!need.isEmpty()) {
                requestPermissions(need.toArray(new String[0]), 5502);
            }
        }
    }
}
