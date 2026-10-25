package com.rekixo.ludopaws.feature.store.data.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.rekixo.ludopaws.feature.store.domain.StoreCatalog
import com.rekixo.ludopaws.feature.store.domain.model.StoreProductKind
import com.rekixo.ludopaws.feature.store.domain.model.StoreBillingProduct
import java.util.concurrent.ConcurrentHashMap

class GooglePlayBillingGateway(
    private val activity: Activity,
    private val enabled: Boolean,
    accountId: String,
    private val listener: Listener,
) : PurchasesUpdatedListener {
    @Volatile
    private var currentBillingAccountId: String = accountId

    private val productDetails =
        linkedMapOf<
            String,
            ProductDetails,
            >()

    @Volatile
    private var closed =
        false
    private val processingPurchaseTokens =
        ConcurrentHashMap.newKeySet<String>()

    private val billingClient =
        BillingClient
            .newBuilder(
                activity.applicationContext,
            )
            .setListener(
                this,
            )
            .enablePendingPurchases(
                PendingPurchasesParams
                    .newBuilder()
                    .enableOneTimeProducts()
                    .build(),
            )
            .enableAutoServiceReconnection()
            .build()

    /**
     * Refreshes purchase attribution when the active Ludo Paws account changes.
     * The caller must pass the account binding returned by the authenticated backend.
     */
    fun updateAccountId(newAccountId: String) {
        require(OBFUSCATED_ACCOUNT_ID_REGEX.matches(newAccountId)) {
            "Google Play account binding is invalid."
        }
        currentBillingAccountId = newAccountId
    }

    fun connect() {
        if (
            closed
        ) {
            return
        }

        if (!enabled) {
            listener
                .onBillingUnavailable(
                    "Google Play purchases are not enabled for this build yet.",
                )
            return
        }

        if (
            billingClient
                .isReady
        ) {
            queryProducts()
            queryExistingPurchases()
            return
        }

        billingClient
            .startConnection(
                object :
                    BillingClientStateListener {
                    override fun onBillingSetupFinished(
                        billingResult:
                            BillingResult,
                    ) {
                        if (
                            closed
                        ) {
                            return
                        }

                        if (
                            billingResult.responseCode ==
                            BillingClient
                                .BillingResponseCode
                                .OK
                        ) {
                            queryProducts()
                            queryExistingPurchases()
                        } else {
                            listener
                                .onBillingUnavailable(
                                    billingResult
                                        .debugMessage
                                        .ifBlank {
                                            "Google Play Billing is unavailable."
                                        },
                                )
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        if (
                            closed
                        ) {
                            return
                        }
                        listener
                            .onBillingUnavailable(
                                "Google Play Billing connection was interrupted.",
                            )
                    }
                },
            )
    }

    fun launchPurchase(
        productId: String,
    ) {
        if (
            closed
        ) {
            return
        }

        if (!enabled) {
            listener
                .onBillingUnavailable(
                    "Purchases are disabled until Play Console products and secure verification are configured.",
                )
            return
        }

        if (!OBFUSCATED_ACCOUNT_ID_REGEX.matches(currentBillingAccountId)) {
            listener.onBillingUnavailable("Reconnect to your game account before purchasing.")
            return
        }

        val details =
            productDetails[
                productId
            ]
        if (details == null) {
            listener
                .onBillingUnavailable(
                    "This product is not available from Google Play yet.",
                )
            queryProducts()
            return
        }

        val listedOffer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
        if (listedOffer != null && listedOffer.offerToken.isNullOrBlank()) {
            listener.onBillingUnavailable("Google Play returned an incomplete offer for this product.")
            return
        }

        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        listedOffer?.offerToken?.takeIf(String::isNotBlank)?.let(productParamsBuilder::setOfferToken)
        val productParams = productParamsBuilder.build()

        val result =
            billingClient
                .launchBillingFlow(
                    activity,
                    BillingFlowParams
                        .newBuilder()
                        .setObfuscatedAccountId(currentBillingAccountId)
                        .setProductDetailsParamsList(
                            listOf(
                                productParams,
                            ),
                        )
                        .build(),
                )

        if (
            result.responseCode !=
            BillingClient
                .BillingResponseCode
                .OK
        ) {
            listener
                .onBillingUnavailable(
                    result.debugMessage
                        .ifBlank {
                            "Google Play purchase screen could not be opened."
                        },
                )
        }
    }

    fun close() {
        if (
            closed
        ) {
            return
        }
        closed =
            true
        productDetails.clear()
        runCatching {
            billingClient
                .endConnection()
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?,
    ) {
        if (
            closed
        ) {
            return
        }

        when (
            billingResult.responseCode
        ) {
            BillingClient
                .BillingResponseCode
                .OK -> {
                processPurchases(purchases.orEmpty())
            }
            BillingClient
                .BillingResponseCode
                .USER_CANCELED ->
                Unit

            else ->
                listener
                    .onBillingUnavailable(
                        billingResult
                            .debugMessage
                            .ifBlank {
                                "Google Play purchase failed."
                            },
                    )
        }
    }

    private fun queryExistingPurchases() {
        if (closed || !billingClient.isReady) return
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        ) { billingResult, purchases ->
            if (closed) return@queryPurchasesAsync
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                listener.onBillingUnavailable(
                    billingResult.debugMessage.ifBlank {
                        "Google Play could not reload previous purchases."
                    },
                )
                return@queryPurchasesAsync
            }
            processPurchases(purchases)
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        purchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    val productId = purchase.products.singleOrNull()
                    if (purchase.products.size != 1 || productId == null || StoreCatalog.find(productId) == null) {
                        listener.onBillingUnavailable(
                            "A Google Play purchase has an unsupported product shape; no entitlement was granted.",
                        )
                        return@forEach
                    }
                    val purchaseAccountId = purchase.accountIdentifiers?.obfuscatedAccountId
                    val restorableNonConsumable =
                        productId.let(StoreCatalog::find)?.kind ==
                            StoreProductKind.NON_CONSUMABLE_REMOVE_ADS
                    val validPurchaseAccountId = purchaseAccountId?.takeIf(OBFUSCATED_ACCOUNT_ID_REGEX::matches)
                    if (purchaseAccountId == null && restorableNonConsumable) {
                        listener.onBillingUnavailable(
                            "This Remove Ads purchase has no recoverable Ludo Paws account binding. Restore the account that made the purchase; no entitlement was granted.",
                        )
                        return@forEach
                    }
                    if (purchaseAccountId != currentBillingAccountId && !restorableNonConsumable) {
                        listener.onBillingUnavailable(
                            "A Play purchase could not be matched to this app account; no content was granted.",
                        )
                        return@forEach
                    }
                    if (validPurchaseAccountId == null || !processingPurchaseTokens.add(purchase.purchaseToken)) {
                        if (validPurchaseAccountId == null) {
                            listener.onBillingUnavailable(
                                "This Play purchase has an invalid Ludo Paws account binding. No content was granted.",
                            )
                        }
                        return@forEach
                    }
                    val finish: () -> Unit = {
                        processingPurchaseTokens.remove(purchase.purchaseToken)
                        Unit
                    }
                    listener.onPurchaseRequiresVerification(
                        productId = productId,
                        purchaseToken = purchase.purchaseToken,
                        accountId = validPurchaseAccountId,
                        onFinished = finish,
                    )
                }
                Purchase.PurchaseState.PENDING ->
                    purchase.products.forEach { productId -> listener.onPurchasePending(productId) }
                else -> Unit
            }
        }
    }

    private fun queryProducts() {
        if (closed) return
        productDetails.clear()
        queryProductType(
            productIds = StoreCatalog.playProductIds,
            productType = BillingClient.ProductType.INAPP,
        ) { products ->
            if (!closed) listener.onCatalogLoaded(products)
        }
    }

    private fun queryProductType(
        productIds: List<String>,
        productType: String,
        onComplete: (Map<String, StoreBillingProduct>) -> Unit,
    ) {
        if (closed || productIds.isEmpty()) {
            onComplete(emptyMap())
            return
        }
        val products = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            if (closed) return@queryProductDetailsAsync
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                listener.onBillingUnavailable(
                    billingResult.debugMessage.ifBlank { "Google Play catalog could not be loaded." },
                )
                onComplete(emptyMap())
                return@queryProductDetailsAsync
            }
            val mapped = linkedMapOf<String, StoreBillingProduct>()
            result.productDetailsList.forEach { details ->
                val formattedPrice =
                    details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
                        ?: return@forEach
                productDetails[details.productId] = details
                mapped[details.productId] = StoreBillingProduct(
                    productId = details.productId,
                    formattedPrice = formattedPrice,
                )
            }
            onComplete(mapped)
        }
    }

    private companion object {
        val OBFUSCATED_ACCOUNT_ID_REGEX = Regex("^[a-f0-9]{64}$")
    }

    interface Listener {
        fun onCatalogLoaded(
            products:
                Map<
                    String,
                    StoreBillingProduct,
                    >,
        )

        fun onPurchaseRequiresVerification(
            productId: String,
            purchaseToken: String,
            accountId: String,
            onFinished: () -> Unit,
        )

        fun onPurchasePending(
            productId: String,
        )

        fun onBillingUnavailable(
            message: String,
        )
    }
}
