package com.rekixo.ludopaws.ui.offline.state

import com.rekixo.ludopaws.GameMode
import com.rekixo.ludopaws.feature.offline.data.local.ActiveOfflineCharacterSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCharacterAssignmentPolicyTest {
    @Test
    fun `matching pass and play assignment is preserved`() {
        val active =
            ActiveOfflineCharacterSetup(
                mode = GameMode.PASS_AND_PLAY,
                playerCount = 4,
                preferredColor = "BLUE",
                characterIds = listOf("dog", "goat", "duck", "cat"),
            )

        val resolved =
            OfflineCharacterAssignmentPolicy.resolve(
                mode = GameMode.PASS_AND_PLAY,
                playerCount = 4,
                preferredCharacterId = "dog",
                active = active,
            )

        assertEquals(active.characterIds, resolved)
    }

    @Test
    fun `legacy saved presentation state is migrated deterministically`() {
        val legacy =
            ActiveOfflineCharacterSetup(
                mode = GameMode.PASS_AND_PLAY,
                playerCount = 4,
                preferredColor = "BLUE",
                characterIds = listOf("squirrel", "hedgehog", "duck", "sheep"),
            )

        val resolved =
            OfflineCharacterAssignmentPolicy.resolve(
                mode = GameMode.PASS_AND_PLAY,
                playerCount = 4,
                preferredCharacterId = "squirrel",
                active = legacy,
            )

        assertEquals(listOf("dog", "goat", "duck", "cat"), resolved)
    }

    @Test
    fun `mismatched saved presentation state is repaired deterministically`() {
        val wrongMode =
            ActiveOfflineCharacterSetup(
                mode = GameMode.COMPUTER,
                playerCount = 2,
                preferredColor = "BLUE",
                characterIds = listOf("dog", "goat"),
            )

        val resolved =
            OfflineCharacterAssignmentPolicy.resolve(
                mode = GameMode.PASS_AND_PLAY,
                playerCount = 3,
                preferredCharacterId = "duck",
                active = wrongMode,
            )

        assertEquals(3, resolved.size)
        assertEquals("duck", resolved.first())
        assertEquals(resolved.size, resolved.toSet().size)
    }

    @Test
    fun `computer resume preserves individually chosen robot opponents`() {
        val active = ActiveOfflineCharacterSetup(
            mode = GameMode.COMPUTER,
            playerCount = 4,
            preferredColor = "BLUE",
            characterIds = listOf("dog", "horse", "rabbit", "cat"),
        )
        val resolved = OfflineCharacterAssignmentPolicy.resolve(
            mode = GameMode.COMPUTER,
            playerCount = 4,
            preferredCharacterId = "dog",
            active = active,
            unlockedCharacterIds = setOf("dog", "goat", "cat"),
        )
        assertEquals(active.characterIds, resolved)
    }

    @Test
    fun `computer resume keeps human preference and unique cpu characters`() {
        val resolved =
            OfflineCharacterAssignmentPolicy.resolve(
                mode = GameMode.COMPUTER,
                playerCount = 4,
                preferredCharacterId = "cat",
                active = null,
            )

        assertEquals("cat", resolved.first())
        assertEquals(4, resolved.size)
        assertEquals(resolved.size, resolved.toSet().size)
        assertTrue("dog" in resolved)
    }
}
