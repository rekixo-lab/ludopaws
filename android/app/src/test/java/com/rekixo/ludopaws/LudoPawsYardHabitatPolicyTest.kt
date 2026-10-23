package com.rekixo.ludopaws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoPawsYardHabitatPolicyTest {
    @Test fun `every shipped animal gets a unique habitat`() {
        val ids = listOf("dog", "goat", "duck", "cat", "donkey")
        val habitats = ids.map(LudoPawsYardHabitatPolicy::forCharacterId)
        assertEquals(5, habitats.distinct().size)
        assertEquals(LudoPawsYardHabitat.DUCK_POND, LudoPawsYardHabitatPolicy.forCharacterId("duck"))
        assertEquals(LudoPawsYardHabitat.DONKEY_FARM, LudoPawsYardHabitatPolicy.forCharacterId("donkey"))
    }

    @Test fun `selected character follows its seat color rather than fixed corner`() {
        val state = match(listOf("RED", "GREEN", "YELLOW", "BLUE"))
        val chosen = listOf("duck", "cat", "donkey", "goat")
        val yards = LudoPawsYardHabitatPolicy.forMatch(state, chosen)
        assertEquals(LudoPawsYardHabitat.DUCK_POND, yards["RED"])
        assertEquals(LudoPawsYardHabitat.CAT_COURTYARD, yards["GREEN"])
        assertEquals(LudoPawsYardHabitat.DONKEY_FARM, yards["YELLOW"])
        assertEquals(LudoPawsYardHabitat.GOAT_MEADOW, yards["BLUE"])
    }

    @Test fun `two player game leaves unoccupied yards unchanged`() {
        val state = match(listOf("BLUE", "YELLOW"))
        val yards = LudoPawsYardHabitatPolicy.forMatch(state, listOf("duck", "dog"))
        assertEquals(2, yards.size)
        assertEquals(LudoPawsYardHabitat.DUCK_POND, yards["BLUE"])
        assertEquals(LudoPawsYardHabitat.DOG_GARDEN, yards["YELLOW"])
        assertFalse(yards.containsKey("RED"))
        assertFalse(yards.containsKey("GREEN"))
    }

    @Test fun `missing selections use same color fallback as real 3D animals`() {
        val state = match(listOf("GREEN", "YELLOW", "BLUE", "RED"))
        val yards = LudoPawsYardHabitatPolicy.forMatch(state, emptyList())
        assertEquals(LudoPawsYardHabitat.GOAT_MEADOW, yards["GREEN"])
        assertEquals(LudoPawsYardHabitat.DUCK_POND, yards["YELLOW"])
        assertEquals(LudoPawsYardHabitat.CAT_COURTYARD, yards["BLUE"])
        assertEquals(LudoPawsYardHabitat.DOG_GARDEN, yards["RED"])
        assertTrue(LudoPawsYardHabitatPolicy.forMatch(null, listOf("duck")).isEmpty())
    }

    @Test fun `habitats fill all four complete five-cell yard quadrants without color strips`() {
        // Logical yard dimensions are 6x6, but the nonuniform displayed board
        // allocates exactly 5x5 physical cells to each yard.
        for (cell in listOf(1f, 37.5f, 60f, 128f)) {
            val expected = mapOf(
                "RED" to (0f to 0f),
                "GREEN" to (10f to 0f),
                "YELLOW" to (10f to 10f),
                "BLUE" to (0f to 10f),
            )
            for ((color, origin) in expected) {
                val f = requireNotNull(LudoPawsYardHabitatGeometry.face(color, cell))
                assertEquals(origin.first * cell, f.left, .001f)
                assertEquals(origin.second * cell, f.top, .001f)
                assertEquals((origin.first + 5f) * cell, f.right, .001f)
                assertEquals((origin.second + 5f) * cell, f.bottom, .001f)
                assertEquals(5f * cell, f.width, .001f)
                assertEquals(5f * cell, f.height, .001f)

                // Directly touches—but never overlaps—the cross and Center.
                val topOrBottom = f.bottom <= 5f * cell || f.top >= 10f * cell
                val leftOrRight = f.right <= 5f * cell || f.left >= 10f * cell
                assertTrue(topOrBottom && leftOrRight)
                assertTrue(f.left >= 0f && f.top >= 0f)
                assertTrue(f.right <= 15f * cell && f.bottom <= 15f * cell)
                // All four real starting slots are inside the complete habitat.
                for (tokenIndex in 0..3) {
                    val (x, y) = requireNotNull(
                        LudoPawsFxBoardGeometry.tokenCenter(color, tokenIndex, -1, cell),
                    )
                    assertTrue(x > f.left && x < f.right)
                    assertTrue(y > f.top && y < f.bottom)
                }
            }
        }
        assertNull(LudoPawsYardHabitatGeometry.face("PURPLE", 60f))
        assertNull(LudoPawsYardHabitatGeometry.face("RED", 0f))
        assertNull(LudoPawsYardHabitatGeometry.face("RED", Float.NaN))
    }

    private fun match(colors: List<String>): MatchSnapshot =
        MatchSnapshot(
            matchId = "habitat-test",
            status = "ACTIVE",
            hostPlayerId = "player0",
            players = colors.mapIndexed { seat, color ->
                PlayerSnapshot(
                    playerId = "player$seat",
                    displayName = "Player $seat",
                    color = color,
                    seat = seat,
                    tokens = listOf(-1, -1, -1, -1),
                )
            },
            turnSeat = 0,
            randomEventIndex = 0,
            pendingRoll = null,
            winnerPlayerId = null,
            rulesetId = "ludoproof-standard-v1",
            history = emptyList(),
        )
}
