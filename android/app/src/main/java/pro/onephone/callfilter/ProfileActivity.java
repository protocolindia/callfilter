package pro.onephone.callfilter;

import android.Manifest;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.EditText;
import android.widget.Button;

/**
 * Full profile / account screen replacing the old AlertDialog popup.
 * Shows: phone number, subscription status, manage subscription,
 * cloud contact sync toggle, change PIN, logout.
 */
public class ProfileActivity extends AppCompatActivity {

    private TextView statusValue, planPriceValue, mobileLabel;
    private TextView btnManageSub, btnViewPlans;
    private SwitchCompat contactsSyncSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);



        // Auto-Reply SMS Templates manager
        renderSmsTemplates();
        android.widget.Button btnAddTpl = findViewById(R.id.btnAddSmsTemplate);
        if (btnAddTpl != null) {
            btnAddTpl.setOnClickListener(v -> showTemplateEditor(-1, ""));
        }

        // Set version dynamically so it always matches the actual build
        android.widget.TextView tvVer = findViewById(R.id.tvAppVersion);
        if (tvVer != null) {
            try {
                String vn = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
                tvVer.setText("AI CallFilter  ·  v" + vn);
            } catch (Exception ignored) {}
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        mobileLabel        = findViewById(R.id.profileMobileLabel);
        statusValue        = findViewById(R.id.profileSubStatus);
        planPriceValue     = findViewById(R.id.profilePlanPrice);
        btnManageSub       = findViewById(R.id.btnManageSubscription);
        btnViewPlans       = findViewById(R.id.btnViewPlans);
        contactsSyncSwitch = findViewById(R.id.contactsSyncSwitch);

        // ----- Identity -----
        AuthManager auth = AuthManager.getInstance(this);
        TextView nameLabel = findViewById(R.id.profileNameLabel);
        if (!auth.getName().isEmpty()) {
            if (nameLabel != null) {
                nameLabel.setText(auth.getName());
                nameLabel.setVisibility(View.VISIBLE);
            }
            mobileLabel.setText(auth.getFullNumber());
        } else {
            if (nameLabel != null) nameLabel.setVisibility(View.GONE);
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
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finishAffinity();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });

        // ----- Auto-lock toggle -----
        SwitchCompat autoSw = findViewById(R.id.autoLockSwitch);
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
                    // NEW_TASK + CLEAR_TASK wipes the entire back stack so the user
                    // can't swipe-back from Login to Profile after signing out.
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finishAffinity();
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
            // Normalize "null" string that comes from JSON null values
            if (plan == null || "null".equals(plan)) plan = "";
            planPriceValue.setText(plan.isEmpty() ? "" : plan);
            // Manage/Cancel button is intentionally hidden — Razorpay doesn't
            // expose self-service cancel; admin handles that side.
            btnManageSub.setVisibility(View.GONE);
            // When already subscribed, the CTA reads "Extend plan".
            btnViewPlans.setText("⏳ Extend plan");
        } else {
            statusValue.setText("Inactive");
            statusValue.setTextColor(getResources().getColor(R.color.reject, null));
            planPriceValue.setText("Subscribe to continue blocking calls");
            btnManageSub.setVisibility(View.GONE);
            btnViewPlans.setText("🛒 Buy a plan");
        }
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

    private void renderSmsTemplates() {
        android.widget.LinearLayout container = findViewById(R.id.smsTemplatesContainer);
        if (container == null) return;
        container.removeAllViews();
        SmsAutoResponder smsR = SmsAutoResponder.getInstance(this);
        java.util.List<String> templates = smsR.getTemplates();
        float dp = getResources().getDisplayMetrics().density;

        for (int i = 0; i < templates.size(); i++) {
            final int idx = i;
            final String tpl = templates.get(i);

            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFF1E1E26);
            bg.setCornerRadius(8 * dp);
            row.setBackground(bg);
            row.setPadding((int)(12*dp), (int)(10*dp), (int)(8*dp), (int)(10*dp));
            android.widget.LinearLayout.LayoutParams rlp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = (int)(8*dp);
            row.setLayoutParams(rlp);

            android.widget.TextView txt = new android.widget.TextView(this);
            txt.setText(tpl);
            txt.setTextColor(0xFFFFFFFF);
            txt.setTextSize(13f);
            txt.setMaxLines(2);
            txt.setEllipsize(android.text.TextUtils.TruncateAt.END);
            android.widget.LinearLayout.LayoutParams tlp = new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            txt.setLayoutParams(tlp);
            row.addView(txt);

            android.widget.TextView edit = new android.widget.TextView(this);
            edit.setText("✎");
            edit.setTextSize(16f);
            edit.setPadding((int)(10*dp), 0, (int)(10*dp), 0);
            edit.setOnClickListener(v -> showTemplateEditor(idx, tpl));
            row.addView(edit);

            android.widget.TextView del = new android.widget.TextView(this);
            del.setText("✕");
            del.setTextColor(0xFFef4444);
            del.setTextSize(16f);
            del.setPadding((int)(6*dp), 0, (int)(6*dp), 0);
            del.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(ProfileActivity.this)
                    .setTitle("Delete template?")
                    .setMessage(tpl)
                    .setPositiveButton("Delete", (d, w) -> {
                        SmsAutoResponder.getInstance(ProfileActivity.this).removeTemplate(idx);
                        renderSmsTemplates();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
            row.addView(del);

            container.addView(row);
        }
    }

    private void showTemplateEditor(final int index, String current) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(current);
        input.setHint("SMS message to send to blocked callers...");
        input.setMinLines(3);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(index < 0 ? "Add SMS template" : "Edit SMS template")
            .setView(input)
            .setPositiveButton("Save", (d, w) -> {
                String txt = input.getText().toString().trim();
                if (txt.isEmpty()) return;
                SmsAutoResponder smsR = SmsAutoResponder.getInstance(this);
                if (index < 0) smsR.addTemplate(txt);
                else           smsR.updateTemplate(index, txt);
                renderSmsTemplates();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

}
