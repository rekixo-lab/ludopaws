package com.rekixo.ludopaws.feature.characters.content.duck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Duck3DMotionTimelineTest {
    @Test
    fun hopStartsAndEndsOnGroundWithVisibleApex() {
        val start = Duck3DMotionTimeline.sample(Duck3DMotion.HOP, 0f)
        // Swing becomes visible during flight, not during early takeoff easing.
        val airborne = Duck3DMotionTimeline.sample(Duck3DMotion.HOP, 0.40f)
        val apex = Duck3DMotionTimeline.sample(Duck3DMotion.HOP, 0.5f)
        val end = Duck3DMotionTimeline.sample(Duck3DMotion.HOP, 1f)

        assertEquals(0f, start.liftY, 0.0001f)
        assertTrue(apex.liftY > 0.55f)
        assertTrue(apex.wingFlapDegrees > 25f)
        assertTrue(kotlin.math.abs(airborne.legPaddleDegrees) > 10f)
        assertEquals(0f, end.liftY, 0.0001f)
        assertEquals(0f, end.legPaddleDegrees, 0.0001f)
    }

    @Test
    fun homeCelebrationSpinsOneFullTurn() {
        val halfway = Duck3DMotionTimeline.sample(Duck3DMotion.HOME, 0.5f)
        val end = Duck3DMotionTimeline.sample(Duck3DMotion.HOME, 1f)

        assertEquals(180f, halfway.bodyYawDegrees, 0.001f)
        assertEquals(360f, end.bodyYawDegrees, 0.001f)
        assertEquals(0f, end.liftY, 0.0001f)
    }

    @Test
    fun idleMotionStaysSubtle() {
        val samples =
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
                .map {
                    Duck3DMotionTimeline.sample(Duck3DMotion.IDLE, it)
                }

        assertTrue(samples.all { kotlin.math.abs(it.liftY) <= 0.026f })
        assertTrue(samples.all { kotlin.math.abs(it.wingFlapDegrees) <= 3.6f })
    }
}
