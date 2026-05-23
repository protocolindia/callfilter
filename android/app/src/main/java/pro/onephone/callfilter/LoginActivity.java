package pro.onephone.callfilter;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

/**
 * Smart login screen (v25.9).
 *
 * Two modes, picked at onCreate:
 *
 *   PIN mode  — we have a mobile + PIN stored locally for this device.
 *               User just taps the PIN to sign in. Same UX as before.
 *
 *   MOBILE mode — no PIN yet (full sign-out or fresh install where the
 *                 user clicked "Already a user"). User enters country
 *                 + mobile, taps CONTINUE. Backend tells us whether the
 *                 account exists:
 *                   - exists  → SignupActivity (which handles OTP + new
 *                              PIN setup; the mobile is pre-filled).
 *                   - doesn't → show toast "No account found", offer
 *                               Sign up link below.
 *
 * Always-visible "Don't have an account? Sign up" link at bottom routes
 * to SignupActivity.
 */
public class LoginActivity extends AppCompatActivity {

    // PIN-mode views
    private EditText pinInput;
    private Button   btnLogin, btnSwitchAccount;
    private TextView numberLabel;

    // Mobile-mode views
    private Spinner  countrySpinner;
    private EditText mobileInput;
    private Button   btnContinue;

    private View mobileSection, pinSection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Common
        numberLabel      = findViewById(R.id.loginNumberLabel);
        mobileSection    = findViewById(R.id.mobileSection);
        pinSection       = findViewById(R.id.pinSection);

        // PIN mode
        pinInput         = findViewById(R.id.loginPinInput);
        btnLogin         = findViewById(R.id.btnLogin);
        btnSwitchAccount = findViewById(R.id.btnSwitchAccount);

        // Mobile mode
        countrySpinner   = findViewById(R.id.loginCountrySpinner);
        mobileInput      = findViewById(R.id.loginMobileInput);
        btnContinue      = findViewById(R.id.btnContinue);

        // Sign-up cross-link (always shown)
        findViewById(R.id.linkSignup).setOnClickListener(v -> goToSignup());

        AuthManager auth = AuthManager.getInstance(this);
        if (auth.hasPin() && !auth.getMobile().isEmpty()) {
            showPinMode(auth);
        } else {
            showMobileMode();
        }
    }

    // ============================================================
    // PIN MODE — we know the user; ask for their PIN.
    // ============================================================
    private void showPinMode(AuthManager auth) {
        mobileSection.setVisibility(View.GONE);
        pinSection.setVisibility(View.VISIBLE);
        numberLabel.setText("Signing in as " + auth.getFullNumber());

        // Background account-still-exists check (also surfaces disabled state)
        auth.verifyAccountStillExists(new AuthManager.AccountCheckCallback() {
            public void onResult(boolean stillExists) {
                if (!stillExists) {
                    runOnUiThread(() -> {
                        Toast.makeText(LoginActivity.this,
                            "Account no longer exists — please sign up again",
                            Toast.LENGTH_LONG).show();
                        goToSignup();
                    });
                }
            }
            @Override public void onAccountDisabled() {
                runOnUiThread(() -> {
                    numberLabel.setText("⚠ Your account is disabled.\n"
                        + "Contact support: support@onephone.pro");
                    numberLabel.setTextColor(getResources().getColor(R.color.reject, null));
                });
            }
        });

        btnLogin.setOnClickListener(v -> handlePinLogin());

        btnSwitchAccount.setOnClickListener(v -> {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Use a different number?")
                .setMessage("This signs you out completely. All local rules and "
                    + "settings on this device will be cleared. Cloud data is safe.")
                .setPositiveButton("Sign out", (d, w) -> {
                    AuthManager.getInstance(this).logout();
                    // Restart in mobile-entry mode
                    Intent i = new Intent(this, LoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    private void handlePinLogin() {
        AuthManager auth = AuthManager.getInstance(this);
        String pin = pinInput.getText().toString();
        if (auth.checkPin(pin)) {
            auth.markLoggedIn();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
            pinInput.setText("");
        }
    }

    // ============================================================
    // MOBILE MODE — no PIN locally; ask for mobile, route accordingly.
    // ============================================================
    private void showMobileMode() {
        pinSection.setVisibility(View.GONE);
        mobileSection.setVisibility(View.VISIBLE);
        numberLabel.setText("Enter your registered mobile number");

        ArrayAdapter<CountryData> adapter = new ArrayAdapter<>(this,
            R.layout.spinner_item, CountryData.LIST);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        countrySpinner.setAdapter(adapter);
        countrySpinner.setSelection(CountryData.findIndexByIso("IN"));

        btnContinue.setOnClickListener(v -> handleContinue());
    }

    private void handleContinue() {
        final CountryData cd = (CountryData) countrySpinner.getSelectedItem();
        final String mobile = mobileInput.getText().toString().trim();
        if (mobile.length() < 6) {
            Toast.makeText(this, "Enter a valid mobile number", Toast.LENGTH_SHORT).show();
            return;
        }
        btnContinue.setEnabled(false);
        btnContinue.setText("…");

        // Hit /api/check-account to see if this mobile already exists on backend
        try {
            JSONObject body = new JSONObject();
            body.put("dial_code", cd.dialCode);
            body.put("mobile", mobile);
            BackendClient.post(AuthManager.BACKEND_URL + "/api/check-account", body,
                new BackendClient.Callback() {
                    public void onResult(boolean ok, JSONObject resp, String err) {
                        runOnUiThread(() -> {
                            btnContinue.setEnabled(true);
                            btnContinue.setText("CONTINUE");
                        });
                        if (ok && resp != null && resp.optBoolean("exists", false)) {
                            // Account exists. Skip the intermediate "Verify your mobile"
                            // screen entirely — directly fire /api/signup (which sends OTP
                            // for existing accounts too) and jump to OtpActivity.
                            runOnUiThread(() -> {
                                btnContinue.setEnabled(false);
                                btnContinue.setText("Sending OTP…");
                            });
                            AuthManager.getInstance(LoginActivity.this).startSignup(
                                cd.dialCode, mobile, cd.iso, "",  // name empty for login
                                new AuthManager.SignupCallback() {
                                    public void onSuccess(String devOtp) {
                                        runOnUiThread(() -> {
                                            btnContinue.setEnabled(true);
                                            btnContinue.setText("CONTINUE");
                                            Intent i = new Intent(LoginActivity.this, OtpActivity.class);
                                            if (devOtp != null && !devOtp.isEmpty()) {
                                                i.putExtra("dev_otp", devOtp);
                                            }
                                            i.putExtra("login_mode", true);
                                            startActivity(i);
                                            finish();
                                        });
                                    }
                                    public void onError(String message) {
                                        runOnUiThread(() -> {
                                            btnContinue.setEnabled(true);
                                            btnContinue.setText("CONTINUE");
                                            Toast.makeText(LoginActivity.this,
                                                message != null ? message : "Could not send OTP",
                                                Toast.LENGTH_LONG).show();
                                        });
                                    }
                                });
                        } else if (ok && resp != null) {
                            // No such account
                            runOnUiThread(() -> {
                                new android.app.AlertDialog.Builder(LoginActivity.this)
                                    .setTitle("No account found")
                                    .setMessage("We couldn't find an account with "
                                        + cd.dialCode + mobile + ". Would you like to sign up?")
                                    .setPositiveButton("Sign up", (d, w) -> {
                                        Intent i = new Intent(LoginActivity.this, SignupActivity.class);
                                        i.putExtra("prefill_dial_code", cd.dialCode);
                                        i.putExtra("prefill_mobile", mobile);
                                        i.putExtra("prefill_country_iso", cd.iso);
                                        startActivity(i);
                                        finish();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(LoginActivity.this,
                                "Could not reach server: " + (err != null ? err : "unknown"),
                                Toast.LENGTH_LONG).show());
                        }
                    }
                });
        } catch (Exception e) {
            btnContinue.setEnabled(true);
            btnContinue.setText("CONTINUE");
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void goToSignup() {
        Intent i = new Intent(this, SignupActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
}
