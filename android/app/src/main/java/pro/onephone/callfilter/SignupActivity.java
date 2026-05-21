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
            android.R.layout.simple_spinner_item, CountryData.LIST);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        countrySpinner.setAdapter(adapter);
        countrySpinner.setSelection(CountryData.findIndexByIso("IN"));

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
        if (name.length() < 2) {
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
