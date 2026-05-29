package pro.onephone.callfilter;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import java.util.ArrayList;

/**
 * Multi-step picker shown when the user taps the 🛑 Block All Now icon:
 *   Step 1 — pick mode: everything / except contacts / except custom
 *   (if custom): ContactPickerActivity is launched
 *   Step 2 — pick duration (presets + Custom + Until I turn it off)
 *   then BlockAllManager.activate(...) is called.
 *
 * Caller is responsible for handling the ContactPickerActivity result and
 * calling resumeWithPickedContacts() with the numbers/names.
 */
public class BlockAllNowDialog {

    public static final int REQ_PICK_CONTACTS_FOR_BLOCK_ALL = 5001;

    private final Activity activity;
    private final Runnable onActivated;
    private String pendingMode;          // mode chosen in step 1, awaiting contact pick
    private long pendingDuration = -1;   // ms duration chosen later

    public BlockAllNowDialog(Activity activity, Runnable onActivated) {
        this.activity = activity;
        this.onActivated = onActivated;
    }

    public void show() {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_block_mode, null);
        final AlertDialog dlg = new AlertDialog.Builder(activity)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .create();

        view.findViewById(R.id.optEverything).setOnClickListener(v -> {
            dlg.dismiss();
            askDuration(BlockAllManager.MODE_EVERYTHING, new ArrayList<>(), new ArrayList<>());
        });
        view.findViewById(R.id.optExceptContacts).setOnClickListener(v -> {
            dlg.dismiss();
            askDuration(BlockAllManager.MODE_EXCEPT_CONTACTS, new ArrayList<>(), new ArrayList<>());
        });
        view.findViewById(R.id.optExceptCustom).setOnClickListener(v -> {
            dlg.dismiss();
            pendingMode = BlockAllManager.MODE_EXCEPT_CUSTOM;
            Intent i = new Intent(activity, ContactPickerActivity.class);
            activity.startActivityForResult(i, REQ_PICK_CONTACTS_FOR_BLOCK_ALL);
        });

        dlg.show();
    }

    /** Called by the host Activity from its onActivityResult for the contact picker. */
    public void resumeWithPickedContacts(ArrayList<String> numbers, ArrayList<String> names) {
        if (pendingMode == null) return;
        askDuration(pendingMode, numbers, names);
    }

    private void askDuration(final String mode,
                             final ArrayList<String> allowNumbers,
                             final ArrayList<String> allowNames) {
        final String[] labels = {
            "15 minutes",
            "30 minutes",
            "1 hour",
            "2 hours",
            "4 hours",
            "Custom…",
            "Until I turn it off"
        };
        final int[] mins = { 15, 30, 60, 120, 240, -1, 0 };
        // -1 = custom, 0 = indefinite

        new AlertDialog.Builder(activity)
            .setTitle("Activate for how long?")
            .setItems(labels, (d, which) -> {
                int chosen = mins[which];
                if (chosen == -1) {
                    askCustomDuration(mode, allowNumbers, allowNames);
                } else {
                    activate(mode, chosen, allowNumbers, allowNames);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void askCustomDuration(final String mode,
                                   final ArrayList<String> allowNumbers,
                                   final ArrayList<String> allowNames) {
        final EditText hours = new EditText(activity);
        hours.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        hours.setHint("0");
        final EditText minutes = new EditText(activity);
        minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minutes.setHint("0");

        android.widget.LinearLayout container = new android.widget.LinearLayout(activity);
        container.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);

        android.widget.LinearLayout.LayoutParams lp =
            new android.widget.LinearLayout.LayoutParams(0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

        android.widget.LinearLayout hCol = new android.widget.LinearLayout(activity);
        hCol.setOrientation(android.widget.LinearLayout.VERTICAL);
        hCol.setLayoutParams(lp);
        android.widget.TextView hLabel = new android.widget.TextView(activity);
        hLabel.setText("Hours");
        hCol.addView(hLabel);
        hCol.addView(hours);

        android.widget.LinearLayout mCol = new android.widget.LinearLayout(activity);
        mCol.setOrientation(android.widget.LinearLayout.VERTICAL);
        mCol.setLayoutParams(lp);
        android.widget.TextView mLabel = new android.widget.TextView(activity);
        mLabel.setText("Minutes");
        mCol.addView(mLabel);
        mCol.addView(minutes);

        container.addView(hCol);
        container.addView(mCol);

        new AlertDialog.Builder(activity)
            .setTitle("Custom duration")
            .setView(container)
            .setPositiveButton("Activate", (d, w) -> {
                int h = parseSafe(hours.getText().toString(), 0);
                int m = parseSafe(minutes.getText().toString(), 0);
                int totalMin = h * 60 + m;
                if (totalMin <= 0) {
                    Toast.makeText(activity, "Enter a duration", Toast.LENGTH_SHORT).show();
                    return;
                }
                activate(mode, totalMin, allowNumbers, allowNames);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void activate(final String mode, final int durationMin,
                          final ArrayList<String> allowNumbers, final ArrayList<String> allowNames) {
        // Step 3 — ask which auto-reply SMS template to use (or none)
        askSmsTemplate(mode, durationMin, allowNumbers, allowNames);
    }

    private void askSmsTemplate(final String mode, final int durationMin,
                                final ArrayList<String> allowNumbers,
                                final ArrayList<String> allowNames) {
        final SmsAutoResponder sms = SmsAutoResponder.getInstance(activity);
        final java.util.List<String> templates = sms.getTemplates();

        // Build options: "No SMS reply" + each template (truncated for display)
        final String[] options = new String[templates.size() + 1];
        options[0] = "✕  No SMS reply";
        for (int i = 0; i < templates.size(); i++) {
            String t = templates.get(i);
            options[i + 1] = "✉  " + (t.length() > 50 ? t.substring(0, 50) + "..." : t);
        }

        new AlertDialog.Builder(activity)
            .setTitle("Auto-reply SMS to blocked callers?")
            .setItems(options, (d, which) -> {
                if (which == 0) {
                    sms.setEnabled(false);   // no SMS for this session
                } else {
                    sms.setMessage(templates.get(which - 1));
                    sms.setEnabled(true);
                    // Ensure SEND_SMS permission
                    if (activity.checkSelfPermission(android.Manifest.permission.SEND_SMS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        activity.requestPermissions(
                            new String[]{ android.Manifest.permission.SEND_SMS }, 201);
                    }
                }
                doActivate(mode, durationMin, allowNumbers, allowNames);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void doActivate(String mode, int durationMin,
                            ArrayList<String> allowNumbers, ArrayList<String> allowNames) {
        long expiresAt = (durationMin == 0)
            ? 0L
            : System.currentTimeMillis() + (long) durationMin * 60_000L;
        BlockAllManager.getInstance(activity).activate(mode, expiresAt, allowNumbers, allowNames);

        String summary;
        if (durationMin == 0) summary = "Block All Now active - until you turn it off";
        else if (durationMin < 60) summary = "Block All Now active for " + durationMin + " min";
        else if (durationMin % 60 == 0) summary = "Block All Now active for " + (durationMin / 60) + "h";
        else summary = "Block All Now active for " + (durationMin / 60) + "h " + (durationMin % 60) + "m";
        Toast.makeText(activity, summary, Toast.LENGTH_LONG).show();

        if (onActivated != null) onActivated.run();
    }

    private static int parseSafe(String s, int dflt) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return dflt; }
    }
}
