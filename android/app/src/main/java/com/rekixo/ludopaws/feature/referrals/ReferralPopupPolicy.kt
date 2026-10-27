package com.rekixo.ludopaws.feature.referrals

internal data class ReferralPopupHistory(val impressions: Int = 0, val shownAt: Long = 0, val dismissedAt: Long = 0)

internal object ReferralPopupPolicy {
    const val DAY_MS = 86_400_000L
    const val CAMPAIGN = "friendship-bundle-v1"
    val rewardItems = setOf("dice_pearl_paw", "board_blossom_garden", "character_rabbit")

    fun canShowAutomatically(eligible: Boolean, state: String, history: ReferralPopupHistory, now: Long, shownThisSession: Boolean): Boolean =
        eligible && state == "INVITE" && !shownThisSession && now > 0 && history.impressions in 0..2 &&
            history.shownAt in 0..now && history.dismissedAt in 0..now &&
            (history.shownAt == 0L || now - history.shownAt >= DAY_MS) &&
            (history.dismissedAt == 0L || now - history.dismissedAt >= 3 * DAY_MS)
}

/** Process-local opportunity/suppression signals; no accounts, views or provider tokens. */
internal class ReferralPopupSession {
    private val completed = linkedSetOf<String>()
    private var pendingMatch = false
    private var fullscreenAt: Long? = null
    var automaticShown = false
        private set

    @Synchronized fun completedMatch(matchId: String) {
        if (matchId.isBlank() || !completed.add(matchId)) return
        while (completed.size > 64) completed.remove(completed.first())
        pendingMatch = true
    }

    @Synchronized fun takeHomeOpportunity(): Boolean = pendingMatch.also { pendingMatch = false }
    @Synchronized fun markAutomaticShown() { automaticShown = true }
    @Synchronized fun fullscreenInteraction(now: Long = System.nanoTime()) { fullscreenAt = now }
    @Synchronized fun fullscreenRecently(now: Long = System.nanoTime()): Boolean =
        fullscreenAt?.let { now - it in 0 until 60_000_000_000L } ?: false

    companion object { val current = ReferralPopupSession() }
}
