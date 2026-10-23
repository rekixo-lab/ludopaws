package com.rekixo.ludopaws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoPawsYardPalettePolicyTest {
    @Test fun allFourTeamPalettesAreDistinctAndSupported() {
        val teams = listOf("RED", "GREEN", "YELLOW", "BLUE")
        val palettes = teams.map(LudoPawsYardPalettePolicy::forTeam)
        assertTrue(palettes.all { it != null })
        assertEquals(4, palettes.filterNotNull().map { it.rim }.distinct().size)
        assertEquals(4, palettes.filterNotNull().map { it.bandDark }.distinct().size)
        assertEquals(4, palettes.filterNotNull().map { it.lipDark }.distinct().size)
        assertNull(LudoPawsYardPalettePolicy.forTeam("PURPLE"))
        assertNull(LudoPawsYardPalettePolicy.forTeam(""))
    }

    @Test fun groundStaysPaleAndClearUnderEnlargedCharacters() {
        for (team in listOf("RED", "GREEN", "YELLOW", "BLUE")) {
            val p = LudoPawsYardPalettePolicy.forTeam(team)
            assertNotNull(p)
            requireNotNull(p)
            for (ground in listOf(p.sandTop, p.sandBottom)) {
                val r = (ground ushr 16) and 255
                val g = (ground ushr 8) and 255
                val b = ground and 255
                assertTrue("$team ground should be bright", r >= 220 && g >= 195 && b >= 145)
            }
        }
    }

    @Test fun teamColorPaletteIsIndependentOfCharacterSpecies() {
        val colors = listOf("RED", "GREEN", "YELLOW", "BLUE")
        for (team in colors) {
            val palette = LudoPawsYardPalettePolicy.forTeam(team)
            // Remains constant across selections/turns.
            repeat(5) {
                assertEquals(palette, LudoPawsYardPalettePolicy.forTeam(team))
            }
        }
    }
}
