package pro.onephone.callfilter;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText pinInput;
    private Button btnLogin;
    private TextView numberLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        AuthManager auth = AuthManager.getInstance(this);
        if (!auth.hasPin()) {
            // no PIN locally — back to signup
            startActivity(new Intent(this, SignupActivity.class));
            finish();
            return;
        }

        pinInput    = findViewById(R.id.loginPinInput);
        btnLogin    = findViewById(R.id.btnLogin);
        numberLabel = findViewById(R.id.loginNumberLabel);

        numberLabel.setText("Signing in as " + auth.getFullNumber());

        // Background check that account still exists
        auth.verifyAccountStillExists(new AuthManager.AccountCheckCallback() {
            public void onResult(boolean stillExists) {
                if (!stillExists) {
                    Toast.makeText(LoginActivity.this,
                        "Account no longer exists — please sign up again",
                        Toast.LENGTH_LONG).show();
                    startActivity(new Intent(LoginActivity.this, SignupActivity.class));
                    finish();
                }
            }
        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { handleLogin(); }
        });
    }

    private void handleLogin() {
        String pin = pinInput.getText().toString().trim();
        AuthManager auth = AuthManager.getInstance(this);
        if (pin.length() != 4 || !auth.checkPin(pin)) {
            Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
            pinInput.setText("");
            return;
        }
        auth.markLoggedIn();
        auth.verifyAccountStillExists(new AuthManager.AccountCheckCallback() {
            public void onResult(boolean stillExists) {
                if (!stillExists) {
                    startActivity(new Intent(LoginActivity.this, SignupActivity.class));
                    finish();
                    return;
                }
                Intent i = new Intent(LoginActivity.this, PermissionsActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                // Pull data on reinstall
                SyncManager.getInstance(LoginActivity.this).forcePullRulesFromCloud();
                SyncManager.getInstance(LoginActivity.this).pullBlockedCallsFromCloudIfEmpty();
                ScheduleManager.getInstance(LoginActivity.this).pullFromCloudIfEmpty();
                startActivity(i);
                finish();
            }
        });
    }
}
