package com.rekixo.ludopaws.feature.online

import com.rekixo.ludopaws.LudoTurnAutomationPolicy
import com.rekixo.ludopaws.MatchSnapshot

internal data class OnlineAutoMoveDecision(
    val eventKey: String,
    val tokenIndex: Int,
)

internal object OnlineAutoMovePolicy {
    private val autoMoveRulesets = setOf(
        "ludoproof-standard-v3",
        "ludoproof-team-v3",
        "ludoproof-standard-v4",
        "ludoproof-team-v4",
    )

    fun resolve(
        state: MatchSnapshot,
        localPlayerId: String?,
        isOnline: Boolean,
        hasAuthoritativeState: Boolean,
    ): OnlineAutoMoveDecision? {
        if (!isOnline || !hasAuthoritativeState || state.status != "ACTIVE") return null
        if (state.rulesetId !in autoMoveRulesets) return null
        val pending = state.pendingRoll ?: return null
        if (pending.status != "RESOLVED") return null
        val id = localPlayerId?.takeIf { it.isNotBlank() } ?: return null
        val localPlayer = state.players.firstOrNull { it.playerId == id } ?: return null
        val actingSeat = state.actingSeat ?: state.turnSeat
        if (localPlayer.seat != actingSeat || pending.seat != localPlayer.seat) return null
        // Use one scheduler for all automatic moves. Previously the single-legal
        // path lived in the UI renderer, where a failed move could leave its
        // event tag stuck and prevent any subsequent retry.
        val outcome = pending.outcome?.takeIf { it in 1..6 } ?: return null
        val tokenIndex = LudoTurnAutomationPolicy.singleLegalTokenIndex(
            pending.legalTokenIndexes,
        ) ?: LudoTurnAutomationPolicy.singleAutomaticTokenIndex(
            tokens = localPlayer.tokens,
            roll = outcome,
            legalTokenIndexes = pending.legalTokenIndexes,
        ) ?: return null
        return OnlineAutoMoveDecision(
            eventKey = "${state.matchId}:auto:${pending.eventIndex}:${tokenIndex}",
            tokenIndex = tokenIndex,
        )
    }
}
