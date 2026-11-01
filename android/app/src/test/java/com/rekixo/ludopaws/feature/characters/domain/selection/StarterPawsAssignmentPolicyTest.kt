package com.rekixo.ludopaws.feature.characters.domain.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterPawsAssignmentPolicyTest {
    @Test
    fun `computer mode keeps human preference first and assigns unique cpus`() {
        val assigned =
            StarterPawsAssignmentPolicy.normalize(
                playerCount = 4,
                requestedCharacterIds = emptyList(),
                preferredCharacterId = "cat",
                computerMode = true,
            )

        assertEquals(
            listOf("cat", "dog", "goat", "duck"),
            assigned,
        )
        assertEquals(4, assigned.distinct().size)
    }

    @Test
    fun `pass and play defaults to unique production animals`() {
        val assigned =
            StarterPawsAssignmentPolicy.normalize(
                playerCount = 4,
                requestedCharacterIds = emptyList(),
                preferredCharacterId = "dog",
                computerMode = false,
            )

        assertEquals(
            listOf("dog", "goat", "duck", "cat"),
            assigned,
        )
        assertEquals(4, assigned.distinct().size)
    }

    @Test
    fun `selecting an animal owned by another local player swaps slots`() {
        val result =
            StarterPawsAssignmentPolicy.select(
                playerCount = 4,
                currentCharacterIds = listOf("dog", "goat", "duck", "cat"),
                selectedSlot = 2,
                requestedCharacterId = "dog",
                computerMode = false,
            )

        assertEquals(
            listOf("duck", "goat", "dog", "cat"),
            result,
        )
        assertEquals(4, result.distinct().size)
    }

    @Test
    fun `legacy ids are normalized before assignment`() {
        val result =
            StarterPawsAssignmentPolicy.normalize(
                playerCount = 4,
                requestedCharacterIds = listOf("squirrel", "hedgehog", "duck", "sheep"),
                preferredCharacterId = "squirrel",
                computerMode = false,
            )

        assertEquals(listOf("dog", "goat", "duck", "cat"), result)
    }

    @Test
    fun `invalid character request repairs to safe production assignment`() {
        val result =
            StarterPawsAssignmentPolicy.select(
                playerCount = 3,
                currentCharacterIds = listOf("dog", "goat", "duck"),
                selectedSlot = 1,
                requestedCharacterId = "not_a_character",
                computerMode = false,
            )

        assertEquals(listOf("dog", "goat", "duck"), result)
        assertTrue(
            result.all(StarterPawsAssignmentPolicy.starterCharacterIds::contains),
        )
    }

    @Test
    fun `changing player count remains deterministic and unique`() {
        val twoPlayers =
            StarterPawsAssignmentPolicy.normalize(
                playerCount = 2,
                requestedCharacterIds = listOf("cat", "dog", "goat", "duck"),
                preferredCharacterId = "cat",
                computerMode = false,
            )
        val fourPlayers =
            StarterPawsAssignmentPolicy.normalize(
                playerCount = 4,
                requestedCharacterIds = twoPlayers,
                preferredCharacterId = "cat",
                computerMode = false,
            )

        assertEquals(listOf("cat", "dog"), twoPlayers)
        assertEquals(4, fourPlayers.size)
        assertEquals(4, fourPlayers.distinct().size)
        assertEquals("cat", fourPlayers.first())
    }

    @Test
    fun `robot players can choose distinct opponent paws independently`() {
        val current = listOf("dog", "goat", "duck", "cat")
        val cpuChoice = StarterPawsAssignmentPolicy.select(
            playerCount = 4,
            currentCharacterIds = current,
            selectedSlot = 2,
            requestedCharacterId = "horse",
            computerMode = true,
            unlockedCharacterIds = setOf("dog", "goat", "duck", "cat"),
        )

        assertEquals(listOf("dog", "goat", "horse", "cat"), cpuChoice)
        assertEquals(cpuChoice, StarterPawsAssignmentPolicy.normalize(
            4, cpuChoice, "dog", true, setOf("dog", "goat", "duck", "cat"),
        ))
        assertEquals(cpuChoice.size, cpuChoice.toSet().size)
    }

    @Test
    fun `robot cpu to cpu swaps keep human choice stable`() {
        val current = listOf("dog", "goat", "duck", "cat")
        val swapped = StarterPawsAssignmentPolicy.select(
            playerCount = 4,
            currentCharacterIds = current,
            selectedSlot = 2,
            requestedCharacterId = "goat",
            computerMode = true,
            unlockedCharacterIds = setOf("dog"),
        )
        assertEquals(listOf("dog", "duck", "goat", "cat"), swapped)
    }

    @Test
    fun `robot cpu cannot replace the human paw or unlock it`() {
        val unlocked = setOf("dog", "goat")
        val current = listOf("dog", "horse", "duck", "cat")
        assertEquals(current, StarterPawsAssignmentPolicy.select(
            4, current, 1, "dog", true, unlocked,
        ))
        assertEquals(current, StarterPawsAssignmentPolicy.select(
            4, current, 0, "horse", true, unlocked,
        ))
        assertEquals("dog", StarterPawsAssignmentPolicy.normalize(
            4, listOf("horse", "dog", "goat", "cat"), "horse", true, unlocked,
        ).first())
    }

    @Test
    fun `human may swap with a cpu using an owned paw`() {
        val current = listOf("dog", "goat", "duck", "cat")
        val swapped = StarterPawsAssignmentPolicy.select(
            4, current, 0, "cat", true, setOf("dog", "cat"),
        )
        assertEquals(listOf("cat", "goat", "duck", "dog"), swapped)
    }

    @Test
    fun `only two to four local players are accepted`() {
        assertThrows(IllegalArgumentException::class.java) {
            StarterPawsAssignmentPolicy.normalize(
                playerCount = 1,
                requestedCharacterIds = emptyList(),
                preferredCharacterId = "dog",
                computerMode = false,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            StarterPawsAssignmentPolicy.normalize(
                playerCount = 5,
                requestedCharacterIds = emptyList(),
                preferredCharacterId = "dog",
                computerMode = false,
            )
        }
        assertFalse(StarterPawsAssignmentPolicy.starterCharacterIds.isEmpty())
    }
    @Test
    fun `saved locked animals cannot bypass human entitlement selection`() {
        val free = setOf("dog", "goat", "duck", "cat")
        val local = StarterPawsAssignmentPolicy.normalize(4, listOf("goat_lumi", "donkey", "dog", "cat"), "goat_lumi", false, free)
        assertEquals(free, local.toSet())
        val computer = StarterPawsAssignmentPolicy.normalize(4, listOf("goat_lumi"), "goat_lumi", true, free)
        assertEquals("dog", computer.first())
        val rejected = StarterPawsAssignmentPolicy.select(4, local, 0, "donkey", false, free)
        assertEquals(local, rejected)
    }

    @Test
    fun `unlocking lumi does not unlock donkey or duplicate another local animal`() {
        val unlocked = setOf("dog", "goat", "duck", "cat", "goat_lumi")
        val selected = StarterPawsAssignmentPolicy.select(4, listOf("dog", "goat", "duck", "cat"), 0, "goat_lumi", false, unlocked)
        assertEquals("goat_lumi", selected.first())
        assertEquals(4, selected.toSet().size)
        assertFalse("donkey" in selected)
    }

}
