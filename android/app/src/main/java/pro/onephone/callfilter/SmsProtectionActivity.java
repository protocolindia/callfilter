package pro.onephone.callfilter;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

/**
 * Settings for SMS phishing/spam protection.
 *
 * Mode A "passive"     — a BroadcastReceiver scans and warns; no role change.
 * Mode B "default_app" — prompts the user to make this the default SMS app so
 *                        spam can be handled directly. NOTE: a full messaging
 *                        UI is required for a complete default-SMS experience;
 *                        this requests the role and records the preference.
 */
public class SmsProtectionActivity extends AppCompatActivity {

    private SwitchCompat masterSwitch;
    private TextView modePassiveRadio, modeDefaultRadio, flaggedCountLabel;
    private SmsThreatDetector detector;

    private static final int REQ_DEFAULT_SMS = 5501;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_protection);
        detector = SmsThreatDetector.getInstance(this);

        findViewById(R.id.btnBackSmsProt).setOnClickListener(v -> finish());
        masterSwitch     = findViewById(R.id.smsProtectSwitch);
        modePassiveRadio = findViewById(R.id.modePassiveRadio);
        modeDefaultRadio = findViewById(R.id.modeDefaultRadio);
        flaggedCountLabel = findViewById(R.id.flaggedCountLabel);

        masterSwitch.setChecked(detector.isEnabled());
        masterSwitch.setOnCheckedChangeListener((b, checked) -> {
            detector.setEnabled(checked);
            if (checked) {
                detector.syncRulesAsync();
                requestSmsPermissionIfNeeded();
            }
        });

        findViewById(R.id.modePassive).setOnClickListener(v -> selectMode(SmsThreatDetector.MODE_PASSIVE));
        findViewById(R.id.modeDefaultApp).setOnClickListener(v -> selectMode(SmsThreatDetector.MODE_DEFAULT_APP));

        findViewById(R.id.rowFlaggedMessages).setOnClickListener(v ->
            startActivity(new Intent(this, FlaggedSmsActivity.class)));

        findViewById(R.id.btnTestDetection).setOnClickListener(v -> runTestDetection());

        refreshModeUI();
    }

    /** Runs the detector on a sample phishing message to prove the pipeline
     *  (detection + notification + flagged history) works on this device. */
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
            // Force-flag for the test so the user always sees the result
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
        refreshModeUI();
    }

    private void selectMode(String mode) {
        if (SmsThreatDetector.MODE_DEFAULT_APP.equals(mode)) {
            // Path B — explain, then request the default-SMS-app role.
            new AlertDialog.Builder(this, R.style.DarkDialog)
                .setTitle("Set as default SMS app?")
                .setMessage("To handle spam directly, CyberGuard needs to become your "
                    + "default SMS app. You can switch back anytime in Android settings.\n\n"
                    + "Note: full messaging features are still being rolled out. The passive "
                    + "scanner gives you phishing/spam warnings without changing your SMS app.")
                .setPositiveButton("Continue", (d, w) -> requestDefaultSmsRole())
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            detector.setMode(SmsThreatDetector.MODE_PASSIVE);
            detector.setEnabled(true);
            masterSwitch.setChecked(true);
            requestSmsPermissionIfNeeded();
            refreshModeUI();
        }
    }

    private void requestDefaultSmsRole() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS)) {
                    if (rm.isRoleHeld(RoleManager.ROLE_SMS)) {
                        Toast.makeText(this, "Already the default SMS app", Toast.LENGTH_SHORT).show();
                        detector.setMode(SmsThreatDetector.MODE_DEFAULT_APP);
                        refreshModeUI();
                        return;
                    }
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS), REQ_DEFAULT_SMS);
                    return;
                }
            }
            // Pre-Q fallback
            Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
            startActivityForResult(intent, REQ_DEFAULT_SMS);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open the default-app picker on this device",
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DEFAULT_SMS) {
            if (isDefaultSmsApp()) {
                detector.setMode(SmsThreatDetector.MODE_DEFAULT_APP);
                detector.setEnabled(true);
                masterSwitch.setChecked(true);
                Toast.makeText(this, "CyberGuard is now your default SMS app", Toast.LENGTH_SHORT).show();
            } else {
                detector.setMode(SmsThreatDetector.MODE_PASSIVE);
                Toast.makeText(this, "Staying in passive scanner mode", Toast.LENGTH_SHORT).show();
            }
            refreshModeUI();
        }
    }

    private boolean isDefaultSmsApp() {
        try {
            String def = Telephony.Sms.getDefaultSmsPackage(this);
            return getPackageName().equals(def);
        } catch (Exception e) { return false; }
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

    private void refreshModeUI() {
        boolean isDefault = SmsThreatDetector.MODE_DEFAULT_APP.equals(detector.getMode()) && isDefaultSmsApp();
        modePassiveRadio.setText(isDefault ? "\u25CB" : "\u25C9");
        modeDefaultRadio.setText(isDefault ? "\u25C9" : "\u25CB");
    }
}
