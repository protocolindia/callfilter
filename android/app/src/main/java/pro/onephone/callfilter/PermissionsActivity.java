package pro.onephone.callfilter;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class PermissionsActivity extends AppCompatActivity {

    private static final int REQ_CORE = 201;
    private static final int REQ_NOTIFY = 202;

    private static final String[] CORE_PERMS = {
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS
    };

    private Button btnContinue;
    private Button btnSkip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);

        btnContinue = findViewById(R.id.btnPermsContinue);
        btnSkip     = findViewById(R.id.btnPermsSkip);

        btnContinue.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { requestNextStep(); }
        });
        btnSkip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finishUp(); }
        });

        if (allGranted()) {
            finishUp();
        }
    }

    private boolean allGranted() {
        for (String p : CORE_PERMS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private void requestNextStep() {
        List<String> needed = new ArrayList<>();
        for (String p : CORE_PERMS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), REQ_CORE);
            return;
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
                return;
            }
        }

        finishUp();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CORE) { requestNextStep(); return; }
        if (requestCode == REQ_NOTIFY) { finishUp(); return; }
    }

    private void finishUp() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    @Override
    public void onBackPressed() {
        // No back-out from this screen
    }
}
