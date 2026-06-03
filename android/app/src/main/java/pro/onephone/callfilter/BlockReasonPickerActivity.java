package pro.onephone.callfilter;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

/**
 * Transparent activity that shows a card-style "Why are you blocking?" picker
 * after the user taps BLOCK in Recent Calls or the post-call overlay.
 * Matches the dark, card-based look used elsewhere in the app.
 */
public class BlockReasonPickerActivity extends AppCompatActivity {

    public static final String EXTRA_NUMBER    = "number";
    public static final String EXTRA_BLOCK_NOW = "block_now";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String number = getIntent().getStringExtra(EXTRA_NUMBER);
        final boolean blockNow = getIntent().getBooleanExtra(EXTRA_BLOCK_NOW, false);

        if (number == null || number.isEmpty()) { finish(); return; }

        // Add the rule + record the block if needed (before showing the picker)
        if (blockNow) {
            boolean added = RulesManager.getInstance(this)
                .addRule(number, Rule.TYPE_PREFIX, Rule.ACTION_REJECT);
            if (added) {
                BlockedCallsManager.getInstance(this).recordBlock(
                    number, Rule.TYPE_PREFIX, number, "reject");
                Toast.makeText(this, "\u2717 Blocked " + number, Toast.LENGTH_SHORT).show();
            }
        }

        final List<String> reasons = BlockReasonsCache.getInstance(this).get();
        float dp = getResources().getDisplayMetrics().density;

        // Build a card-style scrollable list
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * dp);
        col.setPadding(pad, pad, pad, (int)(8 * dp));

        TextView head = new TextView(this);
        head.setText("Why are you blocking?");
        head.setTextColor(0xFFFFFFFF);
        head.setTextSize(19f);
        head.setTypeface(null, Typeface.BOLD);
        col.addView(head);

        TextView sub = new TextView(this);
        sub.setText(number);
        sub.setTextColor(0xFFA1A1AA);
        sub.setTextSize(14f);
        sub.setPadding(0, (int)(2*dp), 0, (int)(16*dp));
        col.addView(sub);

        final AlertDialog[] holder = new AlertDialog[1];

        for (final String reason : reasons) {
            TextView cardView = new TextView(this);
            cardView.setText(reason);
            cardView.setTextColor(0xFFFFFFFF);
            cardView.setTextSize(16f);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF22232A);
            bg.setCornerRadius(10 * dp);
            bg.setStroke((int)(1*dp), 0xFF2D2E36);
            cardView.setBackground(bg);
            cardView.setPadding((int)(16*dp), (int)(15*dp), (int)(16*dp), (int)(15*dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int)(10*dp);
            cardView.setLayoutParams(lp);
            cardView.setClickable(true);
            cardView.setFocusable(true);
            cardView.setOnClickListener(v -> {
                boolean ok = BlockedCallsManager.getInstance(this)
                    .setReasonForMostRecent(number, reason);
                if (ok) {
                    SyncManager.getInstance(this).syncBlockedCallsAsync();
                    Toast.makeText(this, "Reason saved: " + reason, Toast.LENGTH_SHORT).show();
                }
                if (holder[0] != null) holder[0].dismiss();
                finish();
            });
            col.addView(cardView);
        }

        scroll.addView(col);

        AlertDialog dlg = new AlertDialog.Builder(this, R.style.DarkDialog)
            .setView(scroll)
            .setNegativeButton("Skip - don't set reason", (d, w) -> {
                SyncManager.getInstance(this).syncBlockedCallsAsync();
                finish();
            })
            .setOnCancelListener(d -> {
                SyncManager.getInstance(this).syncBlockedCallsAsync();
                finish();
            })
            .create();
        holder[0] = dlg;
        dlg.show();
    }
}
