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

    @Test fun compactRoofBaseMovesInwardAndItsApexRemainsAtTheExactFinishPoint() {
        val roof = LudoPawsCenterHouseGeometry.compactRoof()
        assertEquals(25f, roof.start.x, 0f)
        assertEquals(22f, roof.start.y, 0f)
        assertEquals(75f, roof.end.x, 0f)
        assertEquals(22f, roof.end.y, 0f)
        assertEquals(50f, roof.apex.x, 0f)
        assertEquals(50f, roof.apex.y, 0f)
        assertEquals(50f, roof.end.x - roof.start.x, 0f)
        assertEquals(28f, roof.apex.y - roof.start.y, 0f)
        assertEquals(50f, (roof.start.x + roof.end.x) / 2f, 0f)
        val oldRoofArea = (88.2f - 11.8f) * (50f - 15f) / 2f
        val newRoofArea = (roof.end.x - roof.start.x) *
            (roof.apex.y - roof.start.y) / 2f
        assertTrue(newRoofArea < oldRoofArea * .55f)
        assertEquals(700f, newRoofArea, .001f)
    }

    @Test fun compactRoofStaysInsideEveryUnchangedColorWedge() {
        val profile = LudoPawsCenterHouseGeometry.compactRoof()
        for (team in listOf("GREEN", "YELLOW", "BLUE", "RED")) {
            val angle = requireNotNull(LudoPawsCenterHouseGeometry.rotationDegrees(team))
            val radians = Math.toRadians(angle.toDouble())
            fun rotate(p: LudoPawsRoofPoint): LudoPawsRoofPoint {
                val dx = p.x - 50f
                val dy = p.y - 50f
                return LudoPawsRoofPoint(
                    50f + (dx * kotlin.math.cos(radians) - dy * kotlin.math.sin(radians)).toFloat(),
                    50f + (dx * kotlin.math.sin(radians) + dy * kotlin.math.cos(radians)).toFloat(),
                )
            }
            val wedge = requireNotNull(LudoPawsCenterHouseGeometry.triangle(team))
            for (point in listOf(profile.start, profile.end, profile.apex).map(::rotate)) {
                assertTrue("$team compact roof must not cross onto the road", contains(wedge, point))
            }
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
