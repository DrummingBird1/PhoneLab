package com.sensolab.devicemonitor;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Play flavor: optional "buy the developer a coffee" tips through Google Play Billing
 * (Play policy requires Play Billing for in-app tips). Tips unlock nothing — they're
 * consumables, consumed right after purchase. The foss flavor has a no-op TipJar.
 *
 * <p>The product ids must exist as one-time in-app products in Play Console; on a
 * sideloaded build, or before they're configured, the user sees "tips unavailable".
 */
public final class TipJar {

    static final List<String> PRODUCT_IDS =
            Collections.unmodifiableList(Arrays.asList("tip_small", "tip_medium", "tip_large"));

    public static boolean isAvailable() { return true; }

    private final Fragment host;
    private final BillingClient client;
    private boolean dialogRequested;

    public TipJar(Fragment host) {
        this.host = host;
        this.client = BillingClient.newBuilder(host.requireContext().getApplicationContext())
                .setListener(this::onPurchasesUpdated)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
    }

    /** Connects, consumes any tip an earlier session left unconsumed, then shows the prices. */
    public void open() {
        dialogRequested = true;
        if (client.isReady()) {
            queryProducts();
            return;
        }
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(@NonNull BillingResult result) {
                if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    unavailable();
                    return;
                }
                client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                                .setProductType(BillingClient.ProductType.INAPP).build(),
                        (r, purchases) -> { for (Purchase p : purchases) consumeIfPurchased(p, false); });
                queryProducts();
            }
            @Override public void onBillingServiceDisconnected() {
                // The next tap reconnects.
            }
        });
    }

    public void release() {
        client.endConnection();
    }

    private void queryProducts() {
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        for (String id : PRODUCT_IDS) {
            products.add(QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id).setProductType(BillingClient.ProductType.INAPP).build());
        }
        client.queryProductDetailsAsync(
                QueryProductDetailsParams.newBuilder().setProductList(products).build(),
                (result, details) -> {
                    List<ProductDetails> list = details.getProductDetailsList();
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK || list.isEmpty()) {
                        unavailable();
                        return;
                    }
                    List<ProductDetails> sorted = new ArrayList<>(list);
                    sorted.sort((a, b) -> Integer.compare(
                            PRODUCT_IDS.indexOf(a.getProductId()), PRODUCT_IDS.indexOf(b.getProductId())));
                    onMain(() -> showDialog(sorted));
                });
    }

    private void showDialog(List<ProductDetails> products) {
        if (!dialogRequested || !host.isAdded()) return;
        dialogRequested = false;
        CharSequence[] items = new CharSequence[products.size()];
        for (int i = 0; i < items.length; i++) {
            ProductDetails pd = products.get(i);
            String price = price(pd);
            items[i] = pd.getName() + (price.isEmpty() ? "" : " — " + price);
        }
        new MaterialAlertDialogBuilder(host.requireContext())
                .setTitle(R.string.tip_dialog_title)
                .setItems(items, (d, which) -> buy(products.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void buy(ProductDetails product) {
        if (!host.isAdded()) return;
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()))
                .build();
        client.launchBillingFlow(host.requireActivity(), params);
    }

    private static String price(ProductDetails pd) {
        ProductDetails.OneTimePurchaseOfferDetails offer = pd.getOneTimePurchaseOfferDetails();
        return offer == null ? "" : offer.getFormattedPrice();
    }

    private void onPurchasesUpdated(@NonNull BillingResult result, @Nullable List<Purchase> purchases) {
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK || purchases == null) return;
        for (Purchase p : purchases) consumeIfPurchased(p, true);
    }

    private void consumeIfPurchased(Purchase p, boolean thankUser) {
        if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            if (thankUser) snack(R.string.tip_pending);
            return;
        }
        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;
        client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(),
                (r, token) -> {
                    if (thankUser && r.getResponseCode() == BillingClient.BillingResponseCode.OK) snack(R.string.tip_thanks);
                });
    }

    private void unavailable() {
        if (!dialogRequested) return;
        dialogRequested = false;
        snack(R.string.tip_unavailable);
    }

    private void snack(@StringRes int res) {
        onMain(() -> {
            View v = host.getView();
            if (host.isAdded() && v != null) Snackbar.make(v, res, Snackbar.LENGTH_LONG).show();
        });
    }

    private void onMain(Runnable r) {
        if (host.getActivity() != null) host.requireActivity().runOnUiThread(r);
    }
}
