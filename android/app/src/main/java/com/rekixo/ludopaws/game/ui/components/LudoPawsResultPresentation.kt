package com.rekixo.ludopaws

import android.content.Context
import com.rekixo.ludopaws.feature.profile.presentation.MatchProgressSummary

/** Rank and awards are read from saved/authoritative sources, never minted here. */
object LudoPawsResultPresentation {
    fun model(
        context: Context,
        match: MatchSnapshot,
        mode: GameMode,
        viewerId: String?,
        gemLedgerStatus: String? = null,
        creditedGems: Int = 0,
    ): ResultCardModel {
        val teamMode = mode == GameMode.TEAM_UP
        val won = if (teamMode) {
            val player = match.players.firstOrNull { it.playerId == viewerId }
            match.winnerTeamId != null && player?.teamId == match.winnerTeamId
        } else viewerId != null && viewerId == match.winnerPlayerId
        val ranks = if (teamMode) emptyMap() else LudoPawsFinishPresentation.ranks(match)
        val myRank = viewerId?.let(ranks::get)
        val title = when {
            teamMode && won -> "YOUR TEAM WON"
            teamMode -> "TEAM ${match.winnerTeamId ?: "—"} WINS"
            mode == GameMode.PASS_AND_PLAY -> "MATCH COMPLETE"
            myRank == 1 -> "1ST PLACE"
            myRank == 2 -> "2ND PLACE"
            myRank == 3 -> "3RD PLACE"
            myRank == 4 -> "4TH PLACE"
            else -> "MATCH COMPLETE"
        }
        val subtitle = when {
            teamMode && won -> "A brilliant team victory!"
            teamMode -> "Well played, team!"
            myRank == 1 -> "Champion! Fantastic game."
            myRank == 2 -> "Great game!"
            myRank == 3 -> "Well played!"
            myRank == 4 -> "Keep playing, more wins ahead!"
            else -> "Thanks for playing!"
        }
        val placements: List<ResultPlace> = if (teamMode) {
            listOf("A", "B").sortedBy { if (it == match.winnerTeamId) 0 else 1 }
                .map { team ->
                    ResultPlace(
                        if (team == match.winnerTeamId) "WIN" else "TEAM",
                        match.players.filter { it.teamId == team }.joinToString(" + ") { it.displayName }
                            .ifBlank { "Team $team" },
                        match.players.any { it.playerId == viewerId && it.teamId == team },
                    )
                }
        } else {
            match.players.map { player ->
                val rank = ranks[player.playerId]
                    ?: match.finishOrderPlayerIds.indexOf(player.playerId).takeIf { it >= 0 }?.plus(1)
                Triple(player, rank, player.playerId == viewerId)
            }.sortedWith(compareBy<Triple<PlayerSnapshot, Int?, Boolean>> { it.second ?: 99 }
                .thenBy { it.first.seat }).map { (player, rank, isYou) ->
                ResultPlace(
                    rank?.let { "${it}${ordinal(it)}" } ?: "—",
                    player.displayName,
                    isYou && mode != GameMode.PASS_AND_PLAY,
                )
            }
        }
        val xpEligible = mode == GameMode.COMPUTER || mode == GameMode.PASS_AND_PLAY ||
            mode == GameMode.ONLINE
        val xp = MatchProgressSummary.card(
            context, match.matchId, won, eligible = xpEligible && viewerId != null,
        )
        val planned = viewerId?.let(match.matchGemRewards::get)?.takeIf { it > 0 }
        val gems = when {
            planned == null -> null
            gemLedgerStatus == "CREDITED" && creditedGems == planned ->
                "+$creditedGems Gems · Credited" to "Confirmed in your Wallet"
            gemLedgerStatus == "LIMIT_REACHED" ->
                "Reward limit reached" to "Friends Gems are limited to 3 rewards per 24 hours"
            else -> "+$planned Gems · Verifying" to
                "Pending Wallet confirmation · no Gems credited on this screen"
        }
        return ResultCardModel(
            title = title, subtitle = subtitle, placements = placements,
            xpTitle = xp?.title, xpDetail = xp?.detail, xpFraction = xp?.fraction,
            gemsTitle = gems?.first, gemsDetail = gems?.second,
            gemsCredited = gems?.first?.contains("Credited") == true,
        )
    }

    private fun ordinal(rank: Int): String = when (rank) {
        1 -> "ST"
        2 -> "ND"
        3 -> "RD"
        else -> "TH"
    }
}
