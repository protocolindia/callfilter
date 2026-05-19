package pro.onephone.callfilter;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlayBillingManager {
    private static final String TAG = "PlayBilling";

    public static final String PRODUCT_MONTHLY = "callfilter_monthly";

    private final Context appCtx;
    private BillingClient billingClient;
    private boolean connected = false;
    private final List<ProductDetails> productCatalog = new ArrayList<>();

    public interface ConnectCallback {
        void onReady(List<ProductDetails> products);
        void onError(String message);
    }

    public interface PurchaseCallback {
        void onPurchased(String productId, String purchaseToken);
        void onUserCancelled();
        void onError(String message);
    }

    private PurchaseCallback pendingPurchaseCb;

    public PlayBillingManager(Context ctx) {
        this.appCtx = ctx.getApplicationContext();
    }

    public void connect(final ConnectCallback cb) {
        billingClient = BillingClient.newBuilder(appCtx)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .setListener(purchasesUpdatedListener)
            .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    connected = true;
                    queryProducts(cb);
                } else {
                    cb.onError("Billing setup failed: " + result.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                connected = false;
            }
        });
    }

    private void queryProducts(final ConnectCallback cb) {
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        products.add(QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_MONTHLY)
            .setProductType(BillingClient.ProductType.SUBS)
            .build());

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
            .setProductList(products).build();

        billingClient.queryProductDetailsAsync(params, new ProductDetailsResponseListener() {
            @Override
            public void onProductDetailsResponse(BillingResult result, List<ProductDetails> productDetailsList) {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    cb.onError("Couldn't fetch products: " + result.getDebugMessage());
                    return;
                }
                productCatalog.clear();
                if (productDetailsList != null) productCatalog.addAll(productDetailsList);
                cb.onReady(productCatalog);
            }
        });
    }

    public void launchPurchase(Activity activity, String productId, PurchaseCallback cb) {
        if (!connected) { cb.onError("Billing not connected"); return; }
        ProductDetails details = null;
        for (ProductDetails p : productCatalog) {
            if (productId.equals(p.getProductId())) { details = p; break; }
        }
        if (details == null) { cb.onError("Product not found"); return; }

        List<ProductDetails.SubscriptionOfferDetails> offers = details.getSubscriptionOfferDetails();
        if (offers == null || offers.isEmpty()) { cb.onError("No offers available"); return; }
        String offerToken = offers.get(0).getOfferToken();

        BillingFlowParams.ProductDetailsParams pdp = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build();

        AuthManager auth = AuthManager.getInstance(appCtx);
        BillingFlowParams flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(Collections.singletonList(pdp))
            .setObfuscatedAccountId(auth.getUserId())
            .build();

        pendingPurchaseCb = cb;
        BillingResult br = billingClient.launchBillingFlow(activity, flow);
        if (br.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            pendingPurchaseCb = null;
            cb.onError("launch failed: " + br.getDebugMessage());
        }
    }

    private final PurchasesUpdatedListener purchasesUpdatedListener = new PurchasesUpdatedListener() {
        @Override
        public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
            if (pendingPurchaseCb == null) return;
            int rc = result.getResponseCode();
            if (rc == BillingClient.BillingResponseCode.USER_CANCELED) {
                pendingPurchaseCb.onUserCancelled();
                pendingPurchaseCb = null;
                return;
            }
            if (rc != BillingClient.BillingResponseCode.OK) {
                pendingPurchaseCb.onError(result.getDebugMessage());
                pendingPurchaseCb = null;
                return;
            }
            if (purchases == null || purchases.isEmpty()) return;
            for (Purchase p : purchases) {
                if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
                List<String> productIds = p.getProducts();
                String productId = productIds.isEmpty() ? "" : productIds.get(0);
                String token = p.getPurchaseToken();
                pendingPurchaseCb.onPurchased(productId, token);

                if (!p.isAcknowledged()) {
                    AcknowledgePurchaseParams ack = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(token).build();
                    billingClient.acknowledgePurchase(ack, new AcknowledgePurchaseResponseListener() {
                        @Override
                        public void onAcknowledgePurchaseResponse(BillingResult r) {
                            Log.d(TAG, "Acked: " + r.getResponseCode());
                        }
                    });
                }
            }
            pendingPurchaseCb = null;
        }
    };

    public void disconnect() {
        if (billingClient != null) billingClient.endConnection();
        connected = false;
    }

    public void sendPurchaseToBackend(String productId, String purchaseToken,
                                      final BackendClient.Callback cb) {
        try {
            AuthManager auth = AuthManager.getInstance(appCtx);
            JSONObject body = new JSONObject();
            body.put("user_id", Long.parseLong(auth.getUserId()));
            body.put("product_id", productId);
            body.put("purchase_token", purchaseToken);
            BackendClient.post(AuthManager.BACKEND_URL + "/api/billing/google-play/verify", body, cb);
        } catch (Exception e) {
            cb.onResult(false, null, e.getMessage());
        }
    }
}
