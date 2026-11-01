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
    fun `robot cpu can swap owned opponent paws but cannot select locked premium`() {
        val owned = setOf("dog", "goat", "duck", "cat")
        val current = listOf("dog", "goat", "duck", "cat")
        val swapped = StarterPawsAssignmentPolicy.select(
            playerCount = 4,
            currentCharacterIds = current,
            selectedSlot = 2,
            requestedCharacterId = "cat",
            computerMode = true,
            unlockedCharacterIds = owned,
        )
        assertEquals(listOf("dog", "goat", "cat", "duck"), swapped)
        val denied = StarterPawsAssignmentPolicy.select(
            playerCount = 4,
            currentCharacterIds = swapped,
            selectedSlot = 2,
            requestedCharacterId = "horse",
            computerMode = true,
            unlockedCharacterIds = owned,
        )
        assertEquals(swapped, denied)
        assertEquals(swapped.size, swapped.toSet().size)
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
            unlockedCharacterIds = setOf("dog", "goat", "duck", "cat"),
        )
        assertEquals(listOf("dog", "duck", "goat", "cat"), swapped)
    }

    @Test
    fun `robot cpu cannot replace the human paw or unlock it`() {
        val unlocked = setOf("dog", "goat", "duck", "cat")
        val current = listOf("dog", "goat", "duck", "cat")
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
            4, current, 0, "cat", true, setOf("dog", "goat", "duck", "cat"),
        )
        assertEquals(listOf("cat", "goat", "duck", "dog"), swapped)
    }

    @Test
    fun `mixed human and robot seats keep owned paws on every human seat`() {
        val owned = setOf("dog", "goat", "duck", "cat")
        val initial = StarterPawsAssignmentPolicy.normalize(
            playerCount = 4,
            requestedCharacterIds = listOf("dog", "horse", "rabbit", "cat"),
            preferredCharacterId = "dog",
            computerMode = true,
            unlockedCharacterIds = owned,
            humanCount = 2,
        )
        assertEquals(listOf("dog", "goat", "duck", "cat"), initial)
        assertEquals(4, initial.toSet().size)
        assertTrue(initial.take(2).all(owned::contains))

        val invalid = StarterPawsAssignmentPolicy.select(
            playerCount = 4, currentCharacterIds = initial,
            selectedSlot = 1, requestedCharacterId = "horse",
            computerMode = true, unlockedCharacterIds = owned, humanCount = 2,
        )
        assertEquals(initial, invalid)

        val robotCannotTakeHuman2 = StarterPawsAssignmentPolicy.select(
            playerCount = 4, currentCharacterIds = initial,
            selectedSlot = 2, requestedCharacterId = "goat",
            computerMode = true, unlockedCharacterIds = owned, humanCount = 2,
        )
        assertEquals(initial, robotCannotTakeHuman2)
    }

    @Test
    fun `raising number of human seats repairs previously locked cpu paw`() {
        val owned = setOf("dog", "goat", "duck", "cat")
        val selected = StarterPawsAssignmentPolicy.normalize(
            playerCount = 4,
            requestedCharacterIds = listOf("dog", "goat", "horse", "rabbit"),
            preferredCharacterId = "dog",
            computerMode = true, unlockedCharacterIds = owned, humanCount = 3,
        )
        assertEquals("duck", selected[2])
        assertEquals("cat", selected[3])
        assertTrue(selected.take(3).all(owned::contains))
        assertEquals(4, selected.distinct().size)
    }

    @Test
    fun `locked CPU paws in old saves are repaired for every mixed team size`() {
        val owned = setOf("dog", "goat", "duck", "cat")
        for (humans in 1..3) {
            val restored = StarterPawsAssignmentPolicy.normalize(
                playerCount = 4,
                requestedCharacterIds = listOf("horse", "panda", "rabbit", "monkey"),
                preferredCharacterId = "horse",
                computerMode = true,
                unlockedCharacterIds = owned,
                humanCount = humans,
            )
            assertEquals(listOf("dog", "goat", "duck", "cat"), restored)
        }
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
