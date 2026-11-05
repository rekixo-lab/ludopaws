package com.rekixo.ludopaws.feature.growth

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.google.android.play.core.review.ReviewManagerFactory
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore

/** Neutral eligibility: completed experience on two days, regardless of win/loss. */
internal class NaturalReviewController(private val activity: Activity, private val available: () -> Boolean) {
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var ownsPrompt = false
    private val prompt = Runnable { request() }
    fun resume() { generation++; handler.removeCallbacks(prompt); handler.postDelayed(prompt, 2200L) }
    fun pause() {
        generation++; handler.removeCallbacks(prompt)
        if (ownsPrompt) { AutomaticPromptGate.release("review"); ownsPrompt = false }
    }
    private fun request() {
        if (!available() || !activity.hasWindowFocus() || activity.isFinishing || activity.isDestroyed) return
        val account = com.rekixo.ludopaws.feature.account.data.AccountSessionStore(activity).load()?.accountId
        val store = EngagementStore(activity)
        val now = System.currentTimeMillis()
        val previous = store.lastReviewAttempt()
        if (ProfileStore(activity).snapshot().totalGames < 3 || store.completedDays() < 2 ||
            previous > now || (previous > 0 && now - previous < 90 * AutomaticPromptGate.DAY)) return
        if (!AutomaticPromptGate.reserve(activity, "review")) return
        ownsPrompt = true
        val expected = generation
        val manager = try { ReviewManagerFactory.create(activity) } catch (_: Exception) {
            AutomaticPromptGate.release("review"); ownsPrompt = false; return
        }
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (generation != expected || com.rekixo.ludopaws.feature.account.data.AccountSessionStore(activity).load()?.accountId != account ||
                !available() || !activity.hasWindowFocus() || !task.isSuccessful) {
                if (generation == expected && ownsPrompt) { AutomaticPromptGate.release("review"); ownsPrompt = false }
                return@addOnCompleteListener
            }
            store.reviewAttempt()
            AutomaticPromptGate.presented(activity, "review")
            GrowthMetrics.increment(activity, "review_attempt")
            try {
                manager.launchReviewFlow(activity, task.result).addOnCompleteListener {
                    if (generation == expected && ownsPrompt) { AutomaticPromptGate.release("review"); ownsPrompt = false }
                    // Play never reports whether a review was submitted or even shown. No rewards/fallback popup.
                }
            } catch (_: Exception) {
                if (generation == expected && ownsPrompt) { AutomaticPromptGate.release("review"); ownsPrompt = false }
            }
        }
    }
}
