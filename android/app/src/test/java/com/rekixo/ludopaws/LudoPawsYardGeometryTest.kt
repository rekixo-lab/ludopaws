package com.rekixo.ludopaws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Full yard bounds and real starting slots remain authoritative. */
class LudoPawsYardGeometryTest {
    @Test fun `clean yards fill all four complete five-cell quadrants without color strips`() {
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
                val f = requireNotNull(LudoPawsYardGeometry.face(color, cell))
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
        assertNull(LudoPawsYardGeometry.face("PURPLE", 60f))
        assertNull(LudoPawsYardGeometry.face("RED", 0f))
        assertNull(LudoPawsYardGeometry.face("RED", Float.NaN))
    }

}
