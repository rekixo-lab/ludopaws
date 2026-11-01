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
    fun `player tile labels explain human robot counts within two lines`() {
        assertEquals("1 HUMAN\n1 ROBOT", RobotTeamComposition.tileLabel(2, 1, true))
        assertEquals("1 HUMAN\n2 ROBOTS", RobotTeamComposition.tileLabel(3, 1, false))
        assertEquals("2 HUMANS\n1 ROBOT ↻", RobotTeamComposition.tileLabel(3, 2, true))
        assertEquals("1 HUMAN\n3 ROBOTS ↻", RobotTeamComposition.tileLabel(4, 1, true))
        assertEquals("2 HUMANS\n2 ROBOTS ↻", RobotTeamComposition.tileLabel(4, 2, true))
        assertEquals("3 HUMANS\n1 ROBOT ↻", RobotTeamComposition.tileLabel(4, 3, true))
    }

    @Test
    fun `changing player count always starts at the one human tile preview`() {
        assertEquals(1, RobotTeamComposition.humansAfterPlayerTileTap(4, 3, 2))
        assertEquals(1, RobotTeamComposition.humansAfterPlayerTileTap(3, 4, 3))
        assertEquals(2, RobotTeamComposition.humansAfterPlayerTileTap(4, 4, 1))
        assertEquals(3, RobotTeamComposition.humansAfterPlayerTileTap(4, 4, 2))
        assertEquals(1, RobotTeamComposition.humansAfterPlayerTileTap(4, 4, 3))
        assertEquals(1, RobotTeamComposition.humansAfterPlayerTileTap(2, 2, 1))
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
