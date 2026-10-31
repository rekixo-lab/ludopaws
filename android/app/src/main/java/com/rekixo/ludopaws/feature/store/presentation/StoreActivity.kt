package com.rekixo.ludopaws.feature.store.presentation

import com.rekixo.ludopaws.ui.dialogs.PlywoodAlertDialogBuilder
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.rekixo.ludopaws.feature.store.data.ads.PendingAdReward
import com.rekixo.ludopaws.feature.store.data.ads.PendingAdRewardStore
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
    internal var promotedCosmeticId: String? = null
    internal var storeSpotlight: com.rekixo.ludopaws.feature.store.presentation.components.StoreLockedSpotlightView? = null
    internal val spotlightPositions = mutableMapOf<StoreTab, String>()
    internal var showcaseResumed = false
    internal val referralPromotions by lazy {
        com.rekixo.ludopaws.feature.referrals.ReferralPromotionController(this,
            uiAvailable = { showcaseResumed && !cosmeticUpdateInProgress && !purchaseEligibilityInProgress && !rewardRequestInProgress && !(rewardedAdsWasCreated && rewardedAds.isBusy) && pendingAutoPurchaseProductId == null },
            popupVisibility = { visible -> storeSpotlight?.setActive(showcaseResumed && !visible) })
    }
    private var storeOnline = false
    private var storeRefreshInFlight = false
    private val connectivity by lazy {
        com.rekixo.ludopaws.ConnectivityMonitor(this) { connected -> runOnUiThread {
            if (!storeStarted || isFinishing || isDestroyed) return@runOnUiThread
            val changed = storeOnline != connected
            storeOnline = connected
            if (!connected && changed) {
                checkoutGeneration++
                storeRefreshInFlight = false
                catalogRefreshInProgress = false
                pendingAutoPurchaseProductId?.let { chosenProductId = it }
                pendingAutoPurchaseProductId = null
                clearPendingCheckout()
                checkoutRetryNeeded = chosenProductId != null
                if (rewardedAdsWasCreated) rewardedAds.cancelPendingShow()
            }
            if (connected && changed) refreshConnectedStore()
            updateCheckoutStatus()
            updateCatalogStatus()
            renderStore()
        } }
    }
    private var cosmeticUpdateInProgress = false
    private var cosmeticFeedback: Toast? = null
    internal var liveProducts:
        Map<
            String,
            StoreBillingProduct,
            > =
        emptyMap()
    private var chosenProductId: String? = null
    private var chosenProductOwner: String? = null
    private var checkoutRetryNeeded = false
    private val scrollPositions = mutableMapOf<StoreTab, Int>()
    private var catalogRefreshInProgress = false
    private var catalogRefreshFailed = false
    private var catalogStatusView: TextView? = null
    private var purchaseEligibilityInProgress = false
    private var rewardRequestInProgress = false
    private val pendingAdRewards by lazy { PendingAdRewardStore(this) }
    private val rewardHandler = Handler(Looper.getMainLooper())
    private var rewardRecoveryInFlight = false
    private var rewardRecoveryAttempt = 0
    private var rewardStatusText: TextView? = null
    private var rewardRecoveryMessage = ""
    private val retryRewardRecovery = Runnable { recoverPendingAdRewards(resetAttempts = false) }
    private val observedStorePrefs by lazy {
        getSharedPreferences(com.rekixo.ludopaws.feature.store.data.local.StorePreferences.PREFS_NAME, MODE_PRIVATE)
    }
    private val walletChanged = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { if (storeStarted && showcaseResumed && canRenderStoreUi()) renderStore() }
    }
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
            // A modal loading message steals window focus and can prevent the
            // queued ad from opening until an unnecessary second tap.
            onMessage = ::showCosmeticFeedback,
            canPresent = { storeStarted && showcaseResumed },
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
        selectedTab = StoreTab.entries.firstOrNull { it.name == savedInstanceState?.getString("store_tab") }
            ?: requestedInitialTab() ?: StoreTab.GEMS
        StoreTab.entries.forEach { tab -> scrollPositions[tab] = savedInstanceState?.getInt("store_scroll_" + tab.name, 0)?.coerceAtLeast(0) ?: 0 }
        StoreTab.entries.forEach { tab -> savedInstanceState?.getString("store_spotlight_" + tab.name)?.let { spotlightPositions[tab] = it } }
        chosenProductId = savedInstanceState?.getString("store_product")?.takeIf { StoreCatalog.find(it) != null }
        chosenProductOwner = savedInstanceState?.getString("store_product_owner")
        checkoutRetryNeeded = chosenProductId != null
        featuredCharacterId = (savedInstanceState?.getString("store_character") ?: intent.getStringExtra(EXTRA_FEATURED_CHARACTER_ID))
            ?.takeIf { com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry.find(it) != null }
        promotedCosmeticId = if (savedInstanceState != null) savedInstanceState.getString("store_promoted_item")
            else intent.getStringExtra(EXTRA_PROMOTED_COSMETIC_ID)
        promotedCosmeticId = promotedCosmeticId?.takeIf { StoreCosmeticCatalog.find(it) != null }
        if (savedInstanceState == null) promotedCosmeticId?.let { id ->
            StoreCosmeticCatalog.find(id)?.let { selectedTab = PromotionInterest.tabFor(it) }
        }
        if (!BuildConfig.LUDOPROOF_BILLING_ENABLED && !BuildConfig.LUDOPROOF_ADS_ENABLED && selectedTab == StoreTab.GEMS) selectedTab = StoreTab.PAWS
        pendingAutoPurchaseProductId =
            if (savedInstanceState == null) intent
                .getStringExtra(EXTRA_PURCHASE_PRODUCT_ID)
                ?.takeIf { it == StoreCatalog.REMOVE_ADS }
            else null // Restoration preserves selection, never reopens checkout by itself.
        storeCatalogRepository.cached().let { cached ->
            if (cached.items.isNotEmpty()) StoreCosmeticCatalog.replaceRemoteCatalog(cached.items, cached.suppressedIds)
        }
        renderStore()
    }

    override fun onResume() {
        super.onResume()
        showcaseResumed = true
        renderStore()
        referralPromotions.resume()
        storeSpotlight?.setActive(true)
        if (rewardedAdsWasCreated) rewardedAds.onHostResumed()
        recoverPendingAdRewards()
        // Play approval/payment may finish while this Activity is only paused.
        // Refresh owned purchases even when onStart is not called again.
        if (billingWasCreated && billingAvailable) billing.refreshPurchases()
    }

    override fun onPause() {
        showcaseResumed = false
        referralPromotions.pause()
        storeSpotlight?.setActive(false)
        super.onPause()
    }

    internal fun showFeaturedCharacter(characterId: String) {
        featuredCharacterId = characterId
        val selections = com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore(this)
        if (selections.isUnlocked(characterId)) {
            selections.select(com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog.STARTER_PACK_ID, characterId)
        }
        StoreCosmeticCatalog.characterUnlock(characterId)?.let { spotlightPositions[StoreTab.PAWS] = it.id }
        if (promotedCosmeticId != null) promotedCosmeticId = StoreCosmeticCatalog.characterUnlock(characterId)?.id
        selectedTab = StoreTab.PAWS
        renderStore()
    }

    override fun onStart() {
        super.onStart()
        storeStarted = true
        observedStorePrefs.registerOnSharedPreferenceChangeListener(walletChanged)
        storeOnline = connectivity.isOnline()
        connectivity.start()
        if (storeOnline) refreshConnectedStore(allowRequestedCheckout = true)
        else {
            billingAvailable = false
            billingProductCatalogLoaded = false
            catalogRefreshFailed = true
            updateCatalogStatus()
            updateCheckoutStatus()
            renderStore()
        }
    }

    private fun refreshConnectedStore(allowRequestedCheckout: Boolean = false) {
        if (!storeStarted || !storeOnline || storeRefreshInFlight) return
        storeRefreshInFlight = true
        if (!allowRequestedCheckout && pendingAutoPurchaseProductId != null) {
            chosenProductId = pendingAutoPurchaseProductId
            pendingAutoPurchaseProductId = null
            checkoutRetryNeeded = true
        }
        com.rekixo.ludopaws.feature.store.data.local.LocalEquipmentSync.request(this)
        billingAvailable = false
        billingProductCatalogLoaded = false

        val availabilityGeneration = checkoutGeneration
        refreshStoreCatalog()

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
                if (!storeStarted || availabilityGeneration != checkoutGeneration || !canRenderStoreUi()) return@runOnUiThread
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
                if (!storeStarted || availabilityGeneration != checkoutGeneration || !canRenderStoreUi()) return@runOnUiThread
                storeRefreshInFlight = false
                billingAvailable = available
                recoverPendingAdRewards()

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
        connectivity.stop()
        storeRefreshInFlight = false
        if (purchaseEligibilityInProgress || pendingCheckoutProductId != null) checkoutRetryNeeded = chosenProductId != null
        if (rewardedAdsWasCreated) rewardedAds.cancelPendingShow()
        rewardHandler.removeCallbacks(retryRewardRecovery)
        storeStarted = false
        observedStorePrefs.unregisterOnSharedPreferenceChangeListener(walletChanged)
        checkoutGeneration += 1
        catalogRefreshInProgress = false
        clearPendingCheckout()
        rewardRequestInProgress = false
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        renderedTab?.let { tab -> scrollPositions[tab] = catalogScroll?.scrollY ?: scrollPositions[tab] ?: 0 }
        outState.putString("store_tab", selectedTab.name)
        outState.putString("store_character", featuredCharacterId)
        outState.putString("store_promoted_item", promotedCosmeticId)
        spotlightPositions.forEach { (tab, id) -> outState.putString("store_spotlight_" + tab.name, id) }
        outState.putString("store_product", chosenProductId)
        outState.putString("store_product_owner", chosenProductOwner)
        scrollPositions.forEach { (tab, position) -> outState.putInt("store_scroll_" + tab.name, position) }
        super.onSaveInstanceState(outState)
    }

    private fun refreshStoreCatalog() {
        if (!storeStarted || !storeOnline || catalogRefreshInProgress || isFinishing || isDestroyed) return
        catalogRefreshInProgress = true
        catalogRefreshFailed = false
        updateCatalogStatus()
        val generation = checkoutGeneration
        catalogExecutor.execute {
            try {
                val owner = accountRepository.ensureGuestSession().accountId
                val snapshot = storeCatalogRepository.refresh()
                runOnUiThread {
                    if (!storeStarted || generation != checkoutGeneration || !canRenderStoreUi()) return@runOnUiThread
                    catalogRefreshInProgress = false
                    if (accountRepository.currentSession()?.accountId != owner) {
                        catalogRefreshFailed = true
                    } else {
                        StoreCosmeticCatalog.replaceRemoteCatalog(snapshot.items, snapshot.suppressedIds)
                        catalogRefreshFailed = snapshot.stale
                    }
                    updateCatalogStatus()
                    renderStore()
                }
            } catch (error: Exception) {
                Log.w(TAG, "Catalog refresh failed: ${safeErrorSummary(error)}")
                runOnUiThread {
                    if (!storeStarted || generation != checkoutGeneration || !canRenderStoreUi()) return@runOnUiThread
                    catalogRefreshInProgress = false
                    catalogRefreshFailed = true
                    updateCatalogStatus()
                }
            }
        }
    }

    private fun updateCatalogStatus() {
        catalogStatusView?.apply {
            text = when {
                !storeOnline -> "Offline catalog · Connect to refresh offers"
                catalogRefreshInProgress -> "Refreshing store items…"
                catalogRefreshFailed -> "Items could not refresh · Tap to retry"
                else -> ""
            }
            visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
            isEnabled = storeOnline && !catalogRefreshInProgress
        }
    }

    private fun catalogStatusLabel(): TextView = TextView(this).apply {
        catalogStatusView = this
        textSize = 12f
        setTextColor(0xFF60452D.toInt())
        gravity = Gravity.CENTER
        minHeight = dp(48)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        setOnClickListener { refreshStoreCatalog() }
        updateCatalogStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && storeStarted && showcaseResumed && rewardedAdsWasCreated) rewardedAds.onHostResumed()
    }

    override fun onDestroy() {
        cosmeticFeedback?.cancel()
        observedStorePrefs.unregisterOnSharedPreferenceChangeListener(walletChanged)
        rewardHandler.removeCallbacksAndMessages(null)
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
            if (catalogRefreshFailed) refreshStoreCatalog()
            if (billingWasCreated && billingAvailable) billing.connect()
            return
        }
        selectedTab =
            tab
        renderStore()
    }

    internal fun requestPurchase(productId: String) {
        if (StoreCatalog.find(productId) == null) return
        if (!connectivity.isOnline()) {
            chosenProductId = productId
            chosenProductOwner = accountRepository.currentSession()?.accountId
            checkoutRetryNeeded = true
            updateCheckoutStatus()
            showStoreMessage("You are offline. Connect, then retry this selected item.")
            return
        }
        if (!BuildConfig.LUDOPROOF_BILLING_ENABLED) {
            showStoreMessage("Purchases are unavailable in this build.")
            return
        }
        if (StoreCatalog.find(productId) == null || purchaseEligibilityInProgress || !storeStarted || !canRenderStoreUi()) return
        chosenProductId = productId
        chosenProductOwner = accountRepository.currentSession()?.accountId
        checkoutRetryNeeded = false
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
                    chosenProductOwner = session.accountId
                    pendingCheckoutProductId = productId
                    pendingCheckoutAccountId = session.accountId
                    updateCheckoutStatus()
                    billing.updateAccountId(session.billingAccountId)
                    billing.connect()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (generation != checkoutGeneration || !storeStarted || !canRenderStoreUi()) return@runOnUiThread
                    checkoutRetryNeeded = true
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
            !storeOnline -> "Offline · Owned items available · Connect for purchases"
            pendingCheckoutProductId != null -> "Getting your Google Play price…"
            purchaseEligibilityInProgress -> "Connecting to Google Play…"
            checkoutRetryNeeded && chosenProductId != null -> "Retry selected item · " + StoreCatalog.find(chosenProductId.orEmpty())?.title.orEmpty()
            else -> "Pay securely with Google Play"
        }
    }

    private fun checkoutStatusLabel(): TextView = TextView(this).apply {
        checkoutStatusView = this
        textSize = 12f
        setTextColor(0xFF60452D.toInt())
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(4), dp(8), dp(4))
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        minHeight = dp(48)
        setOnClickListener {
            val product = chosenProductId ?: return@setOnClickListener
            if (!checkoutRetryNeeded) return@setOnClickListener
            val currentOwner = accountRepository.currentSession()?.accountId
            if (chosenProductOwner != null && chosenProductOwner != currentOwner) {
                chosenProductId = null
                chosenProductOwner = null
                checkoutRetryNeeded = false
                updateCheckoutStatus()
                showStoreMessage("The active account changed. Select the item again for this account.")
            } else requestPurchase(product)
        }
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
        if (!connectivity.isOnline()) { showStoreMessage("Connect to the internet to watch an ad and verify its reward."); return }
        if (!BuildConfig.LUDOPROOF_ADS_ENABLED) {
            showStoreMessage("Rewarded ads are unavailable in this build.")
            return
        }
        if (rewardRequestInProgress || (rewardedAdsWasCreated && rewardedAds.isBusy) || !storeStarted || !showcaseResumed || !canRenderStoreUi()) return
        rewardRequestInProgress = true
        val generation = checkoutGeneration
        val rewardRequestId = java.util.UUID.randomUUID().toString()
        val customData = JSONObject().put("requestId", rewardRequestId)
            .put("kind", if (cosmeticId == null) "gems" else "cosmetic")
            .apply {
                if (cosmeticId != null) put("cosmeticId", cosmeticId)
            }
            .toString()

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
                    val record = PendingAdReward(session.accountId, rewardRequestId, cosmeticId)
                    rewardedAds.showRewardedAd(
                        userId = session.accountId,
                        customData = customData,
                        onBeforeShow = {
                            if (!storeStarted || !showcaseResumed || accountRepository.currentSession()?.accountId != record.accountId) false
                            else if (cosmeticId != null && StoreCosmeticCatalog.find(cosmeticId)?.contentAvailable != true) {
                                showStoreMessage("This item is no longer available. No ad was shown."); false
                            } else try { pendingAdRewards.put(record); true }
                            catch (error: Exception) { showStoreMessage(error.message ?: "Reward recovery could not be saved. Please retry."); false }
                        },
                        onFinished = { earned ->
                            if (!earned) runCatching { pendingAdRewards.put(record.copy(abandoned = true)) }
                            recoverPendingAdRewards()
                        },
                        onRewardEarned = {
                            runCatching { pendingAdRewards.put(record.copy(earned = true)) }
                                .onFailure { Log.w(TAG, "Earned reward hint remains prepared; server verification is authoritative") }
                            recoverPendingAdRewards()
                        },
                    )
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
                !storeStarted || !canRenderStoreUi()
            ) {
                return@runOnUiThread
            }
            liveProducts = products
            billingProductCatalogLoaded = true
            val waitingProduct = pendingCheckoutProductId
            if (waitingProduct != null && products[waitingProduct] == null) {
                checkoutRetryNeeded = true
                clearPendingCheckout()
                showStoreMessage("This product is unavailable for the current Play account or region. Tap the selected item retry to check again.")
            }
            renderStore()
            launchReadyCheckout()
            launchPendingAutoPurchaseIfReady()
        }
    }

    private fun launchPendingAutoPurchaseIfReady() {
        if (
            !storeStarted || !canRenderStoreUi() ||
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
        runOnUiThread { clearChosenProductForDelivery(productId, accountId) }
        queuePurchaseVerification(
            productId = productId,
            purchaseToken = purchaseToken,
            accountId = accountId,
            onFinished = onFinished,
        )
    }

    private fun clearChosenProductForDelivery(productId: String, billingOwner: String? = null) {
        if (chosenProductId != productId) return
        val current = accountRepository.currentSession()
        if (billingOwner != null && current?.billingAccountId != billingOwner) return
        if (chosenProductOwner != null && current?.accountId != chosenProductOwner) return
        // A provider-reported pending/completed purchase needs delivery recovery,
        // not a second checkout request for the same selected item.
        chosenProductId = null
        chosenProductOwner = null
        checkoutRetryNeeded = false
        updateCheckoutStatus()
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

    private fun currentOfferMatches(cosmetic: com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic): Boolean {
        val current = StoreCosmeticCatalog.find(cosmetic.id)
        if (current == null || !current.contentAvailable || !current.purchaseAvailable || current != cosmetic) {
            showStoreMessage("This item changed or is unavailable. Refresh and select it again; nothing was spent.")
            refreshStoreCatalog()
            return false
        }
        return true
    }

    internal fun purchaseOrSelectCosmetic(cosmetic: com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic) {
        if (cosmetics.isOwned(cosmetic.id) && cosmetics.selectOwnedLocalCosmetic(cosmetic)) {
            com.rekixo.ludopaws.feature.store.data.local.LocalEquipmentSync.request(this)
            renderStore()
            showCosmeticFeedback("${cosmetic.title} selected")
            return
        }
        if (!currentOfferMatches(cosmetic)) return
        if (cosmeticUpdateInProgress) {
            showCosmeticFeedback("Saving selection…")
            return
        }
        if (cosmetics.selectOwnedLocalCosmetic(cosmetic)) {
            com.rekixo.ludopaws.feature.store.data.local.LocalEquipmentSync.request(this)
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
        if (cosmeticUpdateInProgress || !currentOfferMatches(cosmetic)) return
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

    private fun rewardStatusLabel(): TextView = TextView(this).apply {
        rewardStatusText = this
        textSize = 12f
        setTextColor(0xFF60452D.toInt())
        gravity = Gravity.CENTER
        minHeight = dp(48)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        setOnClickListener { recoverPendingAdRewards() }
        text = rewardRecoveryMessage
        visibility = if (rewardRecoveryMessage.isBlank()) View.GONE else View.VISIBLE
    }

    private fun updateRewardStatus(message: String) {
        rewardRecoveryMessage = message
        rewardStatusText?.apply {
            text = message
            visibility = if (message.isBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun recoverPendingAdRewards(resetAttempts: Boolean = true) {
        if (!storeStarted || !showcaseResumed || !canRenderStoreUi() || rewardRecoveryInFlight) return
        val owner = accountRepository.currentSession()?.accountId ?: return
        val records = try { pendingAdRewards.forAccount(owner) } catch (_: Exception) {
            updateRewardStatus("Reward recovery needs attention · Keep the app installed and retry")
            return
        }
        if (records.isEmpty()) { updateRewardStatus(""); return }
        if (resetAttempts) rewardRecoveryAttempt = 0
        rewardHandler.removeCallbacks(retryRewardRecovery)
        rewardRecoveryInFlight = true
        rewardRecoveryAttempt++
        val generation = checkoutGeneration
        if (records.any { it.earned || !it.abandoned }) updateRewardStatus("Checking ad reward verification…")
        rewardVerificationExecutor.execute {
            var confirmed = emptySet<String>()
            try {
                check(accountRepository.requireSession().accountId == owner)
                val candidate = accountRepository.wallet()
                check(candidate.optString("accountId") == owner) { "Reward wallet owner changed" }
                val receipts = candidate.optJSONArray("creditedAdRequestIds")
                val credited = if (receipts == null) emptySet() else (0 until receipts.length()).map { receipts.optString(it) }.toSet()
                confirmed = records.map { it.requestId }.filter { it in credited }.toSet()
                if (accountRepository.currentSession()?.accountId == owner) {
                    // Cache the authoritative response before clearing its hint,
                    // even if the old Store Activity is being destroyed.
                    wallet.applyServerSnapshot(candidate, owner)
                    cosmetics.applyServerSnapshot(candidate, owner)
                }
                pendingAdRewards.confirm(owner, confirmed)
            } catch (error: Exception) {
                Log.w(TAG, "Ad reward verification remains pending: ${safeErrorSummary(error)}")
            }
            runOnUiThread {
                rewardRecoveryInFlight = false
                if (!storeStarted || !showcaseResumed || !canRenderStoreUi()) return@runOnUiThread
                if (generation != checkoutGeneration || accountRepository.currentSession()?.accountId != owner) {
                    recoverPendingAdRewards()
                    return@runOnUiThread
                }
                val remaining = runCatching { pendingAdRewards.forAccount(owner) }.getOrNull()
                val visible = remaining?.any { it.earned || !it.abandoned } == true
                updateRewardStatus(when {
                    remaining == null -> "Reward status could not be saved · Tap to retry"
                    visible -> "Reward verification pending · Tap to retry"
                    else -> ""
                })
                if (confirmed.isNotEmpty()) showCosmeticFeedback("Ad reward verified. Your server wallet is up to date.")
                renderStore()
                if (visible && rewardRecoveryAttempt < 6) {
                    val delay = minOf(15_000L, 2_000L shl (rewardRecoveryAttempt - 1))
                    rewardHandler.postDelayed(retryRewardRecovery, delay)
                }
            }
        }
    }

    override fun onPurchasePending(
        productId: String,
    ) {
        runOnUiThread {
            clearChosenProductForDelivery(productId)
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
            if (!storeStarted || !canRenderStoreUi()) return@runOnUiThread
            checkoutRetryNeeded = chosenProductId != null
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
            selectedTab, storeOnline, featuredCharacterId, promotedCosmeticId, spotlightPositions.toMap(), liveProducts.toMap(), wallet.balance(), wallet.adsRemoved(),
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
        renderedTab?.let { tab -> scrollPositions[tab] = catalogScroll?.scrollY ?: scrollPositions[tab] ?: 0 }
        val previousScroll = scrollPositions[selectedTab] ?: 0
        renderedSignature = signature
        renderedTab = selectedTab
        storeSpotlight?.setActive(false)
        storeSpotlight = null
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

        // Keep tabs reachable even when several status messages and the offer
        // are visible together on a short/split-screen window.
        val offerArea = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val windowLimit = dp(resources.configuration.screenHeightDp.coerceAtLeast(1)) / 3
                val parentLimit = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED)
                    windowLimit else minOf(windowLimit, View.MeasureSpec.getSize(heightMeasureSpec))
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(parentLimit, View.MeasureSpec.AT_MOST))
            }
        }.apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val offerContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        offerArea.addView(offerContent)
        fixedHeader.addView(offerArea, LinearLayout.LayoutParams(contentWidth, LinearLayout.LayoutParams.WRAP_CONTENT))
        offerContent.addView(catalogStatusLabel())
        offerContent.addView(rewardStatusLabel())

        if (BuildConfig.LUDOPROOF_BILLING_ENABLED && selectedTab == StoreTab.GEMS) {
            offerContent.addView(
                pinnedRemoveAdsContent(),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(12)
                },
            )
        }

        if (BuildConfig.LUDOPROOF_ADS_ENABLED && selectedTab == StoreTab.GEMS) {
            offerContent.addView(rewardedAdCard())
        }

        if (BuildConfig.LUDOPROOF_BILLING_ENABLED) offerContent.addView(checkoutStatusLabel())

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
        const val EXTRA_PROMOTED_COSMETIC_ID = "promoted_cosmetic_id"
        const val EXTRA_FEATURED_CHARACTER_ID = "ludo_paws_featured_character_id"
        const val EXTRA_INITIAL_TAB =
            "ludo_paws_store_initial_tab"
        const val EXTRA_PURCHASE_PRODUCT_ID =
            "ludo_paws_store_purchase_product_id"
        private const val TAG = "StoreBilling"
    }
}
