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
