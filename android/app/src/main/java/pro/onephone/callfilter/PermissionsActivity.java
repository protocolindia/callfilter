package pro.onephone.callfilter;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

/**
 * Consolidated permissions gate. Shows ALL required permissions on a single
 * screen with the reason for each. User taps GRANT ALL → system prompts run
 * in sequence (Android requires this; runtime permission UI is OS-controlled).
 *
 * If the user denies a CRITICAL permission, they cannot proceed past this
 * screen — there is no "Skip" option. A button to open App Settings appears
 * if Android reports "don't ask again" was set.
 *
 * After all permissions are granted, also checks SYSTEM_ALERT_WINDOW (special
 * permission, requires its own settings page) before routing to MainActivity.
 */
public class PermissionsActivity extends AppCompatActivity {

    private static final int REQ_CODE_RUNTIME = 9001;

    /** Permission spec: (manifest constant, label, reason, critical?) */
    private static class Perm {
        final String manifest;
        final String label;
        final String reason;
        final boolean critical;
        Perm(String m, String l, String r, boolean c) {
            manifest = m; label = l; reason = r; critical = c;
        }
    }

    /** Order matters — list these in the order they're prompted. */
    private List<Perm> perms() {
        List<Perm> list = new ArrayList<>();
        list.add(new Perm(Manifest.permission.READ_PHONE_STATE,
            "Phone state",
            "Required. Lets the app detect when a call is ringing so it can decide whether to block it.",
            true));
        list.add(new Perm(Manifest.permission.READ_CONTACTS,
            "Contacts",
            "Required for Contacts-Only Mode and to recognise known callers.",
            true));
        list.add(new Perm(Manifest.permission.SEND_SMS,
            "Send SMS (optional)",
            "Enables auto-reply SMS when Block All Now is active. Only SEND permission is used — your inbox is never read. Enable in Profile settings.",
            false));
        list.add(new Perm(Manifest.permission.READ_CALL_LOG,
            "Call log",
            "Required to show your recent calls and detect the number after a call ends (for the block popup).",
            true));
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(new Perm(Manifest.permission.POST_NOTIFICATIONS,
                "Notifications",
                "Required to show the post-call \"Block this number?\" alert when the overlay can't draw.",
                true));
        }
        return list;
    }

    private LinearLayout listView;
    private Button btnContinue, btnSettings;
    private TextView denyHelp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);
        listView    = findViewById(R.id.permsList);
        btnContinue = findViewById(R.id.btnPermsContinue);
        btnSettings = findViewById(R.id.btnPermsSettings);
        denyHelp    = findViewById(R.id.permsDenyHelp);

        renderList();

        btnContinue.setOnClickListener(v -> {
            String[] missing = collectMissing();
            if (missing.length == 0) {
                proceedOrCheckOverlay();
            } else {
                ActivityCompat.requestPermissions(this, missing, REQ_CODE_RUNTIME);
            }
        });
        btnSettings.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
            startActivity(i);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-evaluate state after user returns from system settings or grant dialogs
        renderList();
        if (allCriticalGranted()) {
            proceedOrCheckOverlay();
        }
    }

    /** Build the visible list with ✓ / ✗ next to each permission. */
    private void renderList() {
        listView.removeAllViews();
        for (Perm p : perms()) {
            View row = getLayoutInflater().inflate(R.layout.permission_row, listView, false);
            TextView title  = row.findViewById(R.id.permRowTitle);
            TextView desc   = row.findViewById(R.id.permRowDesc);
            TextView status = row.findViewById(R.id.permRowStatus);
            boolean granted = isGranted(p.manifest);
            title.setText(p.label + (p.critical ? "" : "  (optional)"));
            desc.setText(p.reason);
            status.setText(granted ? "✓" : "✗");
            status.setTextColor(getResources().getColor(
                granted ? R.color.accept : R.color.reject, null));
            listView.addView(row);
        }
    }

    private boolean isGranted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission)
            == PackageManager.PERMISSION_GRANTED;
    }

    private String[] collectMissing() {
        List<String> missing = new ArrayList<>();
        for (Perm p : perms()) {
            if (!isGranted(p.manifest)) missing.add(p.manifest);
        }
        return missing.toArray(new String[0]);
    }

    private boolean allCriticalGranted() {
        for (Perm p : perms()) {
            if (p.critical && !isGranted(p.manifest)) return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_CODE_RUNTIME) return;
        renderList();

        if (allCriticalGranted()) {
            denyHelp.setVisibility(View.GONE);
            btnSettings.setVisibility(View.GONE);
            proceedOrCheckOverlay();
            return;
        }

        // Some permission still denied — figure out if we can re-prompt or must
        // send the user to system settings
        boolean canRePrompt = false;
        for (int i = 0; i < permissions.length; i++) {
            if (results[i] != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                canRePrompt = true;
                break;
            }
        }
        if (canRePrompt) {
            denyHelp.setText("⚠ CallFilter cannot work without these permissions. Tap GRANT ALL to try again.");
            denyHelp.setVisibility(View.VISIBLE);
            btnSettings.setVisibility(View.GONE);
        } else {
            denyHelp.setText("⚠ You denied one or more permissions permanently. Open App Settings, enable them, then return here.");
            denyHelp.setVisibility(View.VISIBLE);
            btnSettings.setVisibility(View.VISIBLE);
        }
    }

    /** After runtime permissions, also check SYSTEM_ALERT_WINDOW. */
    private void proceedOrCheckOverlay() {
        // Overlay isn't blocking — app still works with notification fallback
        // for the post-call popup. So we don't gate on it; we just hand control
        // to MainActivity which will re-prompt if needed.
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    /** Disable back-button — user MUST grant before exiting this screen. */
    @Override
    public void onBackPressed() {
        if (allCriticalGranted()) {
            super.onBackPressed();
        } else {
            android.widget.Toast.makeText(this,
                "Please grant the required permissions to continue", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
