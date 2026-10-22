package com.rekixo.ludopaws.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRatingAnimationPolicyTest {
    @Test
    fun liquidGoldLevelRisesFromStarBottomToTop() {
        assertEquals(82f, RatingLiquidFillPolicy.surfaceY(0f), 0.0001f)
        assertEquals(44.5f, RatingLiquidFillPolicy.surfaceY(.5f), 0.0001f)
        assertEquals(7f, RatingLiquidFillPolicy.surfaceY(1f), 0.0001f)
    }

    @Test
    fun liquidWaveSettlesOnceTheStarIsFull() {
        assertEquals(0f, RatingLiquidFillPolicy.waveHeight(0f), 0.0001f)
        assertEquals(2.6f, RatingLiquidFillPolicy.waveHeight(.5f), 0.0001f)
        assertEquals(0f, RatingLiquidFillPolicy.waveHeight(1f), 0.0001f)
        assertEquals(82f, RatingLiquidFillPolicy.surfaceY(-50f), 0.0001f)
        assertEquals(7f, RatingLiquidFillPolicy.surfaceY(50f), 0.0001f)
    }

    @Test
    fun starsFillOneByOneLeftToRight() {
        assertEquals(0f, RatingStarFillPolicy.fraction(0f, 0), 0.0001f)
        assertEquals(.5f, RatingStarFillPolicy.fraction(.5f, 0), 0.0001f)
        assertEquals(0f, RatingStarFillPolicy.fraction(.5f, 1), 0.0001f)
        assertEquals(1f, RatingStarFillPolicy.fraction(1f, 0), 0.0001f)
        assertEquals(.5f, RatingStarFillPolicy.fraction(2.5f, 2), 0.0001f)
        assertEquals(0f, RatingStarFillPolicy.fraction(2.5f, 3), 0.0001f)
        assertEquals(1f, RatingStarFillPolicy.fraction(5f, 4), 0.0001f)
    }

    @Test
    fun clampsFilledStarBoundaries() {
        for (star in 0 until RatingStarFillPolicy.MAX_STARS) {
            assertEquals(0f, RatingStarFillPolicy.fraction(-5f, star), 0.0001f)
            assertEquals(1f, RatingStarFillPolicy.fraction(15f, star), 0.0001f)
        }
    }
}
