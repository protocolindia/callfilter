package pro.onephone.callfilter;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import com.razorpay.Checkout;
import com.razorpay.PaymentResultWithDataListener;

/**
 * Razorpay-backed billing provider. Flow:
 *   1. POST /api/razorpay/create-order with user_id + plan_id → get order_id + key_id
 *   2. Open Razorpay Checkout sheet (UPI/card/wallet picker) with that order
 *   3. On payment success, POST /api/razorpay/verify-payment to confirm signature
 *   4. Backend creates the subscription row
 *
 * Activity must implement PaymentResultWithDataListener to receive callback,
 * OR we set our own listener via Checkout.setKeyID + razorpay.open(activity, payload).
 * The Razorpay SDK delivers the result back to the Activity's
 * onPaymentSuccess / onPaymentError via reflection.
 */
public class RazorpayBillingManager implements BillingProvider {

    private static final String TAG = "RazorpayBilling";

    private static RazorpayBillingManager instance;
    public static synchronized RazorpayBillingManager getInstance(Context ctx) {
        if (instance == null) instance = new RazorpayBillingManager(ctx.getApplicationContext());
        return instance;
    }

    private final Context appCtx;
    // We hold a pending callback while the Razorpay sheet is open; the host
    // Activity invokes resumeAfterPayment(...) when it receives the result.
    private BillingCallback pending;
    private String pendingOrderId;
    private int pendingPlanId;

    private RazorpayBillingManager(Context ctx) { this.appCtx = ctx; }

    @Override
    public boolean isReady() {
        // We can't know remotely without an API call. Assume true; the user will
        // see a meaningful error if backend says razorpay_disabled.
        return true;
    }

    @Override
    public String paywallSubtitle() {
        return "Pay with UPI, card, or wallet via Razorpay";
    }

    @Override
    public void subscribe(final Activity activity, final int planId, final BillingCallback cb) {
        AuthManager auth = AuthManager.getInstance(activity);
        if (auth.getUserId().isEmpty()) {
            cb.onError("Not logged in");
            return;
        }
        // 1. Create order on backend
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", Long.parseLong(auth.getUserId()));
            body.put("plan_id", planId);
            BackendClient.post(AuthManager.BACKEND_URL + "/api/razorpay/create-order", body,
                new BackendClient.Callback() {
                    public void onResult(boolean ok, JSONObject resp, String err) {
                        if (!ok || resp == null) {
                            cb.onError(err != null ? err : "Failed to create order");
                            return;
                        }
                        String orderId = resp.optString("order_id", null);
                        String keyId   = resp.optString("key_id", null);
                        long amountPaise = resp.optLong("amount_paise", 0L);
                        String currency = resp.optString("currency", "INR");
                        JSONObject plan = resp.optJSONObject("plan");
                        String planName = plan != null ? plan.optString("name", "Subscription") : "Subscription";

                        if (orderId == null || keyId == null) {
                            cb.onError("Razorpay misconfigured");
                            return;
                        }
                        openCheckout(activity, planId, orderId, keyId, amountPaise, currency, planName, cb);
                    }
                });
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
    }

    private void openCheckout(Activity activity, int planId, String orderId, String keyId,
                              long amountPaise, String currency, String planName, BillingCallback cb) {
        pending = cb;
        pendingOrderId = orderId;
        pendingPlanId = planId;

        Checkout co = new Checkout();
        co.setKeyID(keyId);
        try {
            JSONObject opts = new JSONObject();
            opts.put("name", "Call Filter");
            opts.put("description", planName);
            opts.put("order_id", orderId);
            opts.put("currency", currency);
            opts.put("amount", amountPaise);

            AuthManager auth = AuthManager.getInstance(activity);
            JSONObject prefill = new JSONObject();
            prefill.put("contact", auth.getFullNumber());
            if (!auth.getName().isEmpty()) prefill.put("name", auth.getName());
            opts.put("prefill", prefill);

            JSONObject theme = new JSONObject();
            theme.put("color", "#4F8EF7");
            opts.put("theme", theme);

            co.open(activity, opts);
        } catch (Exception e) {
            Log.e(TAG, "Checkout error", e);
            cb.onError("Could not open checkout: " + e.getMessage());
            pending = null;
        }
    }

    /**
     * Called by the host Activity from its onPaymentSuccess(paymentId, paymentData).
     * Verifies the signature with the backend, which then creates the subscription.
     */
    public void resumeAfterPaymentSuccess(final String paymentId, final String signature,
                                          final String orderIdFromCallback) {
        if (pending == null) {
            Log.w(TAG, "Payment success but no pending callback");
            return;
        }
        final BillingCallback cb = pending;
        pending = null;
        final String orderId = orderIdFromCallback != null ? orderIdFromCallback : pendingOrderId;
        AuthManager auth = AuthManager.getInstance(appCtx);
        try {
            JSONObject body = new JSONObject();
            body.put("user_id",   Long.parseLong(auth.getUserId()));
            body.put("order_id",  orderId);
            body.put("payment_id", paymentId);
            body.put("signature", signature);
            BackendClient.post(AuthManager.BACKEND_URL + "/api/razorpay/verify-payment", body,
                new BackendClient.Callback() {
                    public void onResult(boolean ok, JSONObject resp, String err) {
                        if (ok && resp != null && resp.optBoolean("ok", false)) {
                            // Refresh local subscription cache
                            SubscriptionManager.getInstance(appCtx).refreshAsync();
                            cb.onSuccess("Subscription active");
                        } else {
                            cb.onError(err != null ? err : "Verification failed");
                        }
                    }
                });
        } catch (Exception e) {
            cb.onError(e.getMessage());
        }
    }

    /** Called by the host Activity from onPaymentError. */
    public void resumeAfterPaymentError(int code, String description) {
        if (pending == null) return;
        BillingCallback cb = pending;
        pending = null;
        if (code == Checkout.PAYMENT_CANCELED) {
            cb.onCancelled();
        } else {
            cb.onError(description != null ? description : "Payment failed");
        }
    }
}
