package com.rekixo.ludopaws

import com.rekixo.ludopaws.ui.offline.gameplay.OfflinePassAndPlayHandoffPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflinePassAndPlayHandoffPolicyTest {
    @Test
    fun turnChangeInPassAndPlayRequiresHandoff() {
        val previous = snapshot(turnSeat = 0)
        val current = snapshot(turnSeat = 1)

        val handoff =
            OfflinePassAndPlayHandoffPolicy.resolve(
                mode = GameMode.PASS_AND_PLAY,
                previous = previous,
                current = current,
            )

        assertEquals("p2", handoff?.playerId)
        assertEquals("Player 2", handoff?.displayName)
        assertEquals("YELLOW", handoff?.color)
        assertEquals(1, handoff?.seat)
    }

    @Test
    fun extraTurnDoesNotRequireHandoff() {
        assertNull(
            OfflinePassAndPlayHandoffPolicy.resolve(
                mode = GameMode.PASS_AND_PLAY,
                previous = snapshot(turnSeat = 0),
                current = snapshot(turnSeat = 0),
            ),
        )
    }

    @Test
    fun computerModeNeverRequiresSameDeviceHandoff() {
        assertNull(
            OfflinePassAndPlayHandoffPolicy.resolve(
                mode = GameMode.COMPUTER,
                previous = snapshot(turnSeat = 0),
                current = snapshot(turnSeat = 1),
            ),
        )
    }

    @Test
    fun initialOrResumeRenderDoesNotCreateHandoff() {
        assertNull(
            OfflinePassAndPlayHandoffPolicy.resolve(
                mode = GameMode.PASS_AND_PLAY,
                previous = null,
                current = snapshot(turnSeat = 1),
            ),
        )
    }

    @Test
    fun finishedMatchDoesNotCreateHandoff() {
        assertNull(
            OfflinePassAndPlayHandoffPolicy.resolve(
                mode = GameMode.PASS_AND_PLAY,
                previous = snapshot(turnSeat = 0),
                current = snapshot(turnSeat = 1, status = "FINISHED"),
            ),
        )
    }

    private fun snapshot(
        turnSeat: Int,
        status: String = "ACTIVE",
    ): MatchSnapshot =
        MatchSnapshot(
            matchId = "offline-handoff-test",
            status = status,
            hostPlayerId = "p1",
            targetPlayerCount = 2,
            matchMode = "OFFLINE",
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
            randomEventIndex = 0,
            pendingRoll = null,
            winnerPlayerId = if (status == "FINISHED") "p1" else null,
            rulesetId = NativeDiceEngine.RULESET_ID,
            history = emptyList(),
        )
}
