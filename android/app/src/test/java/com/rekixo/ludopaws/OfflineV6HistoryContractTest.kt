package com.rekixo.ludopaws

import org.junit.Assert.*
import org.junit.Test

class OfflineV6HistoryContractTest {
    @Test fun newHistoryWindowHasItsOwnRulesetDigestWithoutChangingLegacyBinding() {
        fun derive(id: String) = OfflineLudoV3Binding.deriveRoll(
            matchId = "history-contract", status = "ACTIVE",
            players = listOf(OfflineV3BindingPlayer("a", "RED", listOf(-1,-1,-1,-1)), OfflineV3BindingPlayer("b", "GREEN", listOf(-1,-1,-1,-1))),
            turnSeat = 0, eventIndex = 0, consecutiveSixes = listOf(0,0),
            openingRollConsumed = listOf(false,false), winnerPlayerId = null, history = emptyList(),
            rulesetId = id, opponentStackCapture = OfflineLudoV5Binding.OPPONENT_STACK_CAPTURE,
        )
        val legacy = derive(OfflineLudoV5Binding.RULESET_ID)
        val bounded = derive(OfflineLudoV6Binding.RULESET_ID)
        assertNotEquals(legacy.config.context.metadataDigest, bounded.config.context.metadataDigest)
        assertEquals(legacy.config.context.previousStateHash, bounded.config.context.previousStateHash)
        assertTrue(EntroNexV4Local.verify(bounded))
        assertTrue(EntroNexV4Local.verify(legacy))
    }
}
