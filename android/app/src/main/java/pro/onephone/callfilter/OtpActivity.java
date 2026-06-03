package pro.onephone.callfilter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OtpActivity extends AppCompatActivity {

    private static final int RESEND_COUNTDOWN_SEC = 30;
    private static final int REQ_USER_CONSENT = 1001;

    private EditText otpInput;
    private Button btnVerifyOtp;
    private Button btnResendOtp;
    private TextView numberLabel;
    private TextView linkChangeNumber;
    private android.os.CountDownTimer resendTimer;
    private SmsConsentReceiver smsReceiver;

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

        String devOtp = getIntent().getStringExtra("dev_otp");
        if (devOtp != null && !devOtp.isEmpty()) {
            numberLabel.setText("DEV MODE \u00B7 OTP: " + devOtp
                + "\nSent to " + auth.getFullNumber());
            otpInput.setText(devOtp);
            otpInput.setSelection(devOtp.length());
            Toast.makeText(this, "Dev OTP: " + devOtp, Toast.LENGTH_LONG).show();
        } else {
            // Production mode: start listening for the incoming OTP SMS.
            startSmsUserConsent();
        }

        btnVerifyOtp.setOnClickListener(v -> handleVerify());
        btnResendOtp.setOnClickListener(v -> handleResend());
        linkChangeNumber.setOnClickListener(v -> {
            Intent i = new Intent(OtpActivity.this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });

        startResendCountdown();
    }

    // ---- SMS User Consent API ----

    /** Begin listening for an incoming verification SMS. When one arrives,
     *  Android shows a one-tap consent popup; on Allow we read the message
     *  and auto-fill the code field. No SMS-read permission required. */
    private void startSmsUserConsent() {
        // null senderPhoneNumber = accept SMS from any sender
        SmsRetriever.getClient(this).startSmsUserConsent(null);
        if (smsReceiver == null) {
            smsReceiver = new SmsConsentReceiver();
            IntentFilter filter = new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION);
            // RECEIVER_EXPORTED required: the SMS Retriever broadcasts from GMS
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(smsReceiver, filter, SmsRetriever.SEND_PERMISSION,
                    null, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(smsReceiver, filter, SmsRetriever.SEND_PERMISSION, null);
            }
        }
    }

    private class SmsConsentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) return;
            Bundle extras = intent.getExtras();
            if (extras == null) return;
            Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
            if (status == null) return;
            if (status.getStatusCode() == CommonStatusCodes.SUCCESS) {
                Intent consentIntent = extras.getParcelable(SmsRetriever.EXTRA_CONSENT_INTENT);
                if (consentIntent != null) {
                    try {
                        startActivityForResult(consentIntent, REQ_USER_CONSENT);
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_USER_CONSENT && resultCode == Activity.RESULT_OK && data != null) {
            String message = data.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE);
            String code = extractCode(message);
            if (code != null) {
                otpInput.setText(code);
                otpInput.setSelection(code.length());
                Toast.makeText(this, "Code filled automatically", Toast.LENGTH_SHORT).show();
                // Auto-verify for a smooth experience
                handleVerify();
            }
        }
    }

    /** Pull the first 4-8 digit run out of the SMS body. */
    private String extractCode(String message) {
        if (message == null) return null;
        Matcher m = Pattern.compile("(\\d{4,8})").matcher(message);
        return m.find() ? m.group(1) : null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) resendTimer.cancel();
        if (smsReceiver != null) {
            try { unregisterReceiver(smsReceiver); } catch (Exception ignored) {}
            smsReceiver = null;
        }
    }

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

    private void handleResend() {
        AuthManager auth = AuthManager.getInstance(this);
        btnResendOtp.setEnabled(false);
        btnResendOtp.setText("Sending\u2026");
        // Restart SMS listening for the new code
        startSmsUserConsent();
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
            Toast.makeText(this, "Enter the verification code", Toast.LENGTH_SHORT).show();
            return;
        }
        btnVerifyOtp.setEnabled(false);
        btnVerifyOtp.setText("\u2026");
        AuthManager.getInstance(this).verifyOtp(code, new AuthManager.OtpVerifyCallback() {
            public void onSuccess(boolean pinAlreadySet, String pinHashFromServer) {
                if (pinAlreadySet) {
                    Toast.makeText(OtpActivity.this,
                        "Welcome back \u2014 sign in with your PIN", Toast.LENGTH_SHORT).show();
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
