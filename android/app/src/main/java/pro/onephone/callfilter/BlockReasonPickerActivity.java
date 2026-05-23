package pro.onephone.callfilter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.Log;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

/**
 * Transparent activity that shows the "Why are you blocking?" follow-up
 * dialog after the user taps BLOCK in the post-call overlay.
 *
 * The overlay itself uses a TYPE_APPLICATION_OVERLAY window which can't
 * easily host a system AlertDialog, so the overlay launches us instead.
 * We finish() once the user picks a reason or taps Skip.
 *
 * Intent extras:
 *   "number" — the phone number being blocked (REQUIRED)
 *   "block_now" — if true, also add the REJECT rule here. If false, the
 *                  caller already added the rule and we only set the reason.
 *
 * Result:
 *   • Always records a block (rule + log entry)
 *   • If user picks a reason, attaches it to the most-recent log entry
 *   • Triggers SyncManager.syncBlockedCallsAsync() so admin sees the
 *     reason within seconds
 */
public class BlockReasonPickerActivity extends AppCompatActivity {

    private static final String TAG = "BlockReasonPicker";
    public static final String EXTRA_NUMBER    = "number";
    public static final String EXTRA_BLOCK_NOW = "block_now";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Transparent theme — set in AndroidManifest

        final String number = getIntent().getStringExtra(EXTRA_NUMBER);
        final boolean blockNow = getIntent().getBooleanExtra(EXTRA_BLOCK_NOW, false);
        Log.d(TAG, "onCreate: number=" + number + " blockNow=" + blockNow);

        if (number == null || number.isEmpty()) {
            finish();
            return;
        }

        // If we still need to add the rule, do it now (before the picker)
        if (blockNow) {
            boolean added = RulesManager.getInstance(this)
                .addRule(number, Rule.TYPE_PREFIX, Rule.ACTION_REJECT);
            if (added) {
                BlockedCallsManager.getInstance(this).recordBlock(
                    number, Rule.TYPE_PREFIX, number, "reject");
                Toast.makeText(this, "✗ Blocked " + number, Toast.LENGTH_SHORT).show();
            }
        }

        final List<String> reasons = BlockReasonsCache.getInstance(this).get();
        final String[] items = reasons.toArray(new String[0]);
        Log.d(TAG, "reasons available: " + items.length);
        final int[] picked = { -1 };

        new AlertDialog.Builder(this)
            .setTitle("Why are you blocking " + number + "?")
            .setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) { picked[0] = which; }
            })
            .setPositiveButton("Save", (d, w) -> {
                if (picked[0] >= 0 && picked[0] < items.length) {
                    String reason = items[picked[0]];
                    boolean ok = BlockedCallsManager.getInstance(this)
                        .setReasonForMostRecent(number, reason);
                    if (ok) {
                        SyncManager.getInstance(this).syncBlockedCallsAsync();
                        Toast.makeText(this, "Reason saved: " + reason, Toast.LENGTH_SHORT).show();
                    }
                }
                finish();
            })
            .setNegativeButton("Skip", (d, w) -> {
                // Still upload the block (without a reason)
                SyncManager.getInstance(this).syncBlockedCallsAsync();
                finish();
            })
            .setOnCancelListener(d -> {
                SyncManager.getInstance(this).syncBlockedCallsAsync();
                finish();
            })
            .show();
        Log.d(TAG, "dialog.show() called");
    }
}
