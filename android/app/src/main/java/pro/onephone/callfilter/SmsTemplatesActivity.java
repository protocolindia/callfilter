package pro.onephone.callfilter;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

/** Standalone screen to manage auto-reply SMS templates. */
public class SmsTemplatesActivity extends AppCompatActivity {

    @Override
    private androidx.appcompat.widget.SwitchCompat autoReplySwitch;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_templates);

        findViewById(R.id.btnBackTpl).setOnClickListener(v -> finish());

        Button add = findViewById(R.id.btnAddSmsTemplate);
        add.setOnClickListener(v -> showEditor(-1, ""));

        // Auto-reply enable/disable. SEND_SMS is requested only when turning ON.
        autoReplySwitch = findViewById(R.id.autoReplySwitch);
        SmsAutoResponder smsR = SmsAutoResponder.getInstance(this);
        autoReplySwitch.setChecked(smsR.isEnabled());
        autoReplySwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                if (checkSelfPermission(android.Manifest.permission.SEND_SMS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    // Don't enable yet — ask first, enable in the permission result.
                    autoReplySwitch.setChecked(false);
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Allow sending SMS?")
                        .setMessage("Auto-reply needs permission to send SMS. CyberGuard only "
                            + "SENDS replies during Block All Now \u2014 it never reads your inbox.")
                        .setPositiveButton("Allow", (d, w) ->
                            requestPermissions(new String[]{ android.Manifest.permission.SEND_SMS }, 301))
                        .setNegativeButton("Cancel", null)
                        .show();
                } else {
                    smsR.setEnabled(true);
                }
            } else {
                smsR.setEnabled(false);
            }
        });

        render();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 301) {
            boolean granted = grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            SmsAutoResponder.getInstance(this).setEnabled(granted);
            if (autoReplySwitch != null) autoReplySwitch.setChecked(granted);
            if (!granted) {
                Toast.makeText(this, "SMS permission denied \u2014 auto-reply stays off",
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    private void render() {
        LinearLayout container = findViewById(R.id.smsTemplatesContainer);
        container.removeAllViews();
        SmsAutoResponder smsR = SmsAutoResponder.getInstance(this);
        List<String> templates = smsR.getTemplates();
        float dp = getResources().getDisplayMetrics().density;

        for (int i = 0; i < templates.size(); i++) {
            final int idx = i;
            final String tpl = templates.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFF1E1E26);
            bg.setCornerRadius(10 * dp);
            row.setBackground(bg);
            row.setPadding((int)(14*dp), (int)(14*dp), (int)(10*dp), (int)(14*dp));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = (int)(10*dp);
            row.setLayoutParams(rlp);

            TextView txt = new TextView(this);
            txt.setText(tpl);
            txt.setTextColor(0xFFFFFFFF);
            txt.setTextSize(14f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            txt.setLayoutParams(tlp);
            row.addView(txt);

            TextView edit = new TextView(this);
            edit.setText("\u270E");
            edit.setTextSize(18f);
            edit.setTextColor(0xFF4f8ef7);
            edit.setPadding((int)(12*dp), 0, (int)(12*dp), 0);
            edit.setOnClickListener(v -> showEditor(idx, tpl));
            row.addView(edit);

            TextView del = new TextView(this);
            del.setText("\u2715");
            del.setTextSize(18f);
            del.setTextColor(0xFFef4444);
            del.setPadding((int)(8*dp), 0, (int)(8*dp), 0);
            del.setOnClickListener(v ->
                new AlertDialog.Builder(SmsTemplatesActivity.this)
                    .setTitle("Delete template?")
                    .setMessage(tpl)
                    .setPositiveButton("Delete", (d, w) -> {
                        SmsAutoResponder.getInstance(this).removeTemplate(idx);
                        render();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            row.addView(del);

            container.addView(row);
        }
    }

    private void showEditor(final int index, String current) {
        final EditText input = new EditText(this);
        input.setText(current);
        input.setHint("SMS message to send to blocked callers...");
        input.setMinLines(3);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
            .setTitle(index < 0 ? "Add SMS template" : "Edit SMS template")
            .setView(input)
            .setPositiveButton("Save", (d, w) -> {
                String txt = input.getText().toString().trim();
                if (txt.isEmpty()) return;
                SmsAutoResponder smsR = SmsAutoResponder.getInstance(this);
                if (index < 0) smsR.addTemplate(txt);
                else           smsR.updateTemplate(index, txt);
                render();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}
