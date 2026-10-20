package com.rekixo.ludopaws.feature.store.data.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.rekixo.ludopaws.feature.account.data.AccountRepository
import com.rekixo.ludopaws.feature.store.data.local.GemWalletStore
import com.rekixo.ludopaws.feature.store.domain.PostWinInterstitialPolicy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates the one non-rewarded ad placement against the current server-owned entitlement.
 *
 * The Remove Ads state is freshly read from the authenticated wallet immediately before display.
 * If the entitlement cannot be verified, navigation wins: no interstitial is shown.
 * Rewarded ads are deliberately not consulted here and remain available for their explicit reward.
 */
internal class PostWinInterstitialExitGate(
    private val activity: Activity,
    private val accountRepository: AccountRepository,
    private val walletStore: GemWalletStore,
    private val interstitial: PostWinInterstitialAdController,
) {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "LudoPawsPostWinAdGate").apply { isDaemon = true }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeDecision: PostWinExitDecisionLatch? = null
    private var pendingTimeout: Runnable? = null
    private var closed = false

    /**
     * Returns true when an eligible, loaded interstitial check has been accepted.
     * Dismissal runs the caller-provided exit callback after the ad closes.
     * If an ad is skipped or fails to show, onSkipAd performs normal navigation.
     */
    fun interceptIfReady(
        onAdDismissed: () -> Unit,
        onSkipAd: () -> Unit,
    ): Boolean {
        if (!interstitial.isReady() || activity.isFinishing || activity.isDestroyed) return false
        val decision = synchronized(this) {
            if (closed) return false
            if (activeDecision != null) return true
            PostWinExitDecisionLatch().also { activeDecision = it }
        }
        // Never hold the winning-screen exit hostage to session refresh or a slow wallet.
        // The 1.2-second budget is independent of the normal 20s+ network timeouts.
        val timeout = Runnable {
            if (decision.complete()) {
                synchronized(this) {
                    if (activeDecision === decision) {
                        activeDecision = null
                        pendingTimeout = null
                    }
                }
                if (!closed && !activity.isFinishing && !activity.isDestroyed) onSkipAd()
            }
        }
        synchronized(this) { pendingTimeout = timeout }
        mainHandler.postDelayed(timeout, MAX_EXIT_WAIT_MS)

        try {
            executor.execute {
                val mayShowInterstitial = try {
                    val session = accountRepository.ensureGuestSession()
                    val wallet = accountRepository.wallet()
                    check(wallet.optString("accountId") == session.accountId) {
                        "Wallet account did not match the active account."
                    }
                    check(wallet.has("adsRemoved") && wallet.opt("adsRemoved") is Boolean) {
                        "The server wallet did not include a verified Remove Ads entitlement."
                    }
                    check(walletStore.applyServerSnapshot(wallet, session.accountId)) {
                        "The server wallet snapshot could not be applied to the active account."
                    }
                    PostWinInterstitialPolicy.shouldShowFillerInterstitial(
                        adsRemoved = wallet.getBoolean("adsRemoved"),
                    )
                } catch (error: Exception) {
                    // Fail open to navigation, not to ads: never show a filler ad if ownership
                    // is stale or the server cannot confirm whether Remove Ads was purchased.
                    Log.w(TAG, "Could not verify ad-suppression entitlements; skipping interstitial", error)
                    false
                }

                activity.runOnUiThread {
                    if (!decision.complete()) return@runOnUiThread
                    mainHandler.removeCallbacks(timeout)
                    synchronized(this) {
                        if (activeDecision === decision) {
                            activeDecision = null
                            pendingTimeout = null
                        }
                    }
                    if (closed || activity.isFinishing || activity.isDestroyed) return@runOnUiThread

                    if (
                        mayShowInterstitial &&
                        interstitial.showIfReady(
                            onAdDismissed = onAdDismissed,
                            onFailedToShow = onSkipAd,
                        )
                    ) {
                        return@runOnUiThread
                    }
                    onSkipAd()
                }
            }
        } catch (error: Exception) {
            mainHandler.removeCallbacks(timeout)
            decision.cancel()
            synchronized(this) {
                if (activeDecision === decision) {
                    activeDecision = null
                    pendingTimeout = null
                }
            }
            Log.w(TAG, "Could not schedule the post-win entitlement check; skipping interstitial", error)
            return false
        }
        return true
    }

    fun close() {
        synchronized(this) {
            closed = true
            activeDecision?.cancel()
            activeDecision = null
            pendingTimeout?.let(mainHandler::removeCallbacks)
            pendingTimeout = null
        }
        executor.shutdownNow()
    }

    private companion object {
        const val TAG = "LudoPawsPostWinAdGate"
        const val MAX_EXIT_WAIT_MS = 1_200L
    }
}

/** One resolution only: wallet completion, timeout, or lifecycle cancellation. */
internal class PostWinExitDecisionLatch {
    private val completed = AtomicBoolean(false)
    fun complete(): Boolean = completed.compareAndSet(false, true)
    fun cancel() { completed.set(true) }
}
