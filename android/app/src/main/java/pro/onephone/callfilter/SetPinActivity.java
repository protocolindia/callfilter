package pro.onephone.callfilter;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class SetPinActivity extends AppCompatActivity {

    private EditText pin1, pin2;
    private Button btnSavePin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_pin);

        pin1       = findViewById(R.id.pinInput);
        pin2       = findViewById(R.id.pinInput2);
        btnSavePin = findViewById(R.id.btnSavePin);

        btnSavePin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { handleSave(); }
        });
    }

    private void handleSave() {
        String a = pin1.getText().toString().trim();
        String b = pin2.getText().toString().trim();
        if (a.length() != 4) {
            Toast.makeText(this, "PIN must be 4 digits", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!a.equals(b)) {
            Toast.makeText(this, "PINs don't match", Toast.LENGTH_SHORT).show();
            return;
        }
        btnSavePin.setEnabled(false);
        btnSavePin.setText("…");
        AuthManager.getInstance(this).setPin(a, new AuthManager.SimpleCallback() {
            public void onSuccess() {
                Toast.makeText(SetPinActivity.this, "✅ PIN saved!", Toast.LENGTH_SHORT).show();
                goToMain();
            }
            public void onError(String message) {
                btnSavePin.setEnabled(true);
                btnSavePin.setText("Save PIN");
                Toast.makeText(SetPinActivity.this,
                    message != null ? message : "Could not save PIN", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void goToMain() {
        final Intent i = new Intent(this, PermissionsActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        AuthManager.getInstance(this).verifyAccountStillExists(
            new AuthManager.AccountCheckCallback() {
                public void onResult(boolean stillExists) {
                    startActivity(i);
                    finish();
                }
            });
    }
}
