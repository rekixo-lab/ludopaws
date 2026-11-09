package com.rekixo.ludopaws.feature.store.data.ads

import android.app.Activity
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform

/**
 * One-shot interstitial used only when the player leaves a completed match result.
 * Missing consent, network failure, or an unloaded ad must never block game navigation.
 */
internal class PostWinInterstitialAdController(
    private val activity: Activity,
    private val enabled: Boolean,
    private val adUnitId: String,
) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity)

    private var consentUpdateInProgress = false
    private var sdkInitializationInProgress = false
    private var sdkInitialized = false
    private var adLoading = false
    private var showConsumed = false
    private var showing = false
    private var closed = false
    private var interstitialAd: InterstitialAd? = null

    private val handler = Handler(Looper.getMainLooper())
    private var retryAttempt = 0
    private var retryAt = 0L
    private var loadedAt = 0L
    private val retryTask = Runnable {
        if (!closed && !activity.isFinishing && !activity.isDestroyed && activity.hasWindowFocus()) prepare()
    }
    private val expiryTask = Runnable {
        interstitialAd = null
        loadedAt = 0L
        if (!closed && activity.hasWindowFocus()) prepare()
    }

    private fun discardExpiredAd() {
        if (interstitialAd != null && SystemClock.elapsedRealtime() - loadedAt >= MAX_AD_AGE_MS) {
            interstitialAd = null
            handler.removeCallbacks(expiryTask)
        }
    }

    private fun scheduleRetry() {
        if (closed || activity.isFinishing || activity.isDestroyed) return
        val delay = if (retryAttempt >= MAX_RETRIES) {
            RETRY_COOLDOWN_MS
        } else {
            (15_000L shl retryAttempt).also { retryAttempt += 1 }
        }
        retryAt = SystemClock.elapsedRealtime() + delay
        handler.removeCallbacks(retryTask)
        handler.postDelayed(retryTask, delay)
    }

    fun prepare() {
        discardExpiredAd()
        if (activity.isFinishing || activity.isDestroyed || SystemClock.elapsedRealtime() < retryAt) return
        if (!enabled || closed || interstitialAd != null || adLoading ||
            consentUpdateInProgress || showing
        ) {
            return
        }

        if (sdkInitialized && consentInformation.canRequestAds()) {
            loadInterstitial()
            return
        }

        FamilyAdsPrivacy.configureBeforeInitialization()
        consentUpdateInProgress = true
        consentInformation.requestConsentInfoUpdate(
            activity,
            FamilyAdsPrivacy.consentParameters(),
            consentReady@{
                if (closed || activity.isFinishing || activity.isDestroyed) {
                    consentUpdateInProgress = false
                    return@consentReady
                }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    consentUpdateInProgress = false
                    if (error != null) {
                        Log.w(TAG, "Ad consent form could not be shown: ${error.message}")
                    }
                    initializeAdsIfAllowed()
                }
            },
            { error ->
                consentUpdateInProgress = false
                Log.w(TAG, "Ad consent information update failed: ${error.message}")
                initializeAdsIfAllowed()
            },
        )
    }

    /**
     * Returns true only when an already-loaded ad was shown. The caller should finish
     * immediately when false; there is no wait screen or delayed navigation for ad loading.
     */
    fun isReady(): Boolean {
        discardExpiredAd()
        return enabled && !closed && !showConsumed && !showing &&
            consentInformation.canRequestAds() && interstitialAd != null && AdBreakSession.canShow()
    }

    fun showIfReady(
        onAdDismissed: () -> Unit,
        onFailedToShow: () -> Unit,
    ): Boolean {
        if (!enabled || closed || showConsumed || showing || !consentInformation.canRequestAds()) return false
        discardExpiredAd()
        val ad = interstitialAd ?: return false
        if (!AdBreakSession.reserve()) return false

        interstitialAd = null
        handler.removeCallbacks(expiryTask)
        showConsumed = true
        showing = true
        var completed = false

        fun finishOnce(wasDismissed: Boolean) {
            if (completed) return
            com.rekixo.ludopaws.feature.referrals.ReferralPopupSession.current.fullscreenInteraction()
            completed = true
            AdBreakSession.release()
            showing = false
            showConsumed = false
            if (wasDismissed) onAdDismissed() else onFailedToShow()
            // Refill for a later completed match only while this gameplay host remains alive.
            // The caller owns navigation after dismissal or a failed-to-show callback.
            if (!closed && !activity.isFinishing && !activity.isDestroyed) prepare()
        }

        ad.fullScreenContentCallback =
            object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() { AdBreakSession.shown(); com.rekixo.ludopaws.feature.growth.GrowthMetrics.increment(activity, "ad_shown") }
                override fun onAdDismissedFullScreenContent() {
                    finishOnce(wasDismissed = true)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(TAG, "Post-win interstitial failed to show: ${error.message}")
                    finishOnce(wasDismissed = false)
                }
            }

        try {
            com.rekixo.ludopaws.feature.referrals.ReferralPopupSession.current.fullscreenInteraction()
            ad.show(activity)
        } catch (error: Exception) {
            Log.w(TAG, "Post-win interstitial could not be shown", error)
            finishOnce(wasDismissed = false)
        }
        return true
    }

    fun close() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        interstitialAd = null
    }

    private fun initializeAdsIfAllowed() {
        if (closed || activity.isFinishing || activity.isDestroyed) return
        if (!consentInformation.canRequestAds()) {
            scheduleRetry()
            return
        }
        if (sdkInitialized) {
            loadInterstitial()
            return
        }
        if (sdkInitializationInProgress) return

        sdkInitializationInProgress = true
        FamilyAdsPrivacy.configureBeforeInitialization()
        MobileAds.initialize(activity) {
            handler.post {
                sdkInitializationInProgress = false
                if (!closed && !activity.isFinishing && !activity.isDestroyed) {
                    sdkInitialized = true
                    loadInterstitial()
                }
            }
        }
    }

    private fun loadInterstitial() {
        if (closed || !consentInformation.canRequestAds() || adLoading ||
            interstitialAd != null || showing
        ) {
            return
        }

        adLoading = true
        InterstitialAd.load(
            activity,
            adUnitId,
            FamilyAdsPrivacy.request(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    adLoading = false
                    if (!closed && consentInformation.canRequestAds()) {
                        com.rekixo.ludopaws.feature.growth.GrowthMetrics.increment(activity, "ad_loaded")
                        interstitialAd = ad
                        loadedAt = SystemClock.elapsedRealtime()
                        retryAttempt = 0
                        retryAt = 0L
                        handler.removeCallbacks(retryTask)
                        handler.removeCallbacks(expiryTask)
                        handler.postDelayed(expiryTask, MAX_AD_AGE_MS)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    adLoading = false
                    com.rekixo.ludopaws.feature.growth.GrowthMetrics.increment(activity, "ad_load_failed")
                    Log.w(TAG, "Post-win interstitial failed to load: ${error.message}")
                    interstitialAd = null
                    scheduleRetry()
                }
            },
        )
    }

    companion object {
        private const val TAG = "LudoPawsPostWinAd"
        private const val MAX_RETRIES = 3
        private const val RETRY_COOLDOWN_MS = 5 * 60 * 1_000L
        private const val MAX_AD_AGE_MS = 55 * 60 * 1_000L

        internal fun showPrivacyOptions(activity: Activity, onError: (String) -> Unit) {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                if (error != null) {
                    Log.w(TAG, "Ad privacy options form failed: ${error.message}")
                    onError(error.message ?: "Ad privacy options could not be opened. Please try again.")
                }
            }
        }
    }
}
