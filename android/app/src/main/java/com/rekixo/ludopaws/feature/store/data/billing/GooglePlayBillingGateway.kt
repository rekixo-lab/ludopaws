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
import com.rekixo.ludopaws.feature.store.domain.RemoveAdsPlayOwnership
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
    private var connecting = false
    private var removeAdsPreflightInProgress = false
    private var lastLaunchedProductId: String? = null
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
        if (currentBillingAccountId != newAccountId) {
            currentBillingAccountId = newAccountId
            listener.onRemoveAdsOwnershipChanged(RemoveAdsPlayOwnership.UNKNOWN)
        }
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

        if (connecting) return
        connecting = true
        billingClient
            .startConnection(
                object :
                    BillingClientStateListener {
                    override fun onBillingSetupFinished(
                        billingResult:
                            BillingResult,
                    ) {
                        connecting = false
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
                        connecting = false
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

    fun refreshPurchases() {
        if (closed || !enabled) return
        if (billingClient.isReady) queryExistingPurchases() else connect()
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

        // Every Remove Ads checkout needs a FRESH owned-purchases query.
        // The account binding must still match after the async Play callback.
        if (productId == StoreCatalog.REMOVE_ADS) {
            if (removeAdsPreflightInProgress) return
            removeAdsPreflightInProgress = true
            val expected = currentBillingAccountId
            queryExistingPurchases { ownership ->
                activity.runOnUiThread {
                    removeAdsPreflightInProgress = false
                    if (closed) return@runOnUiThread
                    if (currentBillingAccountId != expected) {
                        listener.onBillingUnavailable("The game account changed. Reopen the Store and retry.")
                    } else if (ownership != RemoveAdsPlayOwnership.NOT_OWNED) {
                        listener.onRemoveAdsCheckoutBlocked(ownership)
                    } else openPlayPurchaseSheet(productId, details)
                }
            }
            return
        }
        openPlayPurchaseSheet(productId, details)
    }

    private fun openPlayPurchaseSheet(productId: String, details: ProductDetails) {
        if (closed) return
        val listedOffer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
        if (listedOffer != null && listedOffer.offerToken.isNullOrBlank()) {
            listener.onBillingUnavailable("Google Play returned an incomplete offer for this product.")
            return
        }

        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        listedOffer?.offerToken?.takeIf(String::isNotBlank)?.let(productParamsBuilder::setOfferToken)
        val productParams = productParamsBuilder.build()

        com.rekixo.ludopaws.feature.referrals.ReferralPopupSession.current.fullscreenInteraction()
        lastLaunchedProductId = productId
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
        com.rekixo.ludopaws.feature.referrals.ReferralPopupSession.current.fullscreenInteraction()
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

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // A second device may purchase the item after our preflight.
                val attempted = lastLaunchedProductId
                queryExistingPurchases { ownership ->
                    if (attempted == StoreCatalog.REMOVE_ADS)
                        listener.onRemoveAdsCheckoutBlocked(ownership)
                    else listener.onBillingUnavailable(
                        "Google Play already owns this item. Reopen the Store to recover the original game account.",
                    )
                }
            }

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

    private fun queryExistingPurchases(onComplete: ((RemoveAdsPlayOwnership) -> Unit)? = null) {
        if (closed || !billingClient.isReady) {
            onComplete?.invoke(RemoveAdsPlayOwnership.QUERY_FAILED)
            return
        }
        val queriedAccount = currentBillingAccountId
        listener.onRemoveAdsOwnershipChanged(RemoveAdsPlayOwnership.CHECKING)
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        ) { billingResult, purchases ->
            if (closed) return@queryPurchasesAsync
            if (queriedAccount != currentBillingAccountId) {
                onComplete?.invoke(RemoveAdsPlayOwnership.QUERY_FAILED)
                return@queryPurchasesAsync
            }
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                listener.onRemoveAdsOwnershipChanged(RemoveAdsPlayOwnership.QUERY_FAILED)
                listener.onBillingUnavailable(billingResult.debugMessage.ifBlank {
                    "Google Play could not reload previous purchases."
                })
                onComplete?.invoke(RemoveAdsPlayOwnership.QUERY_FAILED)
                return@queryPurchasesAsync
            }
            val ownership = classifyRemoveAdsOwnership(purchases, queriedAccount)
            listener.onRemoveAdsOwnershipChanged(ownership)
            processPurchases(purchases)
            onComplete?.invoke(ownership)
        }
    }

    private fun classifyRemoveAdsOwnership(purchases: List<Purchase>, accountId: String): RemoveAdsPlayOwnership {
        val paid = purchases.filter {
            StoreCatalog.REMOVE_ADS in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (paid.any { it.accountIdentifiers?.obfuscatedAccountId == accountId })
            return RemoveAdsPlayOwnership.OWNED_BY_CURRENT_ACCOUNT
        if (paid.any { it.accountIdentifiers?.obfuscatedAccountId?.let(OBFUSCATED_ACCOUNT_ID_REGEX::matches) == true })
            return RemoveAdsPlayOwnership.OWNED_BY_OTHER_ACCOUNT
        if (paid.isNotEmpty()) return RemoveAdsPlayOwnership.OWNED_WITHOUT_BINDING
        if (purchases.any { StoreCatalog.REMOVE_ADS in it.products && it.purchaseState == Purchase.PurchaseState.PENDING })
            return RemoveAdsPlayOwnership.PENDING
        return RemoveAdsPlayOwnership.NOT_OWNED
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
                        productId.let(StoreCatalog::find)?.kind in setOf(
                            StoreProductKind.NON_CONSUMABLE_REMOVE_ADS,
                            StoreProductKind.NON_CONSUMABLE_COSMETIC,
                        )
                    val validPurchaseAccountId = purchaseAccountId?.takeIf(OBFUSCATED_ACCOUNT_ID_REGEX::matches)
                    if (purchaseAccountId == null && restorableNonConsumable) {
                        listener.onBillingUnavailable(
                            "This purchase has no recoverable Ludo Paws account binding. Restore the account that made the purchase; no entitlement was granted.",
                        )
                        return@forEach
                    }
                    if (purchaseAccountId != currentBillingAccountId && !restorableNonConsumable) {
                        // A completed but unconsumed Gem purchase may belong to
                        // an older game account. Leave it with Google Play and
                        // query it again when that original account is restored.
                        // Do not interrupt a new checkout with a misleading toast.
                        if (purchaseAccountId != null &&
                            OBFUSCATED_ACCOUNT_ID_REGEX.matches(purchaseAccountId)
                        ) {
                            listener.onPurchaseBelongsToOtherAccount()
                        } else {
                            listener.onBillingUnavailable(
                                "A Play purchase has no valid game account binding. No content was granted.",
                            )
                        }
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

        /** A different-account Play purchase is never verified or granted here. */
        fun onPurchaseBelongsToOtherAccount() = Unit
        fun onRemoveAdsOwnershipChanged(ownership: RemoveAdsPlayOwnership)
        fun onRemoveAdsCheckoutBlocked(ownership: RemoveAdsPlayOwnership)

        fun onBillingUnavailable(
            message: String,
        )
    }
}
