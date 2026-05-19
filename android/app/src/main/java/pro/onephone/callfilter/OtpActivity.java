package pro.onephone.callfilter;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class OtpActivity extends AppCompatActivity {

    private EditText otpInput;
    private Button btnVerifyOtp;
    private TextView numberLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

        otpInput     = findViewById(R.id.otpInput);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);
        numberLabel  = findViewById(R.id.numberLabel);

        AuthManager auth = AuthManager.getInstance(this);
        numberLabel.setText("Sent to " + auth.getFullNumber());

        // Dev mode: when admin setting otp_show_in_response=true the backend
        // returns the OTP in the signup response. SignupActivity forwards it
        // here via intent extra. Show it prominently and auto-fill so the
        // user can just tap Verify.
        String devOtp = getIntent().getStringExtra("dev_otp");
        if (devOtp != null && !devOtp.isEmpty()) {
            numberLabel.setText("DEV MODE \u00B7 OTP: " + devOtp
                + "\nSent to " + auth.getFullNumber());
            otpInput.setText(devOtp);
            otpInput.setSelection(devOtp.length());
            Toast.makeText(this, "Dev OTP: " + devOtp, Toast.LENGTH_LONG).show();
        }

        btnVerifyOtp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { handleVerify(); }
        });
    }

    private void handleVerify() {
        final String code = otpInput.getText().toString().trim();
        if (code.length() < 4) {
            Toast.makeText(this, "Enter the 4-digit code", Toast.LENGTH_SHORT).show();
            return;
        }
        btnVerifyOtp.setEnabled(false);
        btnVerifyOtp.setText("…");
        AuthManager.getInstance(this).verifyOtp(code, new AuthManager.OtpVerifyCallback() {
            public void onSuccess(boolean pinAlreadySet, String pinHashFromServer) {
                if (pinAlreadySet) {
                    Toast.makeText(OtpActivity.this,
                        "Welcome back — sign in with your PIN", Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(OtpActivity.this, LoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } else {
                    startActivity(new Intent(OtpActivity.this, SetPinActivity.class));
                }
                finish();
            }
            public void onError(String message) {
                btnVerifyOtp.setEnabled(true);
                btnVerifyOtp.setText("Verify");
                Toast.makeText(OtpActivity.this,
                    message != null ? message : "Incorrect OTP", Toast.LENGTH_SHORT).show();
                otpInput.setText("");
            }
        });
    }
}
