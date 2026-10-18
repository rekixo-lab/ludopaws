package com.rekixo.ludopaws

import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class OfflineMatchInvariantPolicyTest {
    @Test
    fun `valid resolved local turn passes invariant validation`() {
        val state = activeResolvedState()

        assertSame(
            state,
            OfflineMatchInvariantPolicy.requireValid(state),
        )
    }

    @Test
    fun `pending legal set must exactly match canonical path legality`() {
        val state =
            activeResolvedState().copy(
                pendingRoll =
                    requireNotNull(activeResolvedState().pendingRoll)
                        .copy(
                            legalTokenIndexes = setOf(1),
                        ),
            )

        assertInvalid(state)
    }

    @Test
    fun `pending proof must match the same history event`() {
        val state =
            activeResolvedState().copy(
                pendingRoll =
                    requireNotNull(activeResolvedState().pendingRoll)
                        .copy(
                            proofDigest = "different-proof",
                        ),
            )

        assertInvalid(state)
    }

    @Test
    fun `finished match requires all winner tokens at exact home`() {
        val state =
            finishedState(
                winnerTokens = listOf(57, 57, 57, 56),
            )

        assertInvalid(state)
    }

    @Test
    fun `finished match with four home tokens is valid`() {
        val state =
            finishedState(
                winnerTokens = listOf(57, 57, 57, 57),
            )

        assertSame(
            state,
            OfflineMatchInvariantPolicy.requireValid(state),
        )
    }

    @Test
    fun `history cannot reference a future random event`() {
        val base = activeResolvedState()
        val state =
            base.copy(
                randomEventIndex = 0,
                pendingRoll = null,
            )

        assertInvalid(state)
    }

    private fun activeResolvedState(): MatchSnapshot {
        val proof = "proof-event-0"
        return MatchSnapshot(
            matchId = "offline-test",
            status = "ACTIVE",
            hostPlayerId = "p0",
            players =
                listOf(
                    player(
                        id = "p0",
                        color = "RED",
                        seat = 0,
                        tokens = listOf(0, -1, -1, -1),
                    ),
                    player(
                        id = "p1",
                        color = "YELLOW",
                        seat = 1,
                        tokens = listOf(-1, -1, -1, -1),
                    ),
                ),
            turnSeat = 0,
            randomEventIndex = 1,
            pendingRoll =
                PendingRollSnapshot(
                    status = "RESOLVED",
                    seat = 0,
                    eventIndex = 0,
                    eventId = "roll:0",
                    roundId = "round-0",
                    serverCommitment = "server",
                    clientCommitment = "client",
                    revealDeadlineAt = null,
                    proofDigest = proof,
                    outcome = 2,
                    legalTokenIndexes = setOf(0),
                ),
            winnerPlayerId = null,
            rulesetId = ClassicRuleset.V3_ID,
            history =
                listOf(
                    HistoryEventSnapshot(
                        eventIndex = 0,
                        playerId = "p0",
                        roundId = "round-0",
                        proofDigest = proof,
                        outcome = 2,
                        moveTokenIndex = null,
                        captures = 0,
                        randomOutcome = 2,
                        effectiveOutcome = 2,
                    ),
                ),
        )
    }

    private fun finishedState(
        winnerTokens: List<Int>,
    ): MatchSnapshot =
        MatchSnapshot(
            matchId = "offline-finished",
            status = "FINISHED",
            hostPlayerId = "p0",
            players =
                listOf(
                    player(
                        id = "p0",
                        color = "RED",
                        seat = 0,
                        tokens = winnerTokens,
                    ),
                    player(
                        id = "p1",
                        color = "YELLOW",
                        seat = 1,
                        tokens = listOf(3, -1, -1, -1),
                    ),
                ),
            turnSeat = 0,
            randomEventIndex = 1,
            pendingRoll = null,
            winnerPlayerId = "p0",
            rulesetId = ClassicRuleset.V3_ID,
            history =
                listOf(
                    HistoryEventSnapshot(
                        eventIndex = 0,
                        playerId = "p0",
                        roundId = "round-0",
                        proofDigest = "proof-event-0",
                        outcome = 1,
                        moveTokenIndex = 0,
                        captures = 0,
                        randomOutcome = 1,
                        effectiveOutcome = 1,
                    ),
                ),
        )

    private fun player(
        id: String,
        color: String,
        seat: Int,
        tokens: List<Int>,
    ): PlayerSnapshot =
        PlayerSnapshot(
            playerId = id,
            displayName = id,
            color = color,
            seat = seat,
            tokens = tokens,
        )

    private fun assertInvalid(
        state: MatchSnapshot,
    ) {
        try {
            OfflineMatchInvariantPolicy.requireValid(state)
            fail("Expected local match invariant validation to fail")
        } catch (_: IllegalArgumentException) {
            // Expected: invariant policies fail closed with require/requireNotNull.
        }
    }
}
