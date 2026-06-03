package pro.onephone.callfilter;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class SignupActivity extends AppCompatActivity {

    private Spinner countrySpinner;
    private EditText mobileInput, nameInput;
    private Button btnContinue;
    private android.widget.CheckBox cbAcceptTerms;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // If already logged in, go straight in
        AuthManager auth = AuthManager.getInstance(this);
        if (auth.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        countrySpinner = findViewById(R.id.countrySpinner);
        mobileInput    = findViewById(R.id.mobileInput);
        nameInput      = findViewById(R.id.nameInput);
        btnContinue    = findViewById(R.id.btnContinue);
        cbAcceptTerms  = findViewById(R.id.cbAcceptTerms);

        // Tapping the label toggles the checkbox (more forgiving touch target)
        android.view.View lblTerms = findViewById(R.id.lblAcceptTerms);
        if (lblTerms != null && cbAcceptTerms != null) {
            lblTerms.setOnClickListener(v -> cbAcceptTerms.setChecked(!cbAcceptTerms.isChecked()));
        }
        // T&C and Privacy Policy links → open in browser
        android.view.View.OnClickListener openTerms = v -> openUrl("https://app.onephone.pro/terms");
        android.view.View.OnClickListener openPriv  = v -> openUrl("https://app.onephone.pro/privacy");
        android.view.View ltLink = findViewById(R.id.linkTerms);
        android.view.View lpLink = findViewById(R.id.linkPrivacy);
        if (ltLink != null) ltLink.setOnClickListener(openTerms);
        if (lpLink != null) lpLink.setOnClickListener(openPriv);

        ArrayAdapter<CountryData> adapter = new ArrayAdapter<>(this,
            R.layout.spinner_item, CountryData.LIST);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        countrySpinner.setAdapter(adapter);
        countrySpinner.setSelection(CountryData.findIndexByIso("IN"));

        // Prefill from LoginActivity if user came from there
        android.content.Intent in = getIntent();
        if (in != null) {
            String pDial   = in.getStringExtra("prefill_dial_code");
            String pMobile = in.getStringExtra("prefill_mobile");
            String pIso    = in.getStringExtra("prefill_country_iso");
            boolean loginMode = in.getBooleanExtra("login_mode", false);
            if (pMobile != null && !pMobile.isEmpty()) {
                mobileInput.setText(pMobile);
            }
            if (pIso != null && !pIso.isEmpty()) {
                int idx = CountryData.findIndexByIso(pIso);
                if (idx >= 0) countrySpinner.setSelection(idx);
            }
            if (loginMode) {
                // Existing account — make this look like "verify your mobile",
                // including hiding the T&C checkbox (they accepted it when signing up)
                if (cbAcceptTerms != null) {
                    android.view.View parent = (android.view.View) cbAcceptTerms.getParent();
                    if (parent != null) parent.setVisibility(android.view.View.GONE);
                }
                // NOT a fresh signup. Hide name section + cross-link.
                TextView title = findViewById(R.id.signupTitle);
                if (title != null) title.setText("Verify your mobile");
                android.view.View nameLabel = findViewById(R.id.nameLabel);
                if (nameLabel != null) nameLabel.setVisibility(android.view.View.GONE);
                if (nameInput != null) nameInput.setVisibility(android.view.View.GONE);
                // Continue button text
                if (btnContinue != null) btnContinue.setText("SEND OTP");
                // Hide the bottom "Already have account? Sign in" link — we're
                // already on the sign-in path, no need to confuse the user.
                android.view.View signinLink2 = findViewById(R.id.linkSignin);
                if (signinLink2 != null) {
                    android.view.View parent = (android.view.View) signinLink2.getParent();
                    if (parent != null) parent.setVisibility(android.view.View.GONE);
                }
            }
        }

        // "Already have an account? Sign in" link
        android.view.View signinLink = findViewById(R.id.linkSignin);
        if (signinLink != null) {
            signinLink.setOnClickListener(v -> {
                android.content.Intent i = new android.content.Intent(SignupActivity.this, LoginActivity.class);
                i.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            });
        }

        GeoIPHelper.detectAsync(new GeoIPHelper.Callback() {
            public void onCountry(String iso) {
                if (iso != null && !iso.isEmpty()) {
                    countrySpinner.setSelection(CountryData.findIndexByIso(iso));
                }
            }
        });

        btnContinue.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { handleContinue(); }
        });
    }

    private void handleContinue() {
        final CountryData cd = (CountryData) countrySpinner.getSelectedItem();
        final String mobile = mobileInput.getText().toString().trim();
        final String name = nameInput.getText().toString().trim();
        boolean isLoginMode = getIntent() != null && getIntent().getBooleanExtra("login_mode", false);

        // T&C required for fresh signups (skipped in login_mode — they already agreed
        // when they originally signed up)
        if (!isLoginMode && (cbAcceptTerms == null || !cbAcceptTerms.isChecked())) {
            Toast.makeText(this,
                "Please accept the Terms & Conditions and Privacy Policy to continue",
                Toast.LENGTH_LONG).show();
            return;
        }
        if (!isLoginMode && name.length() < 2) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
            nameInput.requestFocus();
            return;
        }
        if (mobile.length() < 6) {
            Toast.makeText(this, "Please enter a valid mobile number", Toast.LENGTH_SHORT).show();
            return;
        }
        btnContinue.setEnabled(false);
        btnContinue.setText("…");
        AuthManager.getInstance(this).startSignup(cd.dialCode, mobile, cd.iso, name,
            new AuthManager.SignupCallback() {
                public void onSuccess(String devOtp) {
                    Intent i = new Intent(SignupActivity.this, OtpActivity.class);
                    if (devOtp != null) i.putExtra("dev_otp", devOtp);
                    startActivity(i);
                    finish();
                }
                public void onError(String message) {
                    btnContinue.setEnabled(true);
                    btnContinue.setText("CONTINUE");
                    Toast.makeText(SignupActivity.this,
                        message != null ? message : "Signup failed", Toast.LENGTH_LONG).show();
                }
            });
    }

    private void openUrl(String url) {
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url));
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open " + url, Toast.LENGTH_SHORT).show();
        }
    }
}
