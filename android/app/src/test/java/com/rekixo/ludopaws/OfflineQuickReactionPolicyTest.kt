package com.rekixo.ludopaws

import com.rekixo.ludopaws.ui.offline.gameplay.OfflineQuickReactionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineQuickReactionPolicyTest {
    @Test
    fun usesActualActivePlayerNotFirstPlayer() {
        val reaction =
            OfflineQuickReactionPolicy.resolve(
                state = snapshot(turnSeat = 1),
                emoji = "😂",
                nowMs = 2_000L,
                lastShownAtMs = null,
            )

        assertEquals("p2", reaction?.playerId)
        assertEquals("Player 2", reaction?.displayName)
        assertEquals(1, reaction?.seat)
        assertEquals("😂", reaction?.emoji)
    }

    @Test
    fun throttlesRapidRepeatedTaps() {
        assertNull(
            OfflineQuickReactionPolicy.resolve(
                state = snapshot(turnSeat = 0),
                emoji = "👍",
                nowMs = 1_500L,
                lastShownAtMs = 1_000L,
            ),
        )
    }

    @Test
    fun finishedMatchAndUnsupportedEmojiAreRejected() {
        assertNull(
            OfflineQuickReactionPolicy.resolve(
                state = snapshot(turnSeat = 0, status = "FINISHED"),
                emoji = "👍",
                nowMs = 2_000L,
                lastShownAtMs = null,
            ),
        )
        assertNull(
            OfflineQuickReactionPolicy.resolve(
                state = snapshot(turnSeat = 0),
                emoji = "🧿",
                nowMs = 2_000L,
                lastShownAtMs = null,
            ),
        )
    }

    @Test
    fun selectedProfileIsTheQuickChatSenderAndAllTwentyFourEmojisAreSupported() {
        val chosen =
            OfflineQuickReactionPolicy.resolve(
                state = snapshot(turnSeat = 0),
                emoji = "🔥",
                nowMs = 2_000L,
                lastShownAtMs = null,
                senderPlayerId = "p2",
            )

        assertEquals("p2", chosen?.playerId)
        assertEquals("Player 2", chosen?.displayName)
        assertEquals(1, chosen?.seat)
        assertEquals(24, com.rekixo.ludopaws.ui.quickchat.QuickChatEmojiCatalog.EMOJIS.size)
        assertEquals(
            24,
            com.rekixo.ludopaws.ui.quickchat.QuickChatEmojiCatalog.EMOJIS.distinct().size,
        )
        assertTrue(
            com.rekixo.ludopaws.ui.quickchat.QuickChatEmojiCatalog.EMOJIS.containsAll(
                listOf("👍", "😂", "😮", "😭", "😠", "🥳", "🤦", "😈", "🔥", "💪", "🤡", "👑"),
            ),
        )
    }

    private fun snapshot(
        turnSeat: Int,
        status: String = "ACTIVE",
    ): MatchSnapshot =
        MatchSnapshot(
            matchId = "quick-reaction-test",
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
