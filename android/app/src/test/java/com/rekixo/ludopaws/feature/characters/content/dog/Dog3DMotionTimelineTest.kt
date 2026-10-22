package com.rekixo.ludopaws.feature.characters.content.dog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class Dog3DMotionTimelineTest {
    @Test
    fun hopStartsAndEndsOnGroundWithVisibleApex() {
        val start = Dog3DMotionTimeline.sample(Dog3DMotion.HOP, 0f)
        // Swing becomes visible during flight, not during early takeoff easing.
        val airborne = Dog3DMotionTimeline.sample(Dog3DMotion.HOP, 0.40f)
        val apex = Dog3DMotionTimeline.sample(Dog3DMotion.HOP, 0.5f)
        val end = Dog3DMotionTimeline.sample(Dog3DMotion.HOP, 1f)

        assertEquals(0f, start.liftY, 0.0001f)
        assertTrue(apex.liftY > 0.50f)
        assertTrue(apex.earBounceDegrees > 12f)
        assertTrue(abs(airborne.legSwingDegrees) > 5f)
        assertTrue(apex.legTuckDegrees > 10f)
        assertEquals(0f, end.liftY, 0.0001f)
        assertEquals(0f, end.legSwingDegrees, 0.0001f)
        assertEquals(0f, end.legTuckDegrees, 0.0001f)
    }

    @Test
    fun homeCelebrationSpinsOneFullTurnAndWagsTail() {
        val halfway = Dog3DMotionTimeline.sample(Dog3DMotion.HOME, 0.5f)
        val quarter = Dog3DMotionTimeline.sample(Dog3DMotion.HOME, 0.25f)
        val end = Dog3DMotionTimeline.sample(Dog3DMotion.HOME, 1f)

        assertEquals(180f, halfway.bodyYawDegrees, 0.001f)
        assertTrue(abs(quarter.tailWagDegrees) > 20f)
        assertEquals(360f, end.bodyYawDegrees, 0.001f)
        assertEquals(0f, end.liftY, 0.0001f)
    }

    @Test
    fun idleKeepsPuppyGroundedWithSubtleBodyMotionAndTailWag() {
        val samples =
            listOf(0f, 0.125f, 0.25f, 0.375f, 0.5f)
                .map {
                    Dog3DMotionTimeline.sample(Dog3DMotion.IDLE, it)
                }

        assertTrue(samples.all { abs(it.liftY) <= 0.019f })
        assertTrue(samples.any { abs(it.tailWagDegrees) >= 20f })
    }
}
