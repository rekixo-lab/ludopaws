package com.rekixo.ludopaws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoPawsPawnLayoutVisualPolicyTest {
    @Test
    fun `single animal pawns remain prominent across yard track and home`() {
        assertEquals(
            0.528f,
            LudoPawsPawnLayout.radiusScale(
                position = -1,
                occupancy = 1,
            ),
            0.0001f,
        )
        assertEquals(
            0.4884f,
            LudoPawsPawnLayout.radiusScale(
                position = 12,
                occupancy = 1,
            ),
            0.0001f,
        )
        assertEquals(
            0.462f,
            LudoPawsPawnLayout.radiusScale(
                position = LudoPathEncoding.HOME_POSITION,
                occupancy = 1,
            ),
            0.0001f,
        )
    }

    @Test
    fun `each animal species stands on the exact authoritative tile at enlarged scale`() {
        // Paw-ground pivots from the shipped dog/cat/goat/duck/donkey rigs.
        // The foot must remain exactly over the tile center in the yard,
        // stretched road, home lane and finish, regardless of size.
        val pivots = listOf(-0.84f, -0.84f, -0.954f, -0.755f, -0.84f)
        val centers = listOf(45f, 175f, 320f, 487.5f)
        val modelScales = listOf(12f, 24f, 34f, 48f, 61f)
        for (pivot in pivots) {
            for (center in centers) {
                for (scale in modelScales) {
                    val rootY = LudoPawsPawnLayout.rootYForFootContact(
                        boardCenterY = center,
                        modelScale = scale,
                        groundPivotY = pivot,
                    )
                    // prepareRoot's negative Y scale maps the pivot back to the
                    // same exact cell center; old root offsets failed at 1.32x.
                    assertEquals(center, rootY - scale * pivot, 0.0001f)
                }
            }
        }
    }

    @Test
    fun `contact shadow stays small and tile centered even with larger models`() {
        val cell = 60f
        for (position in listOf(-1, 0, 12, 52, LudoPathEncoding.HOME_POSITION)) {
            for (occupancy in listOf(1, 2, 4, 9, 16)) {
                val radius = cell * LudoPawsPawnLayout.radiusScale(position, occupancy)
                for (presentationScale in listOf(0.65f, 1f, 1.30f)) {
                    val shadow = LudoPawsPawnLayout.contactShadowRadius(
                        cell, radius, presentationScale,
                    )
                    assertTrue("shadow overflows a cell: $position / $occupancy", shadow <= cell * .30f)
                    assertTrue(shadow > 0f)
                }
            }
        }
    }

    @Test
    fun `shared cells shrink monotonically without changing board position`() {
        val single =
            LudoPawsPawnLayout.radiusScale(
                position = 12,
                occupancy = 1,
            )
        val pair =
            LudoPawsPawnLayout.radiusScale(
                position = 12,
                occupancy = 2,
            )
        val four =
            LudoPawsPawnLayout.radiusScale(
                position = 12,
                occupancy = 4,
            )
        val crowded =
            LudoPawsPawnLayout.radiusScale(
                position = 12,
                occupancy = 10,
            )

        assertTrue(single > pair)
        assertTrue(pair > four)
        assertTrue(four > crowded)
    }
}
