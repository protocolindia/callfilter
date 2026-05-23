package pro.onephone.callfilter;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
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
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Permissions management screen. Reached from Profile → Permissions row, or
 * via a feature-gate redirect when an action needs a permission the user
 * hasn't granted yet.
 *
 * This is NOT a boot gate (was in v25.17, removed in v25.17.1). Each row has
 * its OWN Grant button so we fire one system prompt at a time, never two
 * stacked windows.
 */
public class PermissionsActivity extends AppCompatActivity {

    private static final int REQ_PHONE   = 9101;
    private static final int REQ_CONTACTS = 9102;
    private static final int REQ_CALL_LOG = 9103;
    private static final int REQ_NOTIFY   = 9104;
    private static final int REQ_OVERLAY  = 9201;
    private static final int REQ_ROLE_SCREENING = 9202;

    private LinearLayout listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);
        listView = findViewById(R.id.permsList);
        // Continue button is now "Done" — returns to wherever the user came from
        Button btnDone = findViewById(R.id.btnPermsContinue);
        btnDone.setText("DONE");
        btnDone.setOnClickListener(v -> finish());
        // Old "open settings" + warning hidden — no longer needed in manage mode
        findViewById(R.id.btnPermsSettings).setVisibility(View.GONE);
        findViewById(R.id.permsDenyHelp).setVisibility(View.GONE);

        renderList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderList();
    }

    /** Rebuild the list of rows showing each permission with its grant state. */
    private void renderList() {
        listView.removeAllViews();

        addRuntimeRow(Manifest.permission.READ_PHONE_STATE, REQ_PHONE,
            "Phone state",
            "Detect when a call rings so it can be evaluated against your rules.");
        addRuntimeRow(Manifest.permission.READ_CONTACTS, REQ_CONTACTS,
            "Contacts",
            "Recognise known callers; required for Contacts-Only mode.");
        addRuntimeRow(Manifest.permission.READ_CALL_LOG, REQ_CALL_LOG,
            "Call log",
            "Show your recent calls and identify the number after a call ends (for the post-call popup).");
        if (Build.VERSION.SDK_INT >= 33) {
            addRuntimeRow(Manifest.permission.POST_NOTIFICATIONS, REQ_NOTIFY,
                "Notifications",
                "Show the post-call \"Block this number?\" alert when the floating overlay can't draw.");
        }
        addOverlayRow();
        addCallScreeningRow();
    }

    private void addRuntimeRow(final String permission, final int requestCode,
                               String title, String reason) {
        boolean granted = ContextCompat.checkSelfPermission(this, permission)
            == PackageManager.PERMISSION_GRANTED;
        addRow(title, reason, granted, v -> {
            if (!granted) {
                ActivityCompat.requestPermissions(this, new String[]{permission}, requestCode);
            }
        });
    }

    private void addOverlayRow() {
        boolean granted = canDrawOverlay(this);
        addRow("Display over other apps",
            "Show the floating \"Block this number?\" popup right after a call ends. Without this, you'll get a notification instead (still works, less prominent).",
            granted,
            v -> {
                if (!granted) openOverlaySettings();
            });
    }

    private void addCallScreeningRow() {
        if (Build.VERSION.SDK_INT < 29) return;
        boolean held = isCallScreeningRoleHeld();
        addRow("Default call screening app",
            "REQUIRED for blocking to actually work. Lets CallFilter intercept incoming calls before they ring.",
            held,
            v -> {
                if (!held) requestCallScreeningRole();
            });
    }

    /** Inflate one permission_row.xml entry. The status TextView doubles as a
     *  "Grant" button when the permission isn't granted yet. */
    private void addRow(String title, String reason, boolean granted, View.OnClickListener onGrant) {
        View row = getLayoutInflater().inflate(R.layout.permission_row, listView, false);
        ((TextView) row.findViewById(R.id.permRowTitle)).setText(title);
        ((TextView) row.findViewById(R.id.permRowDesc)).setText(reason);
        TextView status = row.findViewById(R.id.permRowStatus);
        if (granted) {
            status.setText("✓");
            status.setTextColor(getResources().getColor(R.color.accept, null));
            row.setOnClickListener(null);
        } else {
            status.setText("Grant");
            status.setTextSize(13f);
            status.setTextColor(getResources().getColor(R.color.accent, null));
            row.setOnClickListener(onGrant);
            status.setOnClickListener(onGrant);
        }
        listView.addView(row);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Granted", Toast.LENGTH_SHORT).show();
        }
        renderList();
    }

    // ---- Special permission helpers ----------------------------------------

    public static boolean canDrawOverlay(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        return Settings.canDrawOverlays(ctx);
    }

    private void openOverlaySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open overlay settings", Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean isCallScreeningRoleHeld(Context ctx) {
        if (Build.VERSION.SDK_INT < 29) return false;
        RoleManager rm = (RoleManager) ctx.getSystemService(Context.ROLE_SERVICE);
        return rm != null
            && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
            && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    private boolean isCallScreeningRoleHeld() { return isCallScreeningRoleHeld(this); }

    private void requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT < 29) return;
        RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        if (rm == null) return;
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "Call screening not supported on this device",
                Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent intent = rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
            startActivityForResult(intent, REQ_ROLE_SCREENING);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open role picker: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // For both overlay and role flows, just re-render — onResume also handles
        // this when the user returns, but doing it here is faster.
        renderList();
    }
}
