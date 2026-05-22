package pro.onephone.callfilter;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Full profile / account screen replacing the old AlertDialog popup.
 * Shows: phone number, subscription status, manage subscription,
 * cloud contact sync toggle, change PIN, logout.
 */
public class ProfileActivity extends AppCompatActivity {

    private TextView statusValue, planPriceValue, mobileLabel;
    private TextView btnManageSub, btnViewPlans;
    private Switch contactsSyncSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        mobileLabel        = findViewById(R.id.profileMobileLabel);
        statusValue        = findViewById(R.id.profileSubStatus);
        planPriceValue     = findViewById(R.id.profilePlanPrice);
        btnManageSub       = findViewById(R.id.btnManageSubscription);
        btnViewPlans       = findViewById(R.id.btnViewPlans);
        contactsSyncSwitch = findViewById(R.id.contactsSyncSwitch);

        // ----- Identity -----
        AuthManager auth = AuthManager.getInstance(this);
        if (!auth.getName().isEmpty()) {
            mobileLabel.setText(auth.getName() + "\n" + auth.getFullNumber());
        } else {
            mobileLabel.setText(auth.getFullNumber());
        }

        // ----- Subscription card -----
        refreshSubscription();
        // Background refresh
        SubscriptionManager.getInstance(this).refreshAsync();

        btnManageSub.setOnClickListener(v -> openPlayStoreSubscriptions());
        btnViewPlans.setOnClickListener(v ->
            startActivity(new Intent(ProfileActivity.this, PaywallActivity.class)));

        // ----- Contact sync toggle -----
        SyncManager sm = SyncManager.getInstance(this);
        contactsSyncSwitch.setOnCheckedChangeListener(null);
        contactsSyncSwitch.setChecked(sm.isContactsOptedIn());
        contactsSyncSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                        != PackageManager.PERMISSION_GRANTED) {
                    contactsSyncSwitch.setChecked(false);
                    requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 105);
                    return;
                }
                showOptInConfirm();
            } else {
                showOptOutConfirm();
            }
        });

        // ----- Change PIN -----
        findViewById(R.id.rowChangePin).setOnClickListener(v ->
            startActivity(new Intent(ProfileActivity.this, ChangePinActivity.class)));

        // ----- Lock app — locks session, keeps PIN -----
        findViewById(R.id.rowLock).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Lock app?")
                .setMessage("You'll need to enter your PIN to unlock. Your mobile, "
                    + "rules and settings stay intact.")
                .setPositiveButton("Lock", (d, w) -> {
                    AuthManager.getInstance(ProfileActivity.this).lock();
                    Intent i = new Intent(ProfileActivity.this, LoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        // ----- Auto-lock toggle -----
        android.widget.Switch autoSw = findViewById(R.id.autoLockSwitch);
        android.widget.TextView autoSum = findViewById(R.id.autoLockSummary);
        android.content.SharedPreferences uiPrefs =
            getSharedPreferences("ui_prefs", MODE_PRIVATE);
        boolean autoOn = uiPrefs.getBoolean("auto_lock", false);
        autoSw.setChecked(autoOn);
        autoSum.setText(autoOn ? "Locks after 5 minutes in background" : "Off");
        autoSw.setOnCheckedChangeListener((b, checked) -> {
            uiPrefs.edit().putBoolean("auto_lock", checked).commit();
            autoSum.setText(checked ? "Locks after 5 minutes in background" : "Off");
        });

        // ----- Sign out (full logout) — wipes everything -----
        findViewById(R.id.rowLogout).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Sign out completely?")
                .setMessage("This SIGNS YOU OUT and clears your local data on this "
                    + "device. You'll need to enter your mobile and verify via OTP "
                    + "to sign in again. Your cloud data is preserved.")
                .setPositiveButton("Sign out", (d, w) -> {
                    AuthManager.getInstance(ProfileActivity.this).logout();
                    Intent i = new Intent(ProfileActivity.this, LoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        SubscriptionManager.getInstance(this).refreshAsync();
        refreshSubscription();
    }

    private void refreshSubscription() {
        SubscriptionManager sub = SubscriptionManager.getInstance(this);
        if (!sub.hasBeenChecked()) {
            statusValue.setText("Checking…");
            statusValue.setTextColor(getResources().getColor(R.color.subtext, null));
            planPriceValue.setText("");
            return;
        }
        if (sub.isActive()) {
            statusValue.setText(sub.getStatusLabel());
            statusValue.setTextColor(getResources().getColor(R.color.accept, null));
            String plan = sub.getPlanName();
            planPriceValue.setText(plan.isEmpty() ? "" : plan);
            btnManageSub.setVisibility(View.VISIBLE);
        } else {
            statusValue.setText("Inactive");
            statusValue.setTextColor(getResources().getColor(R.color.reject, null));
            planPriceValue.setText("Subscribe to continue blocking calls");
            btnManageSub.setVisibility(View.GONE);
        }
        // "Buy a plan" is ALWAYS visible — even with an active sub the user
        // can upgrade to a longer plan or buy an add-on.
        btnViewPlans.setVisibility(View.VISIBLE);
    }

    private void openPlayStoreSubscriptions() {
        try {
            // Direct link to this app's subscription management
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://play.google.com/store/account/subscriptions?package="
                    + getPackageName()
                    + "&sku=" + PlayBillingManager.PRODUCT_MONTHLY));
            startActivity(i);
        } catch (Exception e) {
            // Fallback to generic subscriptions page
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://play.google.com/store/account/subscriptions")));
            } catch (Exception ignored) {
                Toast.makeText(this, "Google Play not available", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showOptInConfirm() {
        new AlertDialog.Builder(this)
            .setTitle("Sync contacts to cloud?")
            .setMessage(
                "This uploads contact names and numbers to our secure servers "
                + "so they're available if you switch devices.\n\n"
                + "Off by default. You can turn it off any time and we'll delete "
                + "your uploaded contacts.")
            .setPositiveButton("Turn on", (d, w) -> {
                SyncManager.getInstance(this).setContactsOptedIn(true);
                SyncManager.getInstance(this).syncContactsAsync();
                Toast.makeText(this, "\u2713 Contact sync enabled", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", (d, w) -> contactsSyncSwitch.setChecked(false))
            .setOnCancelListener(d -> contactsSyncSwitch.setChecked(false))
            .show();
    }

    private void showOptOutConfirm() {
        new AlertDialog.Builder(this)
            .setTitle("Turn off cloud contact sync?")
            .setMessage("Previously uploaded contacts will be deleted from our servers.")
            .setPositiveButton("Turn off", (d, w) -> {
                SyncManager.getInstance(this).setContactsOptedIn(false);
                Toast.makeText(this, "Contact sync turned off", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", (d, w) -> contactsSyncSwitch.setChecked(true))
            .setOnCancelListener(d -> contactsSyncSwitch.setChecked(true))
            .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 105) {
            boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                showOptInConfirm();
            } else {
                Toast.makeText(this, "Contacts permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }
}
