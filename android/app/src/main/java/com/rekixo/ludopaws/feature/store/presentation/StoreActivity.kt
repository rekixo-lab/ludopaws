package com.rekixo.ludopaws.feature.store.presentation

import com.rekixo.ludopaws.ui.dialogs.PlywoodAlertDialogBuilder
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.LudoProofApplication
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.feature.store.data.ads.PostWinInterstitialAdController
import com.rekixo.ludopaws.feature.store.data.StoreCatalogRepository
import com.rekixo.ludopaws.feature.store.data.ads.RewardedAdsController
import com.rekixo.ludopaws.feature.store.data.billing.GooglePlayBillingGateway
import com.rekixo.ludopaws.feature.store.data.billing.PendingStorePurchase
import com.rekixo.ludopaws.feature.store.data.billing.PendingStorePurchaseStore
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.data.local.GemWalletStore
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.StoreCatalog
import com.rekixo.ludopaws.feature.store.presentation.components.CosmeticPriceLabels
import com.rekixo.ludopaws.feature.store.domain.model.StoreBillingProduct
import com.rekixo.ludopaws.feature.store.domain.model.StoreTab
import com.rekixo.ludopaws.feature.store.presentation.components.storeContent
import com.rekixo.ludopaws.feature.store.presentation.components.storeHeader
import com.rekixo.ludopaws.feature.store.presentation.components.pinnedRemoveAdsContent
import com.rekixo.ludopaws.feature.store.presentation.components.rewardedAdCard
import com.rekixo.ludopaws.feature.store.presentation.components.storeTabs
import java.util.concurrent.Executors
import org.json.JSONObject

class StoreActivity :
    Activity(),
    GooglePlayBillingGateway.Listener {
    internal val wallet by lazy {
        GemWalletStore(this)
    }
    internal val cosmetics by lazy {
        CosmeticInventoryStore(this)
    }

    internal var selectedTab =
        StoreTab.GEMS
    internal var refreshBoardSelection: (() -> Unit)? = null
    internal var featuredCharacterId: String? = null
    internal var featuredPawsCard: com.rekixo.ludopaws.feature.store.presentation.components.FeaturedPawsCard? = null
    internal var showcaseResumed = false
    internal val referralPromotions by lazy {
        com.rekixo.ludopaws.feature.referrals.ReferralPromotionController(this,
            uiAvailable = { showcaseResumed && !cosmeticUpdateInProgress && !purchaseEligibilityInProgress && pendingAutoPurchaseProductId == null },
            popupVisibility = { visible -> featuredPawsCard?.setActive(showcaseResumed && !visible) })
    }
    private var cosmeticUpdateInProgress = false
    private var cosmeticFeedback: Toast? = null
    internal var liveProducts:
        Map<
            String,
            StoreBillingProduct,
            > =
        emptyMap()
    private var purchaseEligibilityInProgress = false
    private var rewardRequestInProgress = false
    private var checkoutGeneration = 0
    private var storeStarted = false
    private var pendingCheckoutProductId: String? = null
    private var pendingCheckoutAccountId: String? = null
    private var billingAvailable = false
    private var billingProductCatalogLoaded = false
    private var pendingAutoPurchaseProductId: String? = null
    private var rewardedAdsEnabledByServer = false
    private var rewardedAdsStatusLoaded = false
    private var rewardedAdsStatusError: Exception? = null
    private var billingWasCreated = false
    private var rewardedAdsWasCreated = false
    private var storeRootHost: FrameLayout? = null
    private var catalogScroll: ScrollView? = null
    private var renderedTab: StoreTab? = null
    private var renderedSignature: List<Any?>? = null
    private var renderQueued = false
    private val accountRepository by lazy {
        (application as LudoProofApplication).accountRepository
    }
    private val billingExecutor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LudoPawsStoreBilling")
        }
    private val catalogExecutor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LudoPawsStoreCatalog")
        }
    private val storeCatalogRepository by lazy {
        StoreCatalogRepository(this, accountRepository)
    }
    private val rewardVerificationExecutor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LudoPawsRewardVerification")
        }
    private val rewardedAdRequestExecutor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LudoPawsRewardRequest")
        }
    private val pendingPurchases by lazy {
        PendingStorePurchaseStore(this)
    }
    // Gateway creation runs on the UI thread. Only read cached attribution here;
    // the background eligibility check supplies a refreshed binding before checkout.
    private val installAccountId: String
        get() = accountRepository.currentSession()?.billingAccountId.orEmpty()
    internal val adsEnabledForBuild: Boolean
        get() =
            rewardedAdsEnabledByServer && BuildConfig.LUDOPROOF_ADS_ENABLED
    private val rewardedAds by lazy {
        rewardedAdsWasCreated = true
        RewardedAdsController(
            activity = this,
            enabled = { adsEnabledForBuild },
            adUnitId = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
            onMessage = ::showStoreMessage,
        )
    }

    private val billing by lazy {
        billingWasCreated = true
        GooglePlayBillingGateway(
            activity =
                this,
            enabled =
                BuildConfig
                    .LUDOPROOF_BILLING_ENABLED,
            accountId =
                installAccountId,
            listener =
                this,
        )
    }

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(
            savedInstanceState,
        )
        LudoProofTheme
            .configureWindow(
                this,
            )
        selectedTab =
            requestedInitialTab()
                ?: StoreTab.GEMS
        featuredCharacterId = intent.getStringExtra(EXTRA_FEATURED_CHARACTER_ID)
            ?.takeIf { com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry.find(it) != null }
        if (!BuildConfig.LUDOPROOF_BILLING_ENABLED && !BuildConfig.LUDOPROOF_ADS_ENABLED && selectedTab == StoreTab.GEMS) selectedTab = StoreTab.PAWS
        pendingAutoPurchaseProductId =
            intent
                .getStringExtra(EXTRA_PURCHASE_PRODUCT_ID)
                ?.takeIf { it == StoreCatalog.REMOVE_ADS }
        renderStore()
    }

    override fun onResume() {
        super.onResume()
        showcaseResumed = true
        referralPromotions.resume()
        featuredPawsCard?.setActive(true)
        // Play approval/payment may finish while this Activity is only paused.
        // Refresh owned purchases even when onStart is not called again.
        if (billingWasCreated && billingAvailable) billing.refreshPurchases()
    }

    override fun onPause() {
        showcaseResumed = false
        referralPromotions.pause()
        featuredPawsCard?.setActive(false)
        super.onPause()
    }

    internal fun showFeaturedCharacter(characterId: String) {
        featuredCharacterId = characterId
        selectedTab = StoreTab.PAWS
        renderStore()
    }

    override fun onStart() {
        super.onStart()
        storeStarted = true
        billingAvailable = false
        billingProductCatalogLoaded = false

        // Refresh catalog off the UI thread. Stale cached offers are display-only and
        // locked; all purchase/equip requests still go to the authoritative server.
        catalogExecutor.execute {
            try {
                accountRepository.ensureGuestSession()
                val snapshot = storeCatalogRepository.refresh()
                StoreCosmeticCatalog.replaceRemoteCatalog(snapshot.items, snapshot.suppressedIds)
                if (snapshot.stale) {
                    Log.i(
                        TAG,
                        "Using stale locked catalog display data (version=${snapshot.catalogVersion}, itemCount=${snapshot.items.size}).",
                    )
                }
                runOnUiThread {
                    if (canRenderStoreUi()) renderStore()
                }
            } catch (error: Exception) {
                Log.w(TAG, "Remote catalog refresh failed: ${safeErrorSummary(error)}")
            }
        }

        rewardedAdsStatusLoaded = false
        rewardedAdsStatusError = null

        billingExecutor.execute {
            // This endpoint is deliberately public. Fetch feature availability before
            // creating/refreshing the account session so an account/wallet failure does
            // not incorrectly disable AdMob for the whole store.
            var statusResponse: JSONObject? = null
            var adsAvailable = false
            var adsStatusLoaded = false
            var adsStatusError: Exception? = null
            try {
                statusResponse = accountRepository.storeStatus()
                adsAvailable = BuildConfig.LUDOPROOF_ADS_ENABLED &&
                    statusResponse.optBoolean("rewardedAdsAvailable", false)
                adsStatusLoaded = true
            } catch (error: Exception) {
                adsStatusError = error
                Log.w(TAG, "Rewarded-ad availability request failed: ${safeErrorSummary(error)}", error)
            }

            runOnUiThread {
                if (!canRenderStoreUi()) return@runOnUiThread
                rewardedAdsStatusLoaded = adsStatusLoaded
                rewardedAdsStatusError = adsStatusError
                rewardedAdsEnabledByServer = adsAvailable
                if (adsEnabledForBuild) rewardedAds.prepare()
                renderStore()
            }

            var available = false
            var availabilityError: Exception? = null
            var recoveredPurchases = 0
            var recoveryError: Exception? = null
            var activeBillingAccountId: String? = null
            try {
                val session = accountRepository.ensureGuestSession()
                activeBillingAccountId = session.billingAccountId
                available = BuildConfig.LUDOPROOF_BILLING_ENABLED &&
                    statusResponse?.optBoolean("billingAvailable", false) == true
                val serverWallet = accountRepository.wallet()
                wallet.applyServerSnapshot(serverWallet, session.accountId)
                cosmetics.applyServerSnapshot(serverWallet, session.accountId)

                // A purchase that belongs to another account is intentionally left pending.
                // It can only be recovered after that same Google-linked account is restored.
                if (available) {
                    pendingPurchases.all().forEach { pending ->
                        try {
                            val applied = verifyPurchaseAndFulfill(pending)
                            if (applied) recoveredPurchases += 1
                            pendingPurchases.remove(pending.purchaseToken)
                        } catch (error: Exception) {
                            Log.e(TAG, "Pending Play purchase verification failed: ${safeErrorSummary(error)}", error)
                            if (recoveryError == null) recoveryError = error
                        }
                    }
                }
            } catch (error: Exception) {
                availabilityError = error
                Log.w(TAG, "Account/store bootstrap failed: ${safeErrorSummary(error)}", error)
            }

            runOnUiThread {
                if (!canRenderStoreUi()) return@runOnUiThread
                billingAvailable = available

                val requestedProduct = pendingAutoPurchaseProductId
                if (
                    requestedProduct == StoreCatalog.REMOVE_ADS &&
                    wallet.adsRemoved()
                ) {
                    pendingAutoPurchaseProductId = null
                    showStoreMessage("Remove Ads is already active for this account.")
                }

                if (BuildConfig.LUDOPROOF_BILLING_ENABLED && billingAvailable) {
                    val binding = activeBillingAccountId
                    if (binding.isNullOrBlank()) {
                        showStoreMessage("The active account could not be verified for Google Play purchases.")
                        return@runOnUiThread
                    }
                    billing.updateAccountId(binding)
                    billing.connect()
                } else if (pendingAutoPurchaseProductId != null) {
                    val productId = pendingAutoPurchaseProductId
                        ?: return@runOnUiThread
                    pendingAutoPurchaseProductId = null
                    requestPurchase(productId)
                } else if (
                    BuildConfig.LUDOPROOF_BILLING_ENABLED &&
                    requestedProduct == null
                ) {
                    val message = availabilityError?.message
                        ?: "Google Play payments are not configured yet."
                    showStoreMessage(message)
                }
                renderStore()
                if (recoveredPurchases > 0) {
                    showStoreMessage(
                        "Recovered $recoveredPurchases verified Play purchase${if (recoveredPurchases == 1) "" else "s"} from this account.",
                    )
                } else if (recoveryError != null) {
                    showStoreMessage(
                        "A previous Play payment could not yet be verified for the currently signed-in account. It remains pending; restore the account that made the purchase and try again.",
                    )
                }
                if (
                    availabilityError == null &&
                    recoveryError == null &&
                    recoveredPurchases == 0 &&
                    wallet.markLegacyUnverifiedNoticeShown()
                ) {
                    showStoreMessage(
                        "Previous device-only store data was saved separately for review, but it has not been migrated. It is not usable as gems or an entitlement; your current balance and items come from the secure account wallet.",
                    )
                }
            }
        }
    }
    override fun onStop() {
        storeStarted = false
        checkoutGeneration += 1
        clearPendingCheckout()
        rewardRequestInProgress = false
        super.onStop()
    }

    override fun onDestroy() {
        referralPromotions.pause()
        if (billingWasCreated) billing.close()
        if (rewardedAdsWasCreated) rewardedAds.close()
        billingExecutor.shutdownNow()
        catalogExecutor.shutdownNow()
        rewardVerificationExecutor.shutdownNow()
        rewardedAdRequestExecutor.shutdownNow()
        super.onDestroy()
    }

    internal fun selectTab(
        tab: StoreTab,
    ) {
        if (
            selectedTab ==
            tab
        ) {
            return
        }
        selectedTab =
            tab
        renderStore()
    }

    internal fun requestPurchase(productId: String) {
        if (!BuildConfig.LUDOPROOF_BILLING_ENABLED) {
            showStoreMessage("Purchases are unavailable in this build.")
            return
        }
        if (StoreCatalog.find(productId) == null || purchaseEligibilityInProgress || !storeStarted || !canRenderStoreUi()) return
        pendingAutoPurchaseProductId = null
        val expectedAccountId = accountRepository.currentSession()?.accountId
        val generation = checkoutGeneration
        purchaseEligibilityInProgress = true
        updateCheckoutStatus()
        // Recheck availability on every explicit attempt: a failed Store bootstrap
        // must not require navigating away after connectivity returns.
        billingExecutor.execute {
            try {
                val session = accountRepository.ensureGuestSession()
                check(expectedAccountId == null || session.accountId == expectedAccountId) {
                    "The active account changed. Please try again."
                }
                val status = accountRepository.storeStatus()
                check(status.optBoolean("billingAvailable", false)) {
                    "Google Play payments are currently unavailable. Please try again later."
                }
                runOnUiThread {
                    if (generation != checkoutGeneration || !storeStarted || !canRenderStoreUi()) return@runOnUiThread
                    if (!com.rekixo.ludopaws.feature.store.domain.PurchaseAccountSafety.canLaunch(
                            session.accountId, accountRepository.currentSession()?.accountId)) {
                        clearPendingCheckout()
                        showStoreMessage("The active account changed. Please try again.")
                        return@runOnUiThread
                    }
                    if (productId == StoreCatalog.REMOVE_ADS && wallet.adsRemoved()) {
                        clearPendingCheckout()
                        showStoreMessage("Remove Ads is already active for this account.")
                        return@runOnUiThread
                    }
                    billingAvailable = true
                    billingProductCatalogLoaded = false
                    pendingCheckoutProductId = productId
                    pendingCheckoutAccountId = session.accountId
                    updateCheckoutStatus()
                    billing.updateAccountId(session.billingAccountId)
                    billing.connect()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (generation != checkoutGeneration || !storeStarted || !canRenderStoreUi()) return@runOnUiThread
                    clearPendingCheckout()
                    showStoreMessage(error.message ?: "Could not connect to Google Play. Check your connection and try again.")
                }
            }
        }
    }

    private fun clearPendingCheckout() {
        purchaseEligibilityInProgress = false
        pendingCheckoutProductId = null
        pendingCheckoutAccountId = null
        updateCheckoutStatus()
    }

    private fun updateCheckoutStatus() {
        checkoutStatusView?.text = when {
            pendingCheckoutProductId != null -> "Getting your Google Play price…"
            purchaseEligibilityInProgress -> "Connecting to Google Play…"
            else -> "Pay securely with Google Play"
        }
    }

    private fun checkoutStatusLabel(): TextView = TextView(this).apply {
        checkoutStatusView = this
        textSize = 12f
        setTextColor(0xFFEED9B7.toInt())
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(4), dp(8), dp(4))
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        updateCheckoutStatus()
    }

    private fun launchReadyCheckout() {
        val productId = pendingCheckoutProductId ?: return
        if (!storeStarted || !canRenderStoreUi() || !billingProductCatalogLoaded) return
        val expectedAccountId = pendingCheckoutAccountId
        clearPendingCheckout()
        val currentSession = accountRepository.currentSession()
        if (!com.rekixo.ludopaws.feature.store.domain.PurchaseAccountSafety.canLaunch(
                expectedAccountId, currentSession?.accountId)) {
            showStoreMessage("The active account changed. Please try again.")
            return
        }
        // Google Play displays the price and handles payment authorization. Optional
        // Google account backup and local age choices do not gate checkout.
        // A late bootstrap callback may have changed the gateway binding. Pin it
        // again in this same UI turn, immediately before opening Google's sheet.
        billing.updateAccountId(requireNotNull(currentSession).billingAccountId)
        billing.launchPurchase(productId)
    }

    internal fun requestRewardedAd(
        cosmeticId: String? = null,
    ) {
        if (!BuildConfig.LUDOPROOF_ADS_ENABLED) {
            showStoreMessage("Rewarded ads are unavailable in this build.")
            return
        }
        if (rewardRequestInProgress || !storeStarted || !canRenderStoreUi()) return
        rewardRequestInProgress = true
        val generation = checkoutGeneration
        val rewardRequestId = java.util.UUID.randomUUID().toString()
        val customData = JSONObject().put("requestId", rewardRequestId)
            .put("kind", if (cosmeticId == null) "gems" else "cosmetic")
            .apply {
                if (cosmeticId != null) put("cosmeticId", cosmeticId)
            }
            .toString()
        val initialBalance = wallet.balance()
        val initialProgress = cosmeticId?.let(cosmetics::rewardedAdProgress) ?: 0

        // Session validation can refresh a token over HTTPS. Never call it on the UI
        // thread; a slow/expired session must not throw NetworkOnMainThreadException.
        rewardedAdRequestExecutor.execute {
            try {
                val status = accountRepository.storeStatus()
                check(status.optBoolean("rewardedAdsAvailable", false)) {
                    "Rewarded ads are currently unavailable from the service. Please try again later."
                }
                val session = accountRepository.ensureGuestSession()
                runOnUiThread {
                    if (generation != checkoutGeneration || !storeStarted || !canRenderStoreUi()) return@runOnUiThread
                    rewardRequestInProgress = false
                    rewardedAdsStatusLoaded = true
                    rewardedAdsStatusError = null
                    rewardedAdsEnabledByServer = true
                    val latest = accountRepository.currentSession()
                    if (latest == null || latest.accountId != session.accountId) {
                        showStoreMessage("The signed-in account changed. Reopen the store and try again.")
                        return@runOnUiThread
                    }
                    rewardedAds.showRewardedAd(
                        userId = session.accountId,
                        customData = customData,
                    ) {
                        refreshWalletAfterRewardedAd(
                            accountId = session.accountId,
                            rewardRequestId = rewardRequestId,
                            initialBalance = initialBalance,
                            cosmeticId = cosmeticId,
                            initialProgress = initialProgress,
                        )
                    }
                }
            } catch (error: Exception) {
                Log.w(TAG, "Rewarded-ad account preparation failed: ${safeErrorSummary(error)}", error)
                runOnUiThread {
                    if (generation != checkoutGeneration || !storeStarted || !canRenderStoreUi()) return@runOnUiThread
                    rewardRequestInProgress = false
                    showStoreMessage(
                        error.message
                            ?: "Your account could not be connected to the reward service. Check your connection and try again.",
                    )
                }
            }
        }
    }
    internal fun showAdPrivacyOptions() {
        PostWinInterstitialAdController.showPrivacyOptions(this, ::showStoreMessage)
    }

    override fun onCatalogLoaded(
        products:
            Map<
                String,
                StoreBillingProduct,
            >,
    ) {
        runOnUiThread {
            if (
                !canRenderStoreUi()
            ) {
                return@runOnUiThread
            }
            liveProducts =
                products
            billingProductCatalogLoaded = true
            renderStore()
            launchReadyCheckout()
            launchPendingAutoPurchaseIfReady()
        }
    }

    private fun launchPendingAutoPurchaseIfReady() {
        if (
            !canRenderStoreUi() ||
            !billingProductCatalogLoaded ||
            !BuildConfig.LUDOPROOF_BILLING_ENABLED ||
            !billingAvailable
        ) {
            return
        }

        val productId = pendingAutoPurchaseProductId ?: return
        pendingAutoPurchaseProductId = null

        if (productId == StoreCatalog.REMOVE_ADS && wallet.adsRemoved()) {
            showStoreMessage("Remove Ads is already active for this account.")
            return
        }

        // Called only after Google Play has returned the product catalog, so this opens
        // the native Google Play purchase sheet rather than merely showing the Store page.
        requestPurchase(productId)
    }

    override fun onPurchaseRequiresVerification(
        productId: String,
        purchaseToken: String,
        accountId: String,
        onFinished: () -> Unit,
    ) {
        queuePurchaseVerification(
            productId = productId,
            purchaseToken = purchaseToken,
            accountId = accountId,
            onFinished = onFinished,
        )
    }

    private fun queuePurchaseVerification(
        productId: String,
        purchaseToken: String,
        accountId: String,
        onFinished: () -> Unit,
    ) {
        val pending = PendingStorePurchase(
            productId = productId,
            purchaseToken = purchaseToken,
            accountId = accountId,
        )
        try {
            pendingPurchases.add(pending)
        } catch (error: Exception) {
            Log.e(TAG, "Could not persist pending Play purchase", error)
            showStoreMessage(
                "Payment was received, but its verification could not be saved safely. No entitlement was granted. Please keep the app installed and reopen the store.",
            )
            onFinished()
            return
        }
        billingExecutor.execute {
            var entitlementApplied = false
            try {
                entitlementApplied = verifyPurchaseAndFulfill(pending)
                pendingPurchases.remove(purchaseToken)
                runOnUiThread {
                    if (!canRenderStoreUi()) return@runOnUiThread
                    renderStore()
                    showStoreMessage(
                        if (entitlementApplied) {
                            "Purchase verified. Your store item is ready."
                        } else {
                            "Purchase verified. This item was already added to your profile."
                        },
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Store purchase verification failed", error)
                runOnUiThread {
                    if (!canRenderStoreUi()) return@runOnUiThread
                    val detail = error.message?.takeIf(String::isNotBlank)
                        ?: "Please keep the app installed and try again."
                    showStoreMessage(
                        if (entitlementApplied) {
                            "Payment was verified, but the recovery record could not be cleared. It will be safely checked again. $detail"
                        } else {
                            "Payment was received, but its verification is still pending. No unverified entitlement was granted. $detail"
                        },
                    )
                }
            } finally {
                onFinished()
            }
        }
    }

    private fun verifyPurchaseAndFulfill(
        pending: PendingStorePurchase,
    ): Boolean {
        val session = accountRepository.requireSession()
        check(session.billingAccountId == pending.accountId) {
            "This Play purchase belongs to another account. Restore that account before recovering it."
        }
        val response = accountRepository.verifyPlayPurchase(
            productId = pending.productId,
            purchaseToken = pending.purchaseToken,
        )
        check(
            response.optBoolean("ok") &&
                response.optBoolean("verified") &&
                response.optString("productId") == pending.productId,
        ) {
            "Google Play did not confirm the purchase for the signed-in account."
        }
        val serverWallet = response.optJSONObject("wallet")
            ?: throw IllegalStateException("Verified purchase did not return the authoritative wallet.")
        check(wallet.applyServerSnapshot(serverWallet, session.accountId)) {
            "Verified wallet snapshot did not match the signed-in account."
        }
        cosmetics.applyServerSnapshot(serverWallet, session.accountId)
        ProfileStore(this).addPurchase(pending.productId) // UI cache only; server remains authoritative.
        return !response.optBoolean("replay", false)
    }

    internal fun purchaseOrSelectCosmetic(cosmetic: com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic) {
        if (cosmeticUpdateInProgress) {
            showCosmeticFeedback("Saving selection…")
            return
        }
        if (cosmetics.selectOfflineFreeCosmetic(cosmetic)) {
            val refresh = refreshBoardSelection
            if (cosmetic.category == com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory.BOARD && refresh != null) {
                refresh()
            } else {
                renderStore()
            }
            showCosmeticFeedback("${cosmetic.title} selected")
            return
        }
        val owned = cosmetics.isOwned(cosmetic.id)
        if (!owned && cosmetic.unlockChannels.size > 1) {
            val channels = cosmetic.unlockChannels
            val labels = channels.map { channel ->
                when (channel) {
                    com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind.FREE ->
                        "Free"
                    com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind.GEMS ->
                        CosmeticPriceLabels.gems(this, cosmetic.gemPrice)
                    com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind.REWARDED_ADS ->
                        cosmetics.rewardedAdProgress(cosmetic.id).let { progress ->
                            if (progress >= cosmetic.requiredAdViews) "Unlock · ads completed"
                            else "Watch ad · $progress/${cosmetic.requiredAdViews} completed"
                        }
                    com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind.LEVEL ->
                        "Reach level ${cosmetic.requiredLevel}"
                    com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind.EVENT ->
                        "Event unlock"
                    com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind.PLAY_PRODUCT ->
                        StoreCatalog.cosmeticProductId(cosmetic.id)?.let { productId ->
                            liveProducts[productId]?.formattedPrice?.let { "Buy · $it" }
                        } ?: "Google Play · price unavailable"
                }
            }
            PlywoodAlertDialogBuilder(this)
                .setTitle("Unlock ${cosmetic.title}")
                .setItems(labels.toTypedArray()) { _, index ->
                    val channel = channels.getOrNull(index) ?: return@setItems
                    if (channel == com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind.REWARDED_ADS &&
                        cosmetics.rewardedAdProgress(cosmetic.id) < cosmetic.requiredAdViews
                    ) {
                        requestRewardedAd(cosmetic.id)
                    } else {
                        chooseCosmeticUnlock(cosmetic, channel.name)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val unlockChannel = if (cosmetic.unlockChannels.size == 1) {
            cosmetic.unlockChannels.single().name
        } else {
            null
        }
        if (owned) performCosmeticPurchaseOrSelect(cosmetic, null)
        else chooseCosmeticUnlock(cosmetic, unlockChannel)
    }

    private fun chooseCosmeticUnlock(
        cosmetic: com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic,
        channel: String?,
    ) {
        if (channel == "PLAY_PRODUCT") {
            val productId = StoreCatalog.cosmeticProductId(cosmetic.id)
            if (productId == null) showStoreMessage("This item has no verified Google Play product.")
            else requestPurchase(productId)
            return
        }
        if (channel == "GEMS" || channel == null && cosmetic.unlockKind ==
            com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind.GEMS) {
            PlywoodAlertDialogBuilder(this)
                .setTitle("Unlock ${cosmetic.title}?")
                .setMessage(android.text.SpannableStringBuilder("Spend ")
                    .append(CosmeticPriceLabels.gems(this, cosmetic.gemPrice))
                    .append("? This unlock is saved to your account."))
                .setPositiveButton("Unlock") { _, _ -> performCosmeticPurchaseOrSelect(cosmetic, channel) }
                .setNegativeButton("Cancel", null).show()
            return
        }
        performCosmeticPurchaseOrSelect(cosmetic, channel)
    }

    private fun performCosmeticPurchaseOrSelect(
        cosmetic: com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic,
        unlockChannel: String?,
    ) {
        if (cosmeticUpdateInProgress) return
        if (unlockChannel == "PLAY_PRODUCT" || unlockChannel == "LEVEL" || unlockChannel == "EVENT") {
            showStoreMessage(
                "This unlock method is not available yet. No Gems or entitlements were changed.",
            )
            return
        }
        if (unlockChannel == "REWARDED_ADS" &&
            cosmetics.rewardedAdProgress(cosmetic.id) < cosmetic.requiredAdViews
        ) {
            requestRewardedAd(cosmetic.id)
            return
        }
        cosmeticUpdateInProgress = true
        showCosmeticFeedback("Saving ${cosmetic.title}…")
        billingExecutor.execute {
            try {
                val session = accountRepository.requireSession()
                val response = if (cosmetics.isOwned(cosmetic.id)) {
                    accountRepository.selectCosmetic(cosmetic.id)
                } else {
                    accountRepository.purchaseCosmetic(
                        cosmetic.id,
                        unlockChannel = unlockChannel,
                    )
                }
                val serverWallet = response.optJSONObject("wallet") ?: accountRepository.wallet()
                check(wallet.applyServerSnapshot(serverWallet, session.accountId)) {
                    "Server returned a wallet for a different account."
                }
                cosmetics.applyServerSnapshot(serverWallet, session.accountId)
                cosmetics.clearOfflineSelection(cosmetic.category)
                runOnUiThread {
                    cosmeticUpdateInProgress = false
                    if (!canRenderStoreUi()) return@runOnUiThread
                    renderStore()
                    showStoreMessage(
                        if (cosmetics.selectedId(cosmetic.category) == cosmetic.id) {
                            "${cosmetic.title} selected."
                        } else if (response.optBoolean("alreadyOwned", false)) {
                            "${cosmetic.title} is already owned."
                        } else {
                            "${cosmetic.title} was saved to your account."
                        },
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Server cosmetic purchase/select failed", error)
                val apiError = error as? com.rekixo.ludopaws.feature.account.data.AccountApiException
                val message = when (apiError?.code) {
                    "INSUFFICIENT_GEMS" -> "You do not have enough gems to buy ${cosmetic.title}."
                    "COSMETIC_NOT_OWNED" -> "This item is not owned by the signed-in account."
                    "COSMETIC_UNAVAILABLE" -> "This item is not available yet."
                    "COSMETIC_UNLOCK_CHANNEL_REQUIRED" -> "Choose an unlock method before continuing."
                    "COSMETIC_UNLOCK_CHANNEL_UNAVAILABLE" -> "That unlock method is no longer available."
                    "CATALOG_PLAY_PURCHASE_NOT_READY" -> "Direct Play purchases for cosmetic items are not available yet."
                    "STORE_CATALOG_UNAVAILABLE" -> "The store catalog could not be verified. Try again when connected."
                    "PROFILE_PROGRESSION_NOT_SERVER_AUTHORITATIVE" ->
                        "This unlock needs server-verified profile progression before it can be purchased."
                    else -> error.message ?: "The store could not update your account. Try again when connected."
                }
                runOnUiThread {
                    cosmeticUpdateInProgress = false
                    if (!canRenderStoreUi()) return@runOnUiThread
                    showStoreMessage(message)
                }
            }
        }
    }

    private fun showCosmeticFeedback(message: String) {
        cosmeticFeedback?.cancel()
        cosmeticFeedback = Toast.makeText(this, message, Toast.LENGTH_SHORT).also { it.show() }
    }

    private fun refreshWalletAfterRewardedAd(
        accountId: String,
        rewardRequestId: String,
        initialBalance: Int,
        cosmeticId: String?,
        initialProgress: Int,
    ) {
        rewardVerificationExecutor.execute {
            var latest: JSONObject? = null
            var changed = false
            for (attempt in 0 until 30) {
                try {
                    val session = accountRepository.requireSession()
                    if (session.accountId != accountId) break
                    val candidate = accountRepository.wallet()
                    latest = candidate
                    val receipts = candidate.optJSONArray("creditedAdRequestIds")
                    changed = receipts != null && (0 until receipts.length()).any { receipts.optString(it) == rewardRequestId }
                    if (changed) break
                } catch (error: Exception) {
                    Log.w(TAG, "Waiting for server-verified ad reward", error)
                }
                if (attempt < 29) Thread.sleep(2000)
            }
            val snapshot = latest
            runOnUiThread {
                if (!canRenderStoreUi()) return@runOnUiThread
                if (snapshot != null) {
                    val session = accountRepository.currentSession()
                    if (session != null && session.accountId == accountId) {
                        wallet.applyServerSnapshot(snapshot, accountId)
                        cosmetics.applyServerSnapshot(snapshot, accountId)
                    }
                }
                renderStore()
                showStoreMessage(
                    if (changed) {
                        if (cosmeticId == null) "Your +5 rewarded-ad gems were verified by the server."
                        else "Your +5 gems and rewarded-ad progress for this item were verified by the server."
                    } else {
                        "Reward verification is still pending. No local Gems were granted; reopen the store shortly to refresh the server wallet."
                    },
                )
            }
        }
    }

    override fun onPurchasePending(
        productId: String,
    ) {
        runOnUiThread {
            if (
                !canRenderStoreUi()
            ) {
                return@runOnUiThread
            }
            showStoreMessage(
                "Your Google Play purchase for $productId is pending. Items will only be granted after Google confirms payment and the backend verifies it.",
            )
        }
    }

    override fun onBillingUnavailable(
        message: String,
    ) {
        runOnUiThread {
            if (!canRenderStoreUi()) return@runOnUiThread
            clearPendingCheckout()
            Toast.makeText(
                this,
                message.ifBlank { "Google Play payments are unavailable." },
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    internal fun priceFor(
        productId: String,
        fallback: String,
    ): String =
        liveProducts[
            productId
        ]
            ?.formattedPrice
            ?: if (
                BuildConfig
                    .LUDOPROOF_BILLING_ENABLED
            ) {
                "Price unavailable"
            } else {
                fallback
            }

    internal fun dp(
        value: Int,
    ): Int =
        LudoProofTheme
            .dp(
                this,
                value,
            )

    private fun requestedInitialTab(): StoreTab? {
        val requested =
            intent
                ?.getStringExtra(
                    EXTRA_INITIAL_TAB,
                )
                ?.trim()
                ?.uppercase()
                ?: return null
        return StoreTab.entries
            .firstOrNull {
                it.name ==
                    requested
            }
    }

    private fun canRenderStoreUi(): Boolean =
        !isFinishing &&
            !isDestroyed

    private fun safeErrorSummary(error: Exception): String {
        val apiError = error as? com.rekixo.ludopaws.feature.account.data.AccountApiException
        return if (apiError != null) {
            "type=${error.javaClass.simpleName} status=${apiError.status} code=${apiError.code}"
        } else {
            "type=${error.javaClass.simpleName}"
        }
    }

    private var checkoutStatusView: TextView? = null
    internal fun renderStore() {
        if (!canRenderStoreUi()) return
        if (storeRootHost == null) {
            renderStoreNow()
        } else if (!renderQueued) {
            renderQueued = true
            window.decorView.postOnAnimation {
                renderQueued = false
                if (canRenderStoreUi()) renderStoreNow()
            }
        }
    }

    private fun renderStoreNow() {
        val signature = listOf(
            selectedTab, featuredCharacterId, liveProducts.toMap(), wallet.balance(), wallet.adsRemoved(),
            cosmetics.ownedCosmeticIds(),
            com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory.entries.map { cosmetics.selectedId(it) },
            com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog.all.let { items ->
                items.map { it to cosmetics.rewardedAdProgress(it.id) }
            },
            ProfileStore(this).snapshot(), accountRepository.currentSession()?.accountId,
            billingAvailable, billingProductCatalogLoaded, purchaseEligibilityInProgress, cosmeticUpdateInProgress,
            pendingAutoPurchaseProductId, rewardedAdsEnabledByServer, rewardedAdsStatusLoaded,
            rewardedAdsStatusError?.javaClass?.name,
        )
        if (renderedSignature == signature) return
        val previousScroll = if (renderedTab == selectedTab) catalogScroll?.scrollY ?: 0 else 0
        renderedSignature = signature
        renderedTab = selectedTab
        featuredPawsCard?.setActive(false)
        featuredPawsCard = null
        refreshBoardSelection = null
        val renderStartedAt = android.os.SystemClock.elapsedRealtime()

        val host = storeRootHost ?: LudoProofTheme.arcadeRoot(this, gameplayBackground = true).let { (root, newHost) ->
            setContentView(root)
            storeRootHost = newHost
            newHost
        }
        host.removeAllViews()

        val horizontalPadding =
            dp(
                if (LudoProofTheme.isCompactWidth(this)) {
                    10
                } else {
                    14
                },
            )
        val contentWidth =
            minOf(
                resources.displayMetrics.widthPixels - horizontalPadding * 2,
                dp(LudoProofTheme.pageMaxContentWidthDp(this)),
            )

        // The page itself does not scroll. Store navigation stays visible while only
        // the active tab's catalog below it can move.
        val page =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(12), 0, dp(8))
                // The scrolling catalog must never draw outside its allocated viewport
                // and over the pinned navigation/Remove Ads area.
                clipChildren = true
                clipToPadding = true
            }
        host.addView(
            page,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val fixedHeader =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = true
                clipToPadding = true
                // Keep the pinned header above the catalog if a child has an elevation shadow.
                elevation = dp(6).toFloat()
            }
        page.addView(
            fixedHeader,
            LinearLayout.LayoutParams(
                contentWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )

        fixedHeader.addView(storeHeader())
        fixedHeader.addView(
            storeTabs(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58),
            ).apply {
                // Keep category tabs beneath the back button and wallet pill.
                topMargin = dp(14)
            },
        )

        if (BuildConfig.LUDOPROOF_BILLING_ENABLED && selectedTab == StoreTab.GEMS) {
            fixedHeader.addView(
                pinnedRemoveAdsContent(),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(12)
                },
            )
        }

        if (!BuildConfig.LUDOPROOF_BILLING_ENABLED && BuildConfig.LUDOPROOF_ADS_ENABLED && selectedTab == StoreTab.GEMS) {
            fixedHeader.addView(rewardedAdCard())
        }

        if (BuildConfig.LUDOPROOF_BILLING_ENABLED) fixedHeader.addView(checkoutStatusLabel())

        val scroll =
            ScrollView(this).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                // Clip card artwork/elevation shadows at the scroll viewport edge.
                clipChildren = true
                clipToPadding = true
                elevation = 0f
            }
        catalogScroll = scroll
        page.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ).apply {
                topMargin = dp(8)
            },
        )

        val scrollContentHost =
            FrameLayout(this).apply {
                clipChildren = true
                clipToPadding = true
            }
        scroll.addView(
            scrollContentHost,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        scrollContentHost.addView(
            storeContent(),
            FrameLayout.LayoutParams(
                contentWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ),
        )

        if (previousScroll > 0) scroll.post { scroll.scrollTo(0, previousScroll) }
        Log.d("LudoPawsLoad", "store tab=$selectedTab ui_build_ms=${android.os.SystemClock.elapsedRealtime() - renderStartedAt}")
    }

    internal fun showStoreMessage(
        message: String,
    ) {
        if (
            !canRenderStoreUi()
        ) {
            return
        }

        PlywoodAlertDialogBuilder(this)
            .setTitle(
                "Ludo Paws Store",
            )
            .setMessage(
                message,
            )
            .setPositiveButton(
                "OK",
                null,
            )
            .show()
    }

    companion object {
        const val EXTRA_FEATURED_CHARACTER_ID = "ludo_paws_featured_character_id"
        const val EXTRA_INITIAL_TAB =
            "ludo_paws_store_initial_tab"
        const val EXTRA_PURCHASE_PRODUCT_ID =
            "ludo_paws_store_purchase_product_id"
        private const val TAG = "StoreBilling"
    }
}
