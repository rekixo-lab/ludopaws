package com.rekixo.ludopaws.feature.store.domain

/**
 * Completed-match placement for every mode, independent of who won.
 * Active-match exits and unfinished games never qualify.
 */
@Suppress("UNUSED_PARAMETER") // Keep call-site compatibility; win/loss no longer affects eligibility.
internal object PostWinInterstitialPolicy {
    /**
     * Remove Ads suppresses unsolicited interstitial placements.
     * Explicit user-requested rewarded ads are handled independently.
     */
    fun shouldShowFillerInterstitial(
        adsRemoved: Boolean,
    ): Boolean = !adsRemoved

    fun shouldShowOffline(
        matchStatus: String?,
        winnerPlayerId: String?,
        humanPlayerId: String?,
        isComputerMode: Boolean,
    ): Boolean {
        if (matchStatus != FINISHED_STATUS || winnerPlayerId.isNullOrBlank()) return false
        return true
    }

    fun shouldShowRemote(
        matchStatus: String?,
        teamBased: Boolean,
        winnerPlayerId: String?,
        playerId: String?,
        winnerTeamId: String?,
        playerTeamId: String?,
    ): Boolean {
        if (matchStatus != FINISHED_STATUS || playerId.isNullOrBlank()) return false
        return if (teamBased) {
            winnerTeamId in setOf("A", "B") && playerTeamId in setOf("A", "B")
        } else {
            !winnerPlayerId.isNullOrBlank()
        }
    }

    private const val FINISHED_STATUS = "FINISHED"
}
