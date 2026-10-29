package com.rekixo.ludopaws.feature.store.data.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
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
    private val canPresent: () -> Boolean = { true },
) {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity)

    private var consentUpdateInProgress = false
    private var sdkInitializationInProgress = false
    private var sdkInitialized = false
    private var adLoading = false
    private var rewardedAd: RewardedAd? = null
    private var closed = false
    private var showing = false
    private var loadGeneration = 0
    private val handler = Handler(Looper.getMainLooper())
    private data class WatchRequest(val userId: String, val customData: String,
        val beforeShow: () -> Boolean, val earned: () -> Unit, val finished: (Boolean) -> Unit)
    private var pendingShow: WatchRequest? = null
    val isBusy: Boolean get() = pendingShow != null || showing
    private val requestTimeout = Runnable {
        if (adLoading) { adLoading = false; loadGeneration++ }
        failPending("The ad could not load in time. Tap Watch to retry.")
    }

    fun cancelPendingShow() {
        pendingShow = null
        handler.removeCallbacks(requestTimeout)
    }

    fun onHostResumed() { if (!closed) maybeShowPending() }

    private fun failPending(message: String) {
        val hadRequest = pendingShow != null
        cancelPendingShow()
        if (hadRequest && !closed && canPresent()) onMessage(message)
    }

    fun prepare() {
        if (!enabled() || closed || showing || rewardedAd != null || adLoading) return
        if (activity.isFinishing || activity.isDestroyed) return
        Log.d(TAG, "Preparing rewarded ad; sdkInitialized=$sdkInitialized, consentCanRequestAds=${consentInformation.canRequestAds()}")
        if (sdkInitialized && consentInformation.canRequestAds()) {
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
                if (pendingShow == null) {
                    consentUpdateInProgress = false
                    initializeAdsIfAllowed()
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
        onBeforeShow: () -> Boolean,
        onFinished: (Boolean) -> Unit,
        onRewardEarned: () -> Unit,
    ): Boolean {
        if (!enabled() || closed || activity.isFinishing || activity.isDestroyed) {
            onMessage("Rewarded ads are currently unavailable.")
            return false
        }
        if (isBusy) { onMessage("An ad request is already loading or open."); return false }
        if (!userId.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")) ||
            customData.isBlank() || customData.toByteArray(Charsets.UTF_8).size > 1024) {
            onMessage("The account reward request is invalid. No ad was requested.")
            return false
        }
        pendingShow = WatchRequest(userId, customData, onBeforeShow, onRewardEarned, onFinished)
        handler.postDelayed(requestTimeout, 30_000L)
        if (rewardedAd != null && consentInformation.canRequestAds()) maybeShowPending()
        else {
            onMessage("Loading your ad… It will open when ready. Leaving Store cancels this request.")
            prepare()
        }
        return true
    }

    private fun maybeShowPending() {
        val request = pendingShow ?: return
        if (closed || showing || !canPresent() || !activity.hasWindowFocus() || activity.isFinishing || activity.isDestroyed) return
        if (!enabled() || !consentInformation.canRequestAds()) return
        val ad = rewardedAd ?: return
        cancelPendingShow()
        val permitted = try { request.beforeShow() } catch (_: Exception) {
            onMessage("The reward request could not be prepared safely. Tap Watch to retry.")
            false
        }
        if (!permitted) return
        rewardedAd = null
        showing = true
        var delivered = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showing = false
                com.rekixo.ludopaws.feature.referrals.ReferralPopupSession.current.fullscreenInteraction()
                request.finished(delivered)
                prepare()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showing = false
                request.finished(delivered)
                if (!closed && canPresent()) onMessage("The ad could not be shown. Tap Watch to retry.")
                prepare()
            }
        }
        com.rekixo.ludopaws.feature.referrals.ReferralPopupSession.current.fullscreenInteraction()
        try {
            ad.setServerSideVerificationOptions(ServerSideVerificationOptions.Builder()
                .setUserId(request.userId).setCustomData(request.customData).build())
            ad.show(activity) {
                // Mediation may deliver this after dismissal. Record the earned
                // event exactly once at its own callback, not only on dismiss.
                if (!delivered) { delivered = true; request.earned() }
            }
        } catch (_: RuntimeException) {
            showing = false
            request.finished(delivered)
            if (!closed && canPresent()) onMessage("The ad could not open. Tap Watch to retry.")
        }
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
        cancelPendingShow()
        rewardedAd = null
    }

    private fun initializeAdsIfAllowed() {
        if (!enabled() || closed || activity.isFinishing || activity.isDestroyed) return
        if (!consentInformation.canRequestAds()) {
            failPending("Ad privacy is not ready. Review privacy options and tap Watch again.")
            return
        }
        if (sdkInitialized) {
            loadRewardedAd()
            return
        }
        if (sdkInitializationInProgress) return

        sdkInitializationInProgress = true
        Log.d(TAG, "Initializing Google Mobile Ads SDK")
        FamilyAdsPrivacy.configureBeforeInitialization()
        MobileAds.initialize(activity) {
            activity.runOnUiThread {
                if (closed) return@runOnUiThread
                sdkInitializationInProgress = false
                sdkInitialized = true
                loadRewardedAd()
            }
        }
    }

    private fun loadRewardedAd() {
        if (
            closed ||
            !enabled() ||
            !consentInformation.canRequestAds() ||
            !sdkInitialized ||
            adLoading || showing ||
            rewardedAd != null
        ) {
            return
        }
        adLoading = true
        val generation = ++loadGeneration
        Log.d(TAG, "Loading rewarded ad for configured production ad unit")
        RewardedAd.load(
            activity,
            adUnitId,
            FamilyAdsPrivacy.request(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (generation != loadGeneration) return
                    adLoading = false
                    Log.i(TAG, "Rewarded ad loaded successfully")
                    if (!closed && consentInformation.canRequestAds()) {
                        rewardedAd = ad
                        maybeShowPending()
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (generation != loadGeneration) return
                    adLoading = false
                    failPending("No ad is available right now. Tap Watch to retry later.")
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
