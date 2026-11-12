package com.rekixo.ludopaws.platform;

import android.app.Activity;

import com.android.billingclient.api.AccountIdentifiers;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Play Billing 9.1.0 (the live app's version), mirroring live GooglePlayBillingGateway:
 * pending one-time purchases enabled, automatic service reconnection, INAPP products,
 * purchases bound to the game account's obfuscated billing id. The client never consumes or
 * acknowledges: the server verifies the token and consumes / acknowledges it (live).
 * Results go to GDScript as JSON through the plugin's "billing" signal.
 */
final class BillingBridge implements PurchasesUpdatedListener {
    interface Sink { void emit(String kind, String json); }

    private final Activity activity;
    private final Sink sink;
    private final BillingClient client;
    private final Map<String, ProductDetails> details = new HashMap<>();
    private String accountId = "";

    BillingBridge(Activity activity, Sink sink) {
        this.activity = activity;
        this.sink = sink;
        client = BillingClient.newBuilder(activity.getApplicationContext())
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
    }

    void connect() {
        if (client.isReady()) { sink.emit("ready", "{\"ok\":true}"); return; }
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                boolean ok = result.getResponseCode() == BillingClient.BillingResponseCode.OK;
                sink.emit("ready", json("ok", ok, "message", result.getDebugMessage()));
            }
            @Override public void onBillingServiceDisconnected() {
                sink.emit("ready", json("ok", false, "message", "Google Play Billing disconnected."));
            }
        });
    }

    void setAccount(String id) { accountId = id == null ? "" : id; }

    void queryProducts(String idsJson) {
        try {
            JSONArray ids = new JSONArray(idsJson);
            List<QueryProductDetailsParams.Product> products = new ArrayList<>();
            for (int i = 0; i < ids.length(); i++) {
                products.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(ids.getString(i)).setProductType(BillingClient.ProductType.INAPP).build());
            }
            if (products.isEmpty() || !client.isReady()) { sink.emit("products", "{\"ok\":false,\"products\":{}}"); return; }
            client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build(), (result, queried) -> {
                try {
                    JSONObject out = new JSONObject();
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        for (ProductDetails d : queried.getProductDetailsList()) {
                            List<ProductDetails.OneTimePurchaseOfferDetails> offers = d.getOneTimePurchaseOfferDetailsList();
                            if (offers == null || offers.isEmpty()) continue;
                            details.put(d.getProductId(), d);
                            out.put(d.getProductId(), offers.get(0).getFormattedPrice());
                        }
                    }
                    sink.emit("products", new JSONObject().put("ok", result.getResponseCode() == BillingClient.BillingResponseCode.OK)
                        .put("message", result.getDebugMessage()).put("products", out).toString());
                } catch (Exception e) {
                    sink.emit("products", "{\"ok\":false,\"products\":{}}");
                }
            });
        } catch (Exception e) {
            sink.emit("products", "{\"ok\":false,\"products\":{}}");
        }
    }

    void purchase(String productId) {
        ProductDetails d = details.get(productId);
        if (d == null || !client.isReady() || !accountId.matches("^[a-f0-9]{64}$")) {
            sink.emit("error", json("code", d == null ? "PRODUCT_UNAVAILABLE" : "NOT_READY", "message",
                d == null ? "This product is not available from Google Play yet." : "Reconnect to your game account before purchasing."));
            return;
        }
        List<ProductDetails.OneTimePurchaseOfferDetails> offers = d.getOneTimePurchaseOfferDetailsList();
        BillingFlowParams.ProductDetailsParams.Builder product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d);
        if (offers != null && !offers.isEmpty() && offers.get(0).getOfferToken() != null) product.setOfferToken(offers.get(0).getOfferToken());
        List<BillingFlowParams.ProductDetailsParams> list = new ArrayList<>();
        list.add(product.build());
        BillingFlowParams params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(list)
            .setObfuscatedAccountId(accountId)
            .build();
        activity.runOnUiThread(() -> {
            BillingResult r = client.launchBillingFlow(activity, params);
            if (r.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                sink.emit("error", json("code", "ITEM_ALREADY_OWNED", "message", r.getDebugMessage()));
            } else if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                sink.emit("error", json("code", "FAILED", "message", r.getDebugMessage()));
            }
        });
    }

    void queryPurchases() {
        if (!client.isReady()) { sink.emit("owned", "{\"ok\":false,\"purchases\":[]}"); return; }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(), (result, purchases) -> {
            try {
                sink.emit("owned", new JSONObject().put("ok", result.getResponseCode() == BillingClient.BillingResponseCode.OK)
                    .put("message", result.getDebugMessage()).put("purchases", toJson(purchases)).toString());
            } catch (Exception e) {
                sink.emit("owned", "{\"ok\":false,\"purchases\":[]}");
            }
        });
    }

    @Override
    public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        try {
            int code = result.getResponseCode();
            if (code == BillingClient.BillingResponseCode.OK) {
                sink.emit("purchases", new JSONObject().put("purchases", toJson(purchases)).toString());
            } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
                sink.emit("error", json("code", "USER_CANCELED", "message", ""));
            } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                sink.emit("error", json("code", "ITEM_ALREADY_OWNED", "message", result.getDebugMessage()));
            } else {
                sink.emit("error", json("code", "FAILED", "message", result.getDebugMessage()));
            }
        } catch (Exception ignored) {
        }
    }

    private static JSONArray toJson(List<Purchase> purchases) throws Exception {
        JSONArray out = new JSONArray();
        if (purchases == null) return out;
        for (Purchase p : purchases) {
            AccountIdentifiers ids = p.getAccountIdentifiers();
            out.put(new JSONObject()
                .put("products", new JSONArray(p.getProducts()))
                .put("token", p.getPurchaseToken())
                .put("state", p.getPurchaseState() == Purchase.PurchaseState.PURCHASED ? "PURCHASED"
                    : p.getPurchaseState() == Purchase.PurchaseState.PENDING ? "PENDING" : "UNSPECIFIED")
                .put("accountId", ids != null && ids.getObfuscatedAccountId() != null ? ids.getObfuscatedAccountId() : JSONObject.NULL));
        }
        return out;
    }

    private static String json(Object... kv) {
        try {
            JSONObject o = new JSONObject();
            for (int i = 0; i + 1 < kv.length; i += 2) o.put((String) kv[i], kv[i + 1] == null ? "" : kv[i + 1]);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    void close() {
        try { client.endConnection(); } catch (Exception ignored) { }
    }
}
