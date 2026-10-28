package com.rekixo.ludopaws.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSpaceBackgroundPolicyTest {
    @Test
    fun starsStayWithinFixedDrawingBudgetAtAllScreenSizes() {
        assertEquals(36, HomeSpaceBackgroundPolicy.starCount(120, 80, 1f))
        assertEquals(105, HomeSpaceBackgroundPolicy.starCount(350, 270, 1f))
        assertEquals(105, HomeSpaceBackgroundPolicy.starCount(1050, 810, 3f))
        assertEquals(120, HomeSpaceBackgroundPolicy.starCount(3000, 2000, 1f))
        assertEquals(36, HomeSpaceBackgroundPolicy.starCount(0, 0, 0f))
    }

    @Test
    fun twinklingKeepsVisibleButBoundedStarlight() {
        for (tick in 0..500) {
            val alpha = HomeSpaceBackgroundPolicy.starAlpha(tick / 10f, 1.4f, 2.1f)
            assertTrue(alpha in 85..245)
        }
        assertEquals(165, HomeSpaceBackgroundPolicy.starAlpha(0f, 1f, 0f))
    }
}
