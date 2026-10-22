package com.rekixo.ludopaws.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRatingAnimationPolicyTest {
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
