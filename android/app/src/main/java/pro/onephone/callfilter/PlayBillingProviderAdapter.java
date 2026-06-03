package pro.onephone.callfilter;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import com.android.billingclient.api.ProductDetails;
import java.util.List;

/**
 * Wraps the existing PlayBillingManager in the BillingProvider interface so
 * both flavors can use the same Factory entry point. This file lives in src/main
 * because the Google Play Billing dependency is in main deps (so the class
 * compiles in both flavors). In sideload builds, Factory just won't return
 * this — RazorpayBillingManager is used instead.
 *
 * Note: planId is not used here. Play Billing identifies SKUs by string ID
 * (e.g. "callfilter_monthly"), and that single SKU is the only thing offered
 * via Google Play. The DB plan_id is recorded server-side when we verify
 * the purchase, not chosen client-side.
 */
public class PlayBillingProviderAdapter implements BillingProvider {

    private static final String TAG = "PlayBillingAdapter";

    private static PlayBillingProviderAdapter instance;
    public static synchronized PlayBillingProviderAdapter getInstance(Context ctx) {
        if (instance == null) instance = new PlayBillingProviderAdapter(ctx.getApplicationContext());
        return instance;
    }

    private final PlayBillingManager pbm;
    private boolean ready = false;
    private boolean connecting = false;

    private PlayBillingProviderAdapter(Context ctx) {
        this.pbm = new PlayBillingManager(ctx);
    }

    @Override public boolean isReady() { return ready; }

    @Override public String paywallSubtitle() {
        return "Subscribe with Google Play Billing";
    }

    @Override
    public void subscribe(final Activity activity, final int planId, final BillingCallback cb) {
        if (connecting) {
            cb.onError("Billing is connecting, try again in a moment");
            return;
        }
        if (ready) {
            launch(activity, cb);
            return;
        }
        connecting = true;
        pbm.connect(new PlayBillingManager.ConnectCallback() {
            public void onReady(List<ProductDetails> products) {
                connecting = false;
                ready = true;
                launch(activity, cb);
            }
            public void onError(String message) {
                connecting = false;
                Log.e(TAG, "Play Billing connect failed: " + message);
                cb.onError(message != null ? message : "Play Billing unavailable");
            }
        });
    }

    private void launch(Activity activity, final BillingCallback cb) {
        pbm.launchPurchase(activity, PlayBillingManager.PRODUCT_MONTHLY,
            new PlayBillingManager.PurchaseCallback() {
                public void onPurchased(String productId, String purchaseToken) {
                    // Backend server-side verification (existing /api/billing/google-play/verify)
                    // is invoked elsewhere by PaywallActivity. We just signal success here.
                    cb.onSuccess("Purchase received, verifying with server…");
                }
                public void onUserCancelled() { cb.onCancelled(); }
                public void onError(String message) { cb.onError(message); }
            });
    }

    /** Allow PaywallActivity to disconnect when it finishes (good hygiene). */
    public void disconnect() {
        try { pbm.disconnect(); } catch (Exception ignored) {}
        ready = false;
    }
}
