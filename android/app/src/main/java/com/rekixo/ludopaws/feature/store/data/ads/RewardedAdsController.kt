package com.rekixo.ludopaws.feature.store.data.ads

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform

internal class RewardedAdsController(
    private val activity: Activity,
    private val enabled: () -> Boolean,
    private val adUnitId: String,
    private val onMessage: (String) -> Unit,
) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity)

    private var consentUpdateInProgress = false
    private var sdkInitializationInProgress = false
    private var sdkInitialized = false
    private var adLoading = false
    private var adLoadFailed = false
    private var rewardedAd: RewardedAd? = null
    private var closed = false

    fun prepare() {
        if (!enabled() || closed || rewardedAd != null || adLoading) return
        if (activity.isFinishing || activity.isDestroyed) return
        Log.d(TAG, "Preparing rewarded ad; sdkInitialized=$sdkInitialized, consentCanRequestAds=${consentInformation.canRequestAds()}")
        if (sdkInitialized) {
            loadRewardedAd()
            return
        }
        if (sdkInitializationInProgress || consentUpdateInProgress) return

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
                    Log.d(TAG, "Consent flow complete; canRequestAds=${consentInformation.canRequestAds()}")
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

    fun showRewardedAd(
        userId: String,
        customData: String,
        onRewardEarned: () -> Unit,
    ): Boolean {
        // Recheck UMP consent at display time; a previously loaded ad is not authorization.
        if (!enabled() || closed || !consentInformation.canRequestAds()) {
            onMessage("Rewarded ads are unavailable until ad privacy choices allow them.")
            return false
        }
        if (
            !userId.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")) ||
            customData.isBlank() ||
            customData.toByteArray(Charsets.UTF_8).size > 1024
        ) {
            onMessage("The account reward request is invalid. No ad reward was requested.")
            return false
        }
        val ad = rewardedAd
        if (ad == null) {
            prepare()
            onMessage(
                if (adLoadFailed) {
                    "No rewarded ad could be loaded right now. Check your connection and try again later."
                } else if (consentInformation.canRequestAds()) {
                    "A rewarded ad is loading. Please try again shortly."
                } else {
                    "Ads are not ready yet. Review ad privacy choices or try again later."
                },
            )
            return false
        }

        rewardedAd = null
        ad.setServerSideVerificationOptions(
            ServerSideVerificationOptions.Builder()
                .setUserId(userId)
                .setCustomData(customData)
                .build(),
        )
        var rewardEarned = false
        var rewardDelivered = false
        ad.fullScreenContentCallback =
            object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (rewardEarned && !rewardDelivered && !closed) {
                        rewardDelivered = true
                        onRewardEarned()
                    }
                    prepare()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(TAG, "Rewarded ad failed to show: ${error.message}")
                    onMessage("The ad could not be shown. No reward was added.")
                    prepare()
                }
            }
        ad.show(activity) {
            rewardEarned = true
        }
        return true
    }

    fun showPrivacyOptions() {
        if (!enabled() || closed) return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) {
                Log.e(TAG, "Ad privacy options form failed: ${error.message}")
                onMessage("Ad privacy options could not be opened. Please try again.")
            } else if (consentInformation.canRequestAds()) {
                prepare()
            } else {
                rewardedAd = null
            }
        }
    }

    fun close() {
        closed = true
        rewardedAd = null
    }

    private fun initializeAdsIfAllowed() {
        if (!enabled() || closed || activity.isFinishing || activity.isDestroyed || !consentInformation.canRequestAds()) return
        if (sdkInitialized) {
            loadRewardedAd()
            return
        }
        if (sdkInitializationInProgress) return

        sdkInitializationInProgress = true
        Log.d(TAG, "Initializing Google Mobile Ads SDK")
        FamilyAdsPrivacy.configureBeforeInitialization()
        MobileAds.initialize(activity) {
            if (closed) return@initialize
            sdkInitializationInProgress = false
            sdkInitialized = true
            Log.d(TAG, "Google Mobile Ads SDK initialized")
            loadRewardedAd()
        }
    }

    private fun loadRewardedAd() {
        if (
            closed ||
            !enabled() ||
            !consentInformation.canRequestAds() ||
            !sdkInitialized ||
            adLoading ||
            rewardedAd != null
        ) {
            return
        }
        adLoading = true
        Log.d(TAG, "Loading rewarded ad for configured production ad unit")
        RewardedAd.load(
            activity,
            adUnitId,
            FamilyAdsPrivacy.request(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    adLoading = false
                    adLoadFailed = false
                    Log.i(TAG, "Rewarded ad loaded successfully")
                    if (!closed && consentInformation.canRequestAds()) {
                        rewardedAd = ad
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    adLoading = false
                    adLoadFailed = true
                    Log.w(
                        TAG,
                        "Rewarded ad failed to load: domain=${error.domain}, code=${error.code}, message=${error.message}",
                    )
                }
            },
        )
    }

    private companion object {
        const val TAG = "LudoPawsAds"
    }
}
