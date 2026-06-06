package pro.onephone.callfilter;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

/**
 * Shows the locally-stored flagged-SMS history as cards. Read-only list;
 * the messages themselves remain in the user's normal SMS app.
 */
public class FlaggedSmsActivity extends AppCompatActivity {

    private LinearLayout container;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flagged_sms);

        findViewById(R.id.btnBackFlagged).setOnClickListener(v -> finish());
        container = findViewById(R.id.flaggedContainer);
        emptyView = findViewById(R.id.flaggedEmpty);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        container.removeAllViews();
        List<FlaggedSmsStore.Item> items = FlaggedSmsStore.getInstance(this).getAll();

        if (items.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        emptyView.setVisibility(View.GONE);

        float dp = getResources().getDisplayMetrics().density;
        for (FlaggedSmsStore.Item it : items) {
            container.addView(buildCard(it, dp));
        }
    }

    private View buildCard(FlaggedSmsStore.Item it, float dp) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF16203A);
        bg.setCornerRadius(14 * dp);
        bg.setStroke((int)(1*dp), 0xFF2B3F66);
        card.setBackground(bg);
        card.setPadding((int)(14*dp), (int)(12*dp), (int)(14*dp), (int)(12*dp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int)(10*dp);
        card.setLayoutParams(lp);

        // Header row: sender + category badge
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView sender = new TextView(this);
        sender.setText(it.sender == null || it.sender.isEmpty() ? "Unknown sender" : it.sender);
        sender.setTextColor(0xFFFFFFFF);
        sender.setTextSize(15f);
        sender.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sender.setLayoutParams(sp);
        header.addView(sender);

        boolean phishing = "phishing".equals(it.category);
        TextView badge = new TextView(this);
        badge.setText(phishing ? "PHISHING" : it.category == null ? "SPAM" : it.category.toUpperCase());
        badge.setTextColor(phishing ? 0xFFF87171 : 0xFFF59E0B);
        badge.setTextSize(10f);
        badge.setTypeface(null, Typeface.BOLD);
        GradientDrawable bb = new GradientDrawable();
        bb.setColor(phishing ? 0x33F87171 : 0x33F59E0B);
        bb.setCornerRadius(8 * dp);
        badge.setBackground(bb);
        badge.setPadding((int)(8*dp),(int)(3*dp),(int)(8*dp),(int)(3*dp));
        header.addView(badge);
        card.addView(header);

        // Preview
        TextView preview = new TextView(this);
        preview.setText(it.preview);
        preview.setTextColor(0xFFB6BECC);
        preview.setTextSize(13f);
        preview.setPadding(0, (int)(6*dp), 0, 0);
        card.addView(preview);

        // Reasons + score + time
        TextView meta = new TextView(this);
        StringBuilder sb = new StringBuilder();
        if (it.reasons != null && !it.reasons.isEmpty()) sb.append(it.reasons).append(" \u00B7 ");
        sb.append("score ").append(it.score).append("/100 \u00B7 ");
        sb.append(DateUtils.getRelativeTimeSpanString(it.flaggedAtMs));
        meta.setText(sb.toString());
        meta.setTextColor(0xFF6B7280);
        meta.setTextSize(11f);
        meta.setPadding(0, (int)(8*dp), 0, 0);
        card.addView(meta);

        return card;
    }
}
