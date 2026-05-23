package pro.onephone.callfilter;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class OtpActivity extends AppCompatActivity {

    private static final int RESEND_COUNTDOWN_SEC = 30;

    private EditText otpInput;
    private Button btnVerifyOtp;
    private Button btnResendOtp;
    private TextView numberLabel;
    private TextView linkChangeNumber;
    private android.os.CountDownTimer resendTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

        otpInput         = findViewById(R.id.otpInput);
        btnVerifyOtp     = findViewById(R.id.btnVerifyOtp);
        btnResendOtp     = findViewById(R.id.btnResendOtp);
        numberLabel      = findViewById(R.id.numberLabel);
        linkChangeNumber = findViewById(R.id.linkChangeNumber);

        AuthManager auth = AuthManager.getInstance(this);
        numberLabel.setText("Sent to " + auth.getFullNumber());

        // Dev mode: when admin setting otp_show_in_response=true the backend
        // returns the OTP in the signup response. Show it prominently and
        // auto-fill so the user can just tap Verify.
        String devOtp = getIntent().getStringExtra("dev_otp");
        if (devOtp != null && !devOtp.isEmpty()) {
            numberLabel.setText("DEV MODE \u00B7 OTP: " + devOtp
                + "\nSent to " + auth.getFullNumber());
            otpInput.setText(devOtp);
            otpInput.setSelection(devOtp.length());
            Toast.makeText(this, "Dev OTP: " + devOtp, Toast.LENGTH_LONG).show();
        }

        btnVerifyOtp.setOnClickListener(v -> handleVerify());
        btnResendOtp.setOnClickListener(v -> handleResend());
        linkChangeNumber.setOnClickListener(v -> {
            // Back to LoginActivity in mobile-entry mode
            Intent i = new Intent(OtpActivity.this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });

        startResendCountdown();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) resendTimer.cancel();
    }

    /** Disable the Resend button and tick down 30s before re-enabling. */
    private void startResendCountdown() {
        btnResendOtp.setEnabled(false);
        if (resendTimer != null) resendTimer.cancel();
        resendTimer = new android.os.CountDownTimer(RESEND_COUNTDOWN_SEC * 1000L, 1000L) {
            @Override
            public void onTick(long msUntilFinished) {
                long secs = msUntilFinished / 1000L;
                btnResendOtp.setText("Resend code in " + secs + "s");
            }
            @Override
            public void onFinish() {
                btnResendOtp.setText("Resend OTP");
                btnResendOtp.setEnabled(true);
            }
        };
        resendTimer.start();
    }

    /** Re-trigger OTP send via AuthManager.startSignup (idempotent for existing users). */
    private void handleResend() {
        AuthManager auth = AuthManager.getInstance(this);
        btnResendOtp.setEnabled(false);
        btnResendOtp.setText("Sending…");
        auth.startSignup(
            auth.getDialCode(), auth.getMobile(), "", auth.getName(),
            new AuthManager.SignupCallback() {
                public void onSuccess(String devOtp) {
                    runOnUiThread(() -> {
                        Toast.makeText(OtpActivity.this,
                            "OTP sent again to " + auth.getFullNumber(),
                            Toast.LENGTH_SHORT).show();
                        if (devOtp != null && !devOtp.isEmpty()) {
                            otpInput.setText(devOtp);
                            otpInput.setSelection(devOtp.length());
                            numberLabel.setText("DEV MODE \u00B7 OTP: " + devOtp
                                + "\nSent to " + auth.getFullNumber());
                        }
                        startResendCountdown();
                    });
                }
                public void onError(String message) {
                    runOnUiThread(() -> {
                        Toast.makeText(OtpActivity.this,
                            "Resend failed: " + (message != null ? message : "unknown"),
                            Toast.LENGTH_LONG).show();
                        btnResendOtp.setText("Resend OTP");
                        btnResendOtp.setEnabled(true);
                    });
                }
            }
        );
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
