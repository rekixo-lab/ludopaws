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
    fun `computer selection can only change the human slot`() {
        val current = listOf("dog", "goat", "duck", "cat")
        val ignoredCpuChange =
            StarterPawsAssignmentPolicy.select(
                playerCount = 4,
                currentCharacterIds = current,
                selectedSlot = 2,
                requestedCharacterId = "cat",
                computerMode = true,
            )
        val humanChange =
            StarterPawsAssignmentPolicy.select(
                playerCount = 4,
                currentCharacterIds = current,
                selectedSlot = 0,
                requestedCharacterId = "cat",
                computerMode = true,
            )

        assertEquals(current, ignoredCpuChange)
        assertEquals("cat", humanChange.first())
        assertEquals(4, humanChange.distinct().size)
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
