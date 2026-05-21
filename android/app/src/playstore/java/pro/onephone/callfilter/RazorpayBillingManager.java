package pro.onephone.callfilter;

import android.app.Activity;
import android.content.Context;

/**
 * STUB for the Google Play Store build.
 *
 * In playstore builds we MUST use Google Play Billing per Google policy.
 * Razorpay calls should never reach this — Factory.get() returns the
 * PlayBillingProviderAdapter instead. This stub exists only so the
 * BillingProvider.Factory class compiles in both flavors with no
 * conditional imports.
 */
public class RazorpayBillingManager implements BillingProvider {

    private static RazorpayBillingManager instance;
    public static synchronized RazorpayBillingManager getInstance(Context ctx) {
        if (instance == null) instance = new RazorpayBillingManager();
        return instance;
    }
    private RazorpayBillingManager() {}

    @Override public boolean isReady() { return false; }
    @Override public String paywallSubtitle() { return "Razorpay is not available in this build"; }

    @Override
    public void subscribe(Activity activity, int planId, BillingCallback cb) {
        cb.onError("Razorpay is not available in the Play Store build. Use Google Play Billing.");
    }

    /** No-op — never called in playstore builds. */
    public void resumeAfterPaymentSuccess(String paymentId, String signature, String orderId) {}
    public void resumeAfterPaymentError(int code, String description) {}
}
