package pro.onephone.callfilter;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class ChangePinActivity extends AppCompatActivity {

    private EditText oldPin, newPin, newPin2;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_pin);

        oldPin  = findViewById(R.id.oldPinInput);
        newPin  = findViewById(R.id.newPinInput);
        newPin2 = findViewById(R.id.newPinInput2);
        btnSave = findViewById(R.id.btnSavePinChange);

        btnSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { handleSave(); }
        });
    }

    private void handleSave() {
        AuthManager auth = AuthManager.getInstance(this);
        String old = oldPin.getText().toString().trim();
        String a = newPin.getText().toString().trim();
        String b = newPin2.getText().toString().trim();
        if (!auth.checkPin(old)) {
            Toast.makeText(this, "Current PIN is incorrect", Toast.LENGTH_SHORT).show();
            return;
        }
        if (a.length() != 4) {
            Toast.makeText(this, "New PIN must be 4 digits", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!a.equals(b)) {
            Toast.makeText(this, "PINs don't match", Toast.LENGTH_SHORT).show();
            return;
        }
        btnSave.setEnabled(false);
        auth.setPin(a, new AuthManager.SimpleCallback() {
            public void onSuccess() {
                Toast.makeText(ChangePinActivity.this, "✅ PIN updated", Toast.LENGTH_SHORT).show();
                finish();
            }
            public void onError(String message) {
                btnSave.setEnabled(true);
                Toast.makeText(ChangePinActivity.this,
                    message != null ? message : "Could not update", Toast.LENGTH_LONG).show();
            }
        });
    }
}
