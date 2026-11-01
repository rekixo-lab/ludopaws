package com.rekixo.ludopaws.feature.offline.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RobotTeamCompositionTest {
    @Test
    fun `two player Robot match always has one human and one robot`() {
        assertEquals(1, RobotTeamComposition.nextHumans(2, 1))
        assertEquals("1H · 1R", RobotTeamComposition.label(2, 1))
    }

    @Test
    fun `three and four player mixes cycle without expanding screen`() {
        assertEquals(listOf(2, 1), sequence(3))
        assertEquals(listOf(2, 3, 1), sequence(4))
        assertEquals("2H · 2R", RobotTeamComposition.label(4, 2))
        assertEquals("3H · 1R", RobotTeamComposition.label(4, 3))
    }

    @Test
    fun `old saved values are normalized to a valid mix`() {
        assertEquals(1, RobotTeamComposition.humans(3, 0))
        assertEquals(2, RobotTeamComposition.humans(3, 99))
        assertThrows(IllegalArgumentException::class.java) {
            RobotTeamComposition.humans(5, 1)
        }
    }

    private fun sequence(total: Int): List<Int> {
        var current = 1
        return List(total - 1) {
            current = RobotTeamComposition.nextHumans(total, current)
            current
        }
    }
}
