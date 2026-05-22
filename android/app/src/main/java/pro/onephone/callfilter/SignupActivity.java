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
                // Existing account — name not needed
                TextView title = findViewById(R.id.signupTitle);
                if (title != null) title.setText("Welcome back");
                if (nameInput != null) {
                    nameInput.setHint("Name (optional, leave blank to keep current)");
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
}
