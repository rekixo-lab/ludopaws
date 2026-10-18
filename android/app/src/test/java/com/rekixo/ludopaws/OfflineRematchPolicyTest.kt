package com.rekixo.ludopaws

import com.rekixo.ludopaws.ui.offline.gameplay.OfflineRematchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineRematchPolicyTest {
    @Test
    fun finishedMatchKeepsPlayerCountAndPerspectiveColor() {
        val state =
            snapshot(
                status = "FINISHED",
                colors = listOf("BLUE", "YELLOW", "RED", "GREEN"),
            )

        val spec = OfflineRematchPolicy.fromFinishedState(state)

        assertEquals(4, spec?.playerCount)
        assertEquals("BLUE", spec?.preferredColor)
    }

    @Test
    fun activeMatchDoesNotCreateRematchSpec() {
        assertNull(
            OfflineRematchPolicy.fromFinishedState(
                snapshot(
                    status = "ACTIVE",
                    colors = listOf("RED", "YELLOW"),
                ),
            ),
        )
    }

    private fun snapshot(
        status: String,
        colors: List<String>,
    ): MatchSnapshot =
        MatchSnapshot(
            matchId = "offline-rematch-test",
            status = status,
            hostPlayerId = "p1",
            targetPlayerCount = colors.size,
            matchMode = "COMPUTER",
            players =
                colors.mapIndexed { index, color ->
                    PlayerSnapshot(
                        playerId = "p${index + 1}",
                        displayName = "Player ${index + 1}",
                        color = color,
                        seat = index,
                        tokens = listOf(57, 57, 57, 57),
                    )
                },
            turnSeat = 0,
            randomEventIndex = 10,
            pendingRoll = null,
            winnerPlayerId = "p1",
            rulesetId = ClassicRuleset.V3_ID,
            history = emptyList(),
        )
}
