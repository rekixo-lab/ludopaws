package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.online.OnlineAutoMovePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineAutoMovePolicyTest {
    @Test
    fun schedulesOnlyForTheAuthoritativeActingPlayerWithOneEffectiveDestination() {
        val state = snapshot(
            localSeat = 0,
            tokens = listOf(8, 8, -1, -1),
            roll = 2,
            legal = setOf(0, 1),
        )
        val decision = OnlineAutoMovePolicy.resolve(state, "p0", true, true)
        requireNotNull(decision)
        assertEquals(0, decision.tokenIndex)
        assertEquals("LPV4JSON23:auto:7:0", decision.eventKey)
    }

    @Test
    fun oneLegalTokenUsesTheSameAutomaticMoveScheduler() {
        val state = snapshot(
            localSeat = 0,
            tokens = listOf(8, -1, -1, -1),
            roll = 2,
            legal = setOf(0),
        )
        val decision = OnlineAutoMovePolicy.resolve(state, "p0", true, true)
        requireNotNull(decision)
        assertEquals(0, decision.tokenIndex)
        assertEquals("LPV4JSON23:auto:7:0", decision.eventKey)
    }

    @Test
    fun cachedOfflineOpponentAndUnsupportedRulesetCannotTriggerAutoMove() {
        val state = snapshot(
            localSeat = 0,
            tokens = listOf(8, 8, -1, -1),
            roll = 2,
            legal = setOf(0, 1),
        )
        assertNull(OnlineAutoMovePolicy.resolve(state, "p0", true, false))
        assertNull(OnlineAutoMovePolicy.resolve(state, "p0", false, true))
        assertNull(OnlineAutoMovePolicy.resolve(state, "p1", true, true))
        assertNull(
            OnlineAutoMovePolicy.resolve(
                state.copy(rulesetId = "ludoproof-standard-v2"),
                "p0",
                true,
                true,
            ),
        )
    }

    @Test
    fun multipleDistinctLegalDestinationsRequireManualSelection() {
        val state = snapshot(
            localSeat = 0,
            tokens = listOf(20, 25, -1, -1),
            roll = 3,
            legal = setOf(0, 1),
        )
        assertNull(OnlineAutoMovePolicy.resolve(state, "p0", true, true))
    }

    @Test
    fun teamPartnerCannotAutoMoveWhenAnotherSeatIsActing() {
        val state = snapshot(
            localSeat = 0,
            actingSeat = 2,
            mode = "TEAM_UP",
            rulesetId = "ludoproof-team-v4",
            tokens = listOf(8, 8, -1, -1),
            roll = 2,
            legal = setOf(0, 1),
        )
        assertNull(OnlineAutoMovePolicy.resolve(state, "p0", true, true))
    }

    private fun snapshot(
        localSeat: Int,
        tokens: List<Int>,
        roll: Int,
        legal: Set<Int>,
        actingSeat: Int? = null,
        mode: String = "ONLINE",
        rulesetId: String = "ludoproof-standard-v4",
    ) = MatchSnapshot(
        matchId = "LPV4JSON23",
        status = "ACTIVE",
        hostPlayerId = "p0",
        targetPlayerCount = if (mode == "TEAM_UP") 4 else 2,
        matchMode = mode,
        players = listOf(
            player("p0", 0, tokens, if (mode == "TEAM_UP") "A" else null),
            player("p1", 1, listOf(-1, -1, -1, -1), if (mode == "TEAM_UP") "B" else null),
            player("p2", 2, listOf(-1, -1, -1, -1), if (mode == "TEAM_UP") "A" else null),
            player("p3", 3, listOf(-1, -1, -1, -1), if (mode == "TEAM_UP") "B" else null),
        ).take(if (mode == "TEAM_UP") 4 else 2),
        turnSeat = actingSeat ?: localSeat,
        randomEventIndex = 8,
        pendingRoll = PendingRollSnapshot(
            status = "RESOLVED",
            seat = localSeat,
            eventIndex = 7,
            eventId = "roll:7",
            roundId = "round-7",
            serverCommitment = "server",
            clientCommitment = "client",
            revealDeadlineAt = null,
            proofDigest = "proof",
            outcome = roll,
            legalTokenIndexes = legal,
        ),
        winnerPlayerId = null,
        rulesetId = rulesetId,
        history = emptyList(),
        actingSeat = actingSeat,
        teamAssignments = if (mode == "TEAM_UP") listOf("A", "B", "A", "B") else emptyList(),
    )

    private fun player(id: String, seat: Int, tokens: List<Int>, teamId: String?) =
        PlayerSnapshot(
            playerId = id,
            displayName = id,
            color = listOf("RED", "GREEN", "YELLOW", "BLUE")[seat],
            seat = seat,
            tokens = tokens,
            teamId = teamId,
        )
}
