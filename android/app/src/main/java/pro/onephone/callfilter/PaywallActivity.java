package pro.onephone.callfilter;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.android.billingclient.api.ProductDetails;
import org.json.JSONObject;
import java.util.List;

public class PaywallActivity extends AppCompatActivity {

    private TextView title, subtitle, planName, planPrice, msgView;
    private Button btnPay, btnRestore, btnLogout;
    private PlayBillingManager billing;
    private ProductDetails currentProduct;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_paywall);

        title     = findViewById(R.id.paywallTitle);
        subtitle  = findViewById(R.id.paywallSubtitle);
        planName  = findViewById(R.id.planName);
        planPrice = findViewById(R.id.planPrice);
        msgView   = findViewById(R.id.paywallMsg);
        btnPay    = findViewById(R.id.btnPay);
        btnRestore = findViewById(R.id.btnRestore);
        btnLogout = findViewById(R.id.btnLogout);

        SubscriptionManager sub = SubscriptionManager.getInstance(this);
        if (sub.getExpiresMs() > 0) {
            title.setText(sub.isTrial() ? "Trial expired" : "Subscription expired");
            subtitle.setText("Subscribe to continue blocking calls");
        }

        btnPay.setEnabled(false);
        btnPay.setText("Loading…");
        btnPay.setOnClickListener(v -> startPurchase());
        btnRestore.setOnClickListener(v -> {
            SubscriptionManager.getInstance(this).refreshAsync();
            Toast.makeText(this, "Checking…", Toast.LENGTH_SHORT).show();
        });
        btnLogout.setOnClickListener(v -> {
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
        });

        billing = new PlayBillingManager(this);
        billing.connect(new PlayBillingManager.ConnectCallback() {
            public void onReady(List<ProductDetails> products) {
                if (products.isEmpty()) {
                    msgView.setText("No subscription plans configured.");
                    return;
                }
                currentProduct = products.get(0);
                planName.setText(currentProduct.getTitle());
                List<ProductDetails.SubscriptionOfferDetails> offers = currentProduct.getSubscriptionOfferDetails();
                if (offers != null && !offers.isEmpty()) {
                    List<ProductDetails.PricingPhase> phases =
                        offers.get(0).getPricingPhases().getPricingPhaseList();
                    if (!phases.isEmpty()) {
                        planPrice.setText(phases.get(0).getFormattedPrice()
                            + " / " + phases.get(0).getBillingPeriod());
                    }
                }
                btnPay.setEnabled(true);
                btnPay.setText("SUBSCRIBE");
            }
            public void onError(String message) {
                msgView.setText("Could not connect to Google Play: " + message);
                btnPay.setText("Unavailable");
            }
        });
    }

    private void startPurchase() {
        if (currentProduct == null) return;
        btnPay.setEnabled(false);
        btnPay.setText("Opening Google Play…");
        billing.launchPurchase(this, currentProduct.getProductId(),
            new PlayBillingManager.PurchaseCallback() {
                public void onPurchased(final String productId, final String token) {
                    msgView.setText("Verifying purchase…");
                    billing.sendPurchaseToBackend(productId, token, new BackendClient.Callback() {
                        public void onResult(boolean ok, JSONObject resp, String err) {
                            if (ok) {
                                SubscriptionManager.getInstance(PaywallActivity.this).refreshAsync();
                                Toast.makeText(PaywallActivity.this, "✅ Subscribed!", Toast.LENGTH_LONG).show();
                                Intent i = new Intent(PaywallActivity.this, MainActivity.class);
                                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(i);
                                finish();
                            } else {
                                msgView.setText("Verification failed: " + err);
                                btnPay.setEnabled(true);
                                btnPay.setText("Try again");
                            }
                        }
                    });
                }
                public void onUserCancelled() {
                    btnPay.setEnabled(true);
                    btnPay.setText("SUBSCRIBE");
                }
                public void onError(String message) {
                    btnPay.setEnabled(true);
                    btnPay.setText("SUBSCRIBE");
                    msgView.setText("Payment error: " + message);
                }
            });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (billing != null) billing.disconnect();
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "Subscribe or sign out to continue", Toast.LENGTH_SHORT).show();
    }
}
