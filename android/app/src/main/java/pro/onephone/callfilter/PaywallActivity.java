package pro.onephone.callfilter;

import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Subscription paywall. Flavor-agnostic — uses BillingProvider.Factory to pick
 * Play Billing (playstore flavor) or Razorpay (sideload flavor).
 *
 * Plans are fetched from GET /api/plans. The user picks one and the chosen
 * provider's subscribe() flow takes over.
 *
 * For Razorpay: this Activity declares plain onPaymentSuccess/onPaymentError
 * methods. The Razorpay SDK locates and invokes them via reflection — that
 * means we don't need to import any Razorpay classes here, so the Activity
 * compiles cleanly in both flavors.
 */
public class PaywallActivity extends AppCompatActivity {

    private TextView title, subtitle, msgView;
    private LinearLayout planList;
    private Button btnRestore, btnLogout;

    private BillingProvider billing;
    private final List<PlanRow> plans = new ArrayList<>();
    private int selectedPlanId = -1;

    private static class PlanRow {
        int id;
        String name;
        String description;
        double offerPrice;
        double actualPrice;
        String currency;
        int durationDays;
        TextView priceLabel;
        View card;
        boolean isFree;
        boolean alreadyUsed;
        boolean isOneTime;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paywall);

        android.view.View backBtn = findViewById(R.id.btnBack);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());

        title     = findViewById(R.id.paywallTitle);
        subtitle  = findViewById(R.id.paywallSubtitle);
        msgView   = findViewById(R.id.paywallMsg);
        planList  = findViewById(R.id.planList);
        btnRestore = findViewById(R.id.btnRestore);
        btnLogout = findViewById(R.id.btnLogout);

        SubscriptionManager sub = SubscriptionManager.getInstance(this);
        if (sub.getExpiresMs() > 0) {
            title.setText(sub.isTrial() ? "Trial expired" : "Subscription expired");
        }

        billing = BillingProvider.Factory.get(this);
        subtitle.setText(billing.paywallSubtitle());

        // Current-subscription banner
        SubscriptionManager smgr = SubscriptionManager.getInstance(this);
        android.view.View banner = findViewById(R.id.currentSubBanner);
        android.widget.TextView curText = findViewById(R.id.currentSubText);
        if (smgr.isActive()) {
            long remain = smgr.getExpiresMs() - System.currentTimeMillis();
            long days = Math.max(0, remain / (1000L * 60 * 60 * 24));
            String rawName = smgr.getPlanName();
            // optString returns the string "null" when the JSON value was null
            boolean hasName = rawName != null && !rawName.isEmpty() && !"null".equals(rawName);
            String label = hasName ? rawName : (smgr.isTrial() ? "Trial" : "Active");
            curText.setText(label + " — " + days + " day" + (days == 1 ? "" : "s") + " left");
            banner.setVisibility(android.view.View.VISIBLE);
            title.setText("Extend plan");
        }

        btnRestore.setOnClickListener(v -> {
            SubscriptionManager.getInstance(this).refreshAsync();
            Toast.makeText(this, "Checking…", Toast.LENGTH_SHORT).show();
        });
        btnLogout.setOnClickListener(v -> showSignOutConfirm());

        loadPlans();
    }

    private void loadPlans() {
        msgView.setText("Loading plans…");
        String uid = AuthManager.getInstance(PaywallActivity.this).getUserId();
        String url = AuthManager.BACKEND_URL + "/api/plans" + (uid.isEmpty() ? "" : ("?user_id=" + uid));
        BackendClient.get(url, new BackendClient.Callback() {
            public void onResult(boolean ok, JSONObject resp, String err) {
                if (!ok || resp == null) {
                    msgView.setText("Could not load plans: " + (err != null ? err : "no response"));
                    return;
                }
                JSONArray arr = resp.optJSONArray("plans");
                if (arr == null || arr.length() == 0) {
                    msgView.setText("No subscription plans configured. Contact admin.");
                    return;
                }
                plans.clear();
                planList.removeAllViews();
                int hidden = 0;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject p = arr.optJSONObject(i);
                    if (p == null) continue;
                    PlanRow row = new PlanRow();
                    row.id = p.optInt("id", 0);
                    row.name = p.optString("name", "Plan");
                    row.description = p.optString("description", "");
                    row.offerPrice  = p.optDouble("offer_price", 0);
                    row.actualPrice = p.optDouble("actual_price", 0);
                    row.currency = p.optString("currency", "INR");
                    row.durationDays = p.optInt("duration_days", 30);
                    row.isFree      = p.optBoolean("is_free", false);
                    row.alreadyUsed = p.optBoolean("already_used", false);
                    row.isOneTime   = p.optBoolean("is_one_time_per_user", false);
                    // Hide free plans from the "Buy a plan" list entirely
                    if (row.isFree) { hidden++; continue; }
                    plans.add(row);
                    addPlanCard(row);
                }
                if (plans.isEmpty()) {
                    msgView.setText("No paid plans available right now.");
                }
                if (!plans.isEmpty()) selectPlan(plans.get(0).id);
                msgView.setText("");
            }
        });
    }

    private void addPlanCard(final PlanRow row) {
        View card = LayoutInflater.from(this).inflate(R.layout.plan_card, planList, false);
        TextView nameView   = card.findViewById(R.id.planCardName);
        TextView descView   = card.findViewById(R.id.planCardDesc);
        TextView priceView  = card.findViewById(R.id.planCardPrice);
        TextView origView   = card.findViewById(R.id.planCardOriginalPrice);
        Button   subscribe  = card.findViewById(R.id.planCardSubscribe);

        nameView.setText(row.name);
        descView.setText(row.description.isEmpty()
            ? row.durationDays + " days" : row.description);
        priceView.setText(formatMoney(row.offerPrice, row.currency));
        if (row.actualPrice > row.offerPrice) {
            origView.setText(formatMoney(row.actualPrice, row.currency));
            origView.setPaintFlags(origView.getPaintFlags()
                | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            origView.setVisibility(View.VISIBLE);
        } else {
            origView.setVisibility(View.GONE);
        }

        if (row.alreadyUsed) {
            subscribe.setText("ALREADY USED");
            subscribe.setEnabled(false);
            subscribe.setAlpha(0.5f);
            // Also gray out the rest of the card so it's clearly inert
            nameView.setAlpha(0.6f);
            descView.setAlpha(0.6f);
            priceView.setAlpha(0.6f);
        } else {
            // "EXTEND SUBSCRIPTION" reads better than "SUBSCRIBE" when the user
            // already has an active sub (they're adding days, not starting from zero)
            SubscriptionManager smgr = SubscriptionManager.getInstance(this);
            subscribe.setText(smgr.isActive() ? "EXTEND SUBSCRIPTION" : "SUBSCRIBE");
            subscribe.setOnClickListener(v -> {
                selectPlan(row.id);
                startPurchase(row.id);
            });
        }

        row.priceLabel = priceView;
        row.card = card;
        planList.addView(card);
    }

    private void selectPlan(int planId) {
        selectedPlanId = planId;
        // No explicit selection state needed — each card has its own Subscribe button.
    }

    private static String formatMoney(double amount, String currency) {
        // Backend stores prices in the smallest unit (paise / cents); convert to whole units.
        double whole = amount / 100.0;
        String sym = "USD".equalsIgnoreCase(currency) ? "$" :
                    ("INR".equalsIgnoreCase(currency) ? "₹" : (currency + " "));
        // Round-half-up; show fractional only if non-zero
        if (whole == Math.floor(whole)) return sym + String.format("%.0f", whole);
        return sym + String.format("%.2f", whole);
    }

    private void startPurchase(int planId) {
        msgView.setText("Opening checkout…");
        billing.subscribe(this, planId, new BillingProvider.BillingCallback() {
            public void onSuccess(String detail) {
                msgView.setText(detail != null ? detail : "Subscribed");
                SubscriptionManager.getInstance(PaywallActivity.this).refreshAsync();
                // Wait a moment for the subscription to be visible, then return home
                msgView.postDelayed(() -> {
                    Toast.makeText(PaywallActivity.this, "✅ Subscription active", Toast.LENGTH_LONG).show();
                    Intent i = new Intent(PaywallActivity.this, MainActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                }, 800L);
            }
            public void onCancelled() {
                msgView.setText("Payment cancelled");
            }
            public void onError(String message) {
                msgView.setText("Payment error: " + (message != null ? message : "unknown"));
            }
        });
    }

    // ============================================================
    // Razorpay SDK callbacks (invoked via reflection in sideload flavor).
    // These plain methods MUST exist on the Activity so Razorpay can find
    // them. We deliberately don't @Override or import Razorpay types so the
    // same file compiles cleanly in the playstore flavor too.
    // ============================================================
    @SuppressWarnings("unused")
    public void onPaymentSuccess(String razorpayPaymentId) {
        // The order_id was tracked in RazorpayBillingManager; it forwards verification.
        try {
            RazorpayBillingManager.getInstance(this)
                .resumeAfterPaymentSuccess(razorpayPaymentId, null, null);
        } catch (Exception e) {
            msgView.setText("Verification error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    public void onPaymentError(int code, String description) {
        try {
            RazorpayBillingManager.getInstance(this)
                .resumeAfterPaymentError(code, description);
        } catch (Exception ignored) {}
    }

    private void showSignOutConfirm() {
        new AlertDialog.Builder(this)
            .setTitle("Sign out?")
            .setMessage("You'll need to sign in again to use the app.")
            .setPositiveButton("Sign out", (d, w) -> {
                AuthManager.getInstance(this).logout();
                Intent i = new Intent(this, LoginActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    @Override
    public void onBackPressed() {
        // Allow backing out — user can pay later from Profile
        super.onBackPressed();
    }
}
