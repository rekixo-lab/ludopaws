package com.rekixo.ludopaws.feature.store.data.ads

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform

/**
 * One-shot interstitial used only when the player leaves a completed winning result.
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

    fun prepare() {
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
    fun isReady(): Boolean =
        enabled && !closed && !showConsumed && !showing &&
            consentInformation.canRequestAds() && interstitialAd != null && AdBreakSession.canShow()

    fun showIfReady(
        onAdDismissed: () -> Unit,
        onFailedToShow: () -> Unit,
    ): Boolean {
        if (!enabled || closed || showConsumed || showing || !consentInformation.canRequestAds()) return false
        val ad = interstitialAd ?: return false
        if (!AdBreakSession.reserve()) return false

        interstitialAd = null
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
                override fun onAdShowedFullScreenContent() { AdBreakSession.shown() }
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
        interstitialAd = null
    }

    private fun initializeAdsIfAllowed() {
        if (closed || activity.isFinishing || activity.isDestroyed || !consentInformation.canRequestAds()) return
        if (sdkInitialized) {
            loadInterstitial()
            return
        }
        if (sdkInitializationInProgress) return

        sdkInitializationInProgress = true
        FamilyAdsPrivacy.configureBeforeInitialization()
        MobileAds.initialize(activity) {
            sdkInitializationInProgress = false
            if (closed) return@initialize
            sdkInitialized = true
            loadInterstitial()
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
                        interstitialAd = ad
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    adLoading = false
                    Log.w(TAG, "Post-win interstitial failed to load: ${error.message}")
                    interstitialAd = null
                }
            },
        )
    }

    companion object {
        private const val TAG = "LudoPawsPostWinAd"

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
