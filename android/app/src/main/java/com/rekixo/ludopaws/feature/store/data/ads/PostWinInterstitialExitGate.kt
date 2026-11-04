package com.rekixo.ludopaws.feature.store.data.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.rekixo.ludopaws.feature.account.data.AccountRepository
import com.rekixo.ludopaws.feature.account.data.AccountSessionStore
import com.rekixo.ludopaws.feature.store.data.local.GemWalletStore
import com.rekixo.ludopaws.feature.store.domain.PostWinInterstitialPolicy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Verify ownership during gameplay; result navigation never waits for the network.
 * Only a recent authenticated answer for the still-active account permits an ad.
 * Unknown, expired or purchased Remove Ads ownership always skips the placement.
 */
internal class PostWinInterstitialExitGate(
    private val activity: Activity,
    private val accountRepository: AccountRepository,
    private val walletStore: GemWalletStore,
    private val interstitial: PostWinInterstitialAdController,
) {
    private data class Entitlement(val accountId: String, val adsRemoved: Boolean, val verifiedAt: Long)
    private val sessions = AccountSessionStore(activity)
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "LudoPawsPostWinAdGate").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var entitlement: Entitlement? = null
    @Volatile private var refreshing = false
    @Volatile private var closed = false
    private var showing = false
    private var activeDecision: PostWinExitDecisionLatch? = null
    private val refreshTask = Runnable {
        if (!closed && !activity.isFinishing && !activity.isDestroyed) {
            if (activity.hasWindowFocus()) prepare() else scheduleRefresh()
        }
    }

    /** Call on gameplay entry/resume and result presentation, before EXIT/REMATCH. */
    fun prepare() {
        if (closed || activity.isFinishing || activity.isDestroyed) return
        scheduleRefresh()
        if (refreshing) return
        val previous = entitlement
        if (previous != null && previous.accountId == sessions.load()?.accountId &&
            SystemClock.elapsedRealtime() - previous.verifiedAt in 0 until REFRESH_INTERVAL_MS) return
        refreshing = true
        try {
            executor.execute {
                val verified = try {
                    val session = accountRepository.ensureGuestSession()
                    val wallet = accountRepository.wallet()
                    check(wallet.optString("accountId") == session.accountId)
                    check(wallet.has("adsRemoved") && wallet.opt("adsRemoved") is Boolean)
                    check(walletStore.applyServerSnapshot(wallet, session.accountId))
                    Entitlement(session.accountId, wallet.getBoolean("adsRemoved"), SystemClock.elapsedRealtime())
                } catch (error: Exception) {
                    Log.w(TAG, "Could not verify ad-suppression entitlements; skipping interstitial", error)
                    null
                }
                // No UI or ad is triggered by a delayed network response.
                if (!closed) entitlement = verified
                refreshing = false
            }
        } catch (error: Exception) {
            refreshing = false
            entitlement = null
            Log.w(TAG, "Could not prepare post-match entitlement", error)
        }
    }

    private fun scheduleRefresh() {
        mainHandler.removeCallbacks(refreshTask)
        if (!closed) mainHandler.postDelayed(refreshTask, REFRESH_INTERVAL_MS)
    }

    fun interceptIfReady(onAdDismissed: () -> Unit, onSkipAd: () -> Unit): Boolean {
        if (closed || activity.isFinishing || activity.isDestroyed) return false
        if (showing) return true
        if (!interstitial.isReady()) return false
        val verified = entitlement
        val accountId = sessions.load()?.accountId
        if (verified == null || verified.accountId != accountId ||
            SystemClock.elapsedRealtime() - verified.verifiedAt !in 0..MAX_ENTITLEMENT_AGE_MS ||
            !PostWinInterstitialPolicy.shouldShowFillerInterstitial(verified.adsRemoved) || walletStore.adsRemoved()) {
            prepare()
            return false
        }
        val decision = PostWinExitDecisionLatch()
        activeDecision = decision
        showing = true
        fun complete(action: () -> Unit) {
            if (!decision.complete()) return
            showing = false
            activeDecision = null
            if (!closed && !activity.isFinishing && !activity.isDestroyed) action()
        }
        val accepted = interstitial.showIfReady(
            onAdDismissed = { complete(onAdDismissed) },
            onFailedToShow = { complete(onSkipAd) },
        )
        if (!accepted) {
            decision.cancel()
            showing = false
            activeDecision = null
        }
        return accepted
    }

    fun close() {
        closed = true
        entitlement = null
        activeDecision?.cancel()
        activeDecision = null
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }

    private companion object {
        const val TAG = "LudoPawsPostWinAdGate"
        const val REFRESH_INTERVAL_MS = 30_000L
        const val MAX_ENTITLEMENT_AGE_MS = 60_000L
    }
}

/** One navigation callback only, including a synchronous SDK failure. */
internal class PostWinExitDecisionLatch {
    private val completed = AtomicBoolean(false)
    fun complete(): Boolean = completed.compareAndSet(false, true)
    fun cancel() { completed.set(true) }
}
