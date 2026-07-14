package pro.onephone.callfilter;

import android.app.Activity;
import android.content.Context;

/**
 * Runtime-selectable billing provider. The actual provider is chosen via
 * BuildConfig.BILLING_PROVIDER which is set per product flavor:
 *   - "play"      → PlayBillingManager (Google Play Billing)
 *   - "razorpay"  → RazorpayBillingManager (sideload, India)
 *
 * PaywallActivity and ProfileActivity should go through this factory and
 * never reference the underlying managers directly.
 */
public interface BillingProvider {

    /** Subscribe to a plan from the backend. Activity is required because
     *  Razorpay needs to open its checkout, and PlayBilling needs an Activity
     *  for the purchase flow. */
    void subscribe(Activity activity, int planId, BillingCallback cb);

    /** True if this provider is fully configured and ready to handle a purchase.
     *  For Razorpay this means the admin has filled in keys + enabled it.
     *  For Play, true if BillingClient is connected and product details are loaded. */
    boolean isReady();

    /** Human-readable description for the paywall (e.g. "Pay with UPI / Card via Razorpay"). */
    String paywallSubtitle();

    interface BillingCallback {
        void onSuccess(String detail);
        void onCancelled();
        void onError(String message);
    }

    // -----------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------
    final class Factory {
        private Factory() {}
        public static BillingProvider get(Context ctx) {
            // Payments go through Razorpay only (one-time payment, no auto-renewal).
            // Google Play Billing is deliberately not used in this product, so we do
            // NOT branch on BuildConfig.BILLING_PROVIDER here — a stale flavor value
            // must never silently fall back to Play Billing ("Product not found").
            return RazorpayBillingManager.getInstance(ctx);
        }
    }
}
