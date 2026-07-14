package pro.onephone.callfilter;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.razorpay.Checkout;
import com.razorpay.PaymentResultListener;

import org.json.JSONObject;

/**
 * Razorpay one-time payment provider (no auto-renewal).
 *
 * Flow:
 *   1. POST /api/razorpay/create-order  { user_id, plan_id }
 *        -> { order_id, key_id, amount_paise, currency, plan }
 *   2. Open Razorpay Checkout with that order_id + key_id.
 *   3. The hosting Activity implements PaymentResultListener and calls
 *      verifyPayment(...) on success, which POSTs /api/razorpay/verify-payment
 *      { user_id, order_id, payment_id } — the backend extends the plan.
 *
 * The Activity that starts a purchase MUST implement PaymentResultListener
 * (Razorpay delivers the result to the Activity, not to a callback here).
 */
public class RazorpayBillingManager implements BillingProvider {

    private static final String TAG = "RazorpayBilling";
    private static RazorpayBillingManager instance;

    private final Context appCtx;

    // Order context for the in-flight purchase, read back after checkout returns.
    private String pendingOrderId;
    private int pendingPlanId;
    private BillingCallback pendingCb;

    private RazorpayBillingManager(Context ctx) {
        this.appCtx = ctx.getApplicationContext();
    }

    public static synchronized RazorpayBillingManager getInstance(Context ctx) {
        if (instance == null) instance = new RazorpayBillingManager(ctx);
        return instance;
    }

    @Override
    public boolean isReady() {
        // The backend decides (razorpay_enabled + keys). Assume usable when
        // signed in with a backend; create-order will report a clear error if not.
        AuthManager auth = AuthManager.getInstance(appCtx);
        return auth.isBackendEnabled() && !auth.getUserId().isEmpty();
    }

    @Override
    public String paywallSubtitle() {
        return "Pay securely with UPI, card or netbanking";
    }

    public String getPendingOrderId() { return pendingOrderId; }
    public int getPendingPlanId()     { return pendingPlanId; }

    /** Step 1 + 2: create the order on our backend, then open Razorpay Checkout. */
    @Override
    public void subscribe(final Activity activity, final int planId, final BillingCallback cb) {
        final AuthManager auth = AuthManager.getInstance(appCtx);
        if (!auth.isBackendEnabled() || auth.getUserId().isEmpty()) {
            cb.onError("Please sign in to purchase");
            return;
        }
        this.pendingCb = cb;
        this.pendingPlanId = planId;

        try {
            JSONObject body = new JSONObject();
            body.put("user_id", Long.parseLong(auth.getUserId()));
            body.put("plan_id", planId);

            BackendClient.post(AuthManager.BACKEND_URL + "/api/razorpay/create-order", body,
                new BackendClient.Callback() {
                    public void onResult(boolean ok, JSONObject resp, String err) {
                        if (!ok || resp == null) {
                            String msg = friendlyError(resp, err);
                            activity.runOnUiThread(() -> cb.onError(msg));
                            return;
                        }
                        final String orderId = resp.optString("order_id", "");
                        final String keyId   = resp.optString("key_id", "");
                        final int amount     = resp.optInt("amount_paise", 0);
                        final String currency= resp.optString("currency", "INR");
                        if (orderId.isEmpty() || keyId.isEmpty()) {
                            activity.runOnUiThread(() -> cb.onError("Could not start payment"));
                            return;
                        }
                        pendingOrderId = orderId;
                        activity.runOnUiThread(() ->
                            openCheckout(activity, keyId, orderId, amount, currency, auth, cb));
                    }
                });
        } catch (Exception e) {
            cb.onError("Could not start payment");
        }
    }

    private void openCheckout(Activity activity, String keyId, String orderId,
                              int amountPaise, String currency, AuthManager auth,
                              BillingCallback cb) {
        try {
            Checkout checkout = new Checkout();
            checkout.setKeyID(keyId);

            JSONObject opts = new JSONObject();
            opts.put("name", "CyberGuard AI");
            opts.put("description", "Subscription");
            opts.put("order_id", orderId);
            opts.put("currency", currency);
            opts.put("amount", amountPaise);   // paise
            opts.put("retry", new JSONObject().put("enabled", true).put("max_count", 2));

            JSONObject prefill = new JSONObject();
            String mobile = auth.getMobile();
            if (mobile != null && !mobile.isEmpty()) prefill.put("contact", mobile);
            String email = auth.getEmail();
            if (email != null && !email.isEmpty()) prefill.put("email", email);
            opts.put("prefill", prefill);

            checkout.open(activity, opts);
        } catch (Exception e) {
            Log.e(TAG, "checkout open failed", e);
            cb.onError("Could not open payment screen");
        }
    }

    /** Called by the Activity's onPaymentSuccess(). Verifies with the backend. */
    public void resumeAfterPaymentSuccess(String paymentId, String orderId, String signature) {
        final BillingCallback cb = pendingCb;
        if (cb == null) return;
        if (orderId != null && !orderId.isEmpty()) pendingOrderId = orderId;
        if (pendingOrderId == null || pendingOrderId.isEmpty()) {
            cb.onError("Payment reference missing");
            return;
        }
        AuthManager auth = AuthManager.getInstance(appCtx);
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", Long.parseLong(auth.getUserId()));
            body.put("order_id", pendingOrderId);
            body.put("payment_id", paymentId);
            if (signature != null && !signature.isEmpty()) body.put("signature", signature);

            BackendClient.post(AuthManager.BACKEND_URL + "/api/razorpay/verify-payment", body,
                new BackendClient.Callback() {
                    public void onResult(boolean ok, JSONObject resp, String err) {
                        if (ok) {
                            pendingOrderId = null;
                            SubscriptionManager.getInstance(appCtx).refreshAsync();
                            cb.onSuccess("Payment successful");
                        } else {
                            cb.onError(friendlyError(resp, err));
                        }
                        pendingCb = null;
                    }
                });
        } catch (Exception e) {
            cb.onError("Could not verify payment");
            pendingCb = null;
        }
    }

    /** Called by the Activity's onPaymentError(). */
    public void resumeAfterPaymentError(int code, String description) {
        BillingCallback cb = pendingCb;
        pendingCb = null;
        pendingOrderId = null;
        if (cb == null) return;
        // Razorpay uses code 0 / "cancelled" style messages for user cancellation.
        if (description != null && description.toLowerCase().contains("cancel")) {
            cb.onCancelled();
        } else {
            cb.onError(description != null && !description.isEmpty()
                ? description : "Payment failed");
        }
    }

    public BillingCallback getPendingCallback() { return pendingCb; }

    private static String friendlyError(JSONObject resp, String err) {
        String code = resp != null ? resp.optString("error", "") : "";
        if ("razorpay_disabled".equals(code))        return "Payments are currently unavailable";
        if ("razorpay_not_configured".equals(code))  return "Payments are not configured yet";
        if ("plan_not_found".equals(code))           return "This plan is no longer available";
        if ("payment_not_captured".equals(code))     return "Payment was not completed";
        if ("signature_mismatch".equals(code))       return "Payment could not be verified";
        if (code != null && !code.isEmpty())         return code.replace('_', ' ');
        return (err != null && !err.isEmpty()) ? err : "Payment failed";
    }
}
