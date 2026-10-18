package com.rekixo.ludopaws

import com.rekixo.ludopaws.ui.offline.gameplay.OfflineTurnTransitionFeedbackPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineTurnTransitionFeedbackPolicyTest {
    @Test
    fun nonSixWithoutLegalMoveExplainsTurnPass() {
        val previous = snapshot(turnSeat = 0, randomEventIndex = 7)
        val current =
            snapshot(
                turnSeat = 1,
                randomEventIndex = 8,
                history = listOf(rollEvent(eventIndex = 7, outcome = 4)),
            )

        assertEquals(
            "Player 1 rolled 4 • no legal move • Player 2's turn",
            OfflineTurnTransitionFeedbackPolicy.message(previous, current),
        )
    }

    @Test
    fun thirdSixExplainsForfeitedTurn() {
        val previous = snapshot(turnSeat = 0, randomEventIndex = 11)
        val current =
            snapshot(
                turnSeat = 1,
                randomEventIndex = 12,
                history = listOf(rollEvent(eventIndex = 11, outcome = 6)),
            )

        assertEquals(
            "Player 1 rolled a third 6 • turn forfeited • Player 2's turn",
            OfflineTurnTransitionFeedbackPolicy.message(previous, current),
        )
    }

    @Test
    fun sixWithoutLegalMoveExplainsExtraRollWhenTurnStays() {
        val previous = snapshot(turnSeat = 0, randomEventIndex = 3)
        val current =
            snapshot(
                turnSeat = 0,
                randomEventIndex = 4,
                history = listOf(rollEvent(eventIndex = 3, outcome = 6)),
            )

        assertEquals(
            "Player 1 rolled 6 • no legal move • roll again",
            OfflineTurnTransitionFeedbackPolicy.message(previous, current),
        )
    }

    @Test
    fun completedMoveDoesNotProduceAutomaticTurnMessage() {
        val previous = snapshot(turnSeat = 0, randomEventIndex = 5)
        val current =
            snapshot(
                turnSeat = 1,
                randomEventIndex = 6,
                history =
                    listOf(
                        rollEvent(
                            eventIndex = 5,
                            outcome = 3,
                            moveTokenIndex = 0,
                        ),
                    ),
            )

        assertNull(
            OfflineTurnTransitionFeedbackPolicy.message(previous, current),
        )
    }

    @Test
    fun savedSnapshotWithoutFreshPreviousStateDoesNotReplayOldMessage() {
        val current =
            snapshot(
                turnSeat = 1,
                randomEventIndex = 9,
                history = listOf(rollEvent(eventIndex = 8, outcome = 2)),
            )

        assertNull(
            OfflineTurnTransitionFeedbackPolicy.message(null, current),
        )
    }

    private fun snapshot(
        turnSeat: Int,
        randomEventIndex: Int,
        history: List<HistoryEventSnapshot> = emptyList(),
    ): MatchSnapshot =
        MatchSnapshot(
            matchId = "offline-test-match",
            status = "ACTIVE",
            hostPlayerId = "p1",
            targetPlayerCount = 2,
            matchMode = "COMPUTER",
            players =
                listOf(
                    PlayerSnapshot(
                        playerId = "p1",
                        displayName = "Player 1",
                        color = "RED",
                        seat = 0,
                        tokens = listOf(-1, -1, -1, -1),
                    ),
                    PlayerSnapshot(
                        playerId = "p2",
                        displayName = "Player 2",
                        color = "YELLOW",
                        seat = 1,
                        tokens = listOf(-1, -1, -1, -1),
                    ),
                ),
            turnSeat = turnSeat,
            randomEventIndex = randomEventIndex,
            pendingRoll = null,
            winnerPlayerId = null,
            rulesetId = NativeDiceEngine.RULESET_ID,
            history = history,
        )

    private fun rollEvent(
        eventIndex: Int,
        outcome: Int,
        moveTokenIndex: Int? = null,
    ): HistoryEventSnapshot =
        HistoryEventSnapshot(
            eventIndex = eventIndex,
            playerId = "p1",
            roundId = "round-$eventIndex",
            proofDigest = "proof-$eventIndex",
            outcome = outcome,
            moveTokenIndex = moveTokenIndex,
            captures = 0,
            effectiveOutcome = outcome,
            openingRollApplied = false,
        )
}
