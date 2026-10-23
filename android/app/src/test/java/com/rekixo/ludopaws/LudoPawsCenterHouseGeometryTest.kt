package com.rekixo.ludopaws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LudoPawsCenterHouseGeometryTest {
    @Test fun centerSquareMatchesExistingStretchedBoardBoundaries() {
        for (cell in listOf(1f, 20f, 48f, 60f, 96f, 144f)) {
            val bounds = requireNotNull(LudoPawsCenterHouseGeometry.bounds(cell))
            assertEquals(5f * cell, bounds.left, .0001f)
            assertEquals(5f * cell, bounds.top, .0001f)
            assertEquals(10f * cell, bounds.right, .0001f)
            assertEquals(10f * cell, bounds.bottom, .0001f)
            assertEquals(5f * cell, bounds.width, .0001f)
            assertEquals(5f * cell, bounds.height, .0001f)
        }
        assertNull(LudoPawsCenterHouseGeometry.bounds(0f))
        assertNull(LudoPawsCenterHouseGeometry.bounds(-1f))
        assertNull(LudoPawsCenterHouseGeometry.bounds(Float.NaN))
    }

    @Test fun fourRoofApexesShareExactlyOneUnmovedFinishCenter() {
        for (team in listOf("GREEN", "YELLOW", "BLUE", "RED")) {
            val t = requireNotNull(LudoPawsCenterHouseGeometry.triangle(team))
            assertEquals(50f, t.apex.x, 0f)
            assertEquals(50f, t.apex.y, 0f)
            val area = abs(
                (t.end.x - t.start.x) * (t.apex.y - t.start.y) -
                (t.end.y - t.start.y) * (t.apex.x - t.start.x)
            ) / 2f
            assertEquals(2500f, area, 0f)
        }
        assertNull(LudoPawsCenterHouseGeometry.triangle("PURPLE"))
    }

    @Test fun theFourRooftopsMatchExistingCanonicalWedgeDirections() {
        val expected = mapOf(
            "GREEN" to listOf(0f, 0f, 100f, 0f, 0f),
            "YELLOW" to listOf(100f, 0f, 100f, 100f, 90f),
            "BLUE" to listOf(100f, 100f, 0f, 100f, 180f),
            "RED" to listOf(0f, 100f, 0f, 0f, 270f),
        )
        for ((team, v) in expected) {
            val t = requireNotNull(LudoPawsCenterHouseGeometry.triangle(team))
            assertEquals(v[0], t.start.x, 0f)
            assertEquals(v[1], t.start.y, 0f)
            assertEquals(v[2], t.end.x, 0f)
            assertEquals(v[3], t.end.y, 0f)
            assertEquals(v[4], LudoPawsCenterHouseGeometry.rotationDegrees(team)!!, 0f)
        }
        assertNull(LudoPawsCenterHouseGeometry.rotationDegrees("PURPLE"))
    }

    @Test fun finishPawnTargetsRemainInsideTheirExactTeamTriangle() {
        val finishPoints = mapOf(
            "RED" to LudoPawsRoofPoint(30f, 50f),
            "GREEN" to LudoPawsRoofPoint(50f, 30f),
            "YELLOW" to LudoPawsRoofPoint(70f, 50f),
            "BLUE" to LudoPawsRoofPoint(50f, 70f),
        )
        for ((team, point) in finishPoints) {
            val triangle = requireNotNull(LudoPawsCenterHouseGeometry.triangle(team))
            assertTrue("$team finish pawn must stay within its colored house",
                contains(triangle, point))
        }
    }

    private fun contains(t: LudoPawsRoofTriangle, p: LudoPawsRoofPoint): Boolean {
        fun cross(a: LudoPawsRoofPoint, b: LudoPawsRoofPoint, c: LudoPawsRoofPoint): Float =
            (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        return cross(t.start, t.end, p) >= 0f &&
            cross(t.end, t.apex, p) >= 0f &&
            cross(t.apex, t.start, p) >= 0f
    }
}
