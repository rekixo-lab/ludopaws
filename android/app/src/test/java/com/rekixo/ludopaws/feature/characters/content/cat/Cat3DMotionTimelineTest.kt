package com.rekixo.ludopaws.feature.characters.content.cat

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Cat3DMotionTimelineTest {
    @Test
    fun hopStartsAndEndsOnGroundWithVisibleApex() {
        val start = Cat3DMotionTimeline.sample(Cat3DMotion.HOP, 0f)
        // Swing becomes visible during flight, not during early takeoff easing.
        val airborne = Cat3DMotionTimeline.sample(Cat3DMotion.HOP, 0.40f)
        val apex = Cat3DMotionTimeline.sample(Cat3DMotion.HOP, 0.5f)
        val end = Cat3DMotionTimeline.sample(Cat3DMotion.HOP, 1f)

        assertEquals(0f, start.liftY, 0.0001f)
        assertTrue(apex.liftY > 0.55f)
        assertTrue(apex.earTwitchDegrees > 8f)
        assertTrue(abs(airborne.legSwingDegrees) > 5f)
        assertTrue(apex.legTuckDegrees > 15f)
        assertEquals(0f, end.liftY, 0.0001f)
        assertEquals(0f, end.legSwingDegrees, 0.0001f)
        assertEquals(0f, end.legTuckDegrees, 0.0001f)
    }

    @Test
    fun homeCelebrationSpinsOneFullTurnAndSwishesTail() {
        val halfway = Cat3DMotionTimeline.sample(Cat3DMotion.HOME, 0.5f)
        val sample = Cat3DMotionTimeline.sample(Cat3DMotion.HOME, 0.30f)
        val end = Cat3DMotionTimeline.sample(Cat3DMotion.HOME, 1f)

        assertEquals(180f, halfway.bodyYawDegrees, 0.001f)
        assertTrue(abs(sample.tailSwayDegrees) > 20f)
        assertEquals(360f, end.bodyYawDegrees, 0.001f)
        assertEquals(0f, end.liftY, 0.0001f)
    }

    @Test
    fun idleKeepsCatGroundedWithSubtleMotionAndTailLife() {
        val samples =
            listOf(0f, 0.125f, 0.25f, 0.375f, 0.5f)
                .map {
                    Cat3DMotionTimeline.sample(Cat3DMotion.IDLE, it)
                }

        assertTrue(samples.all { abs(it.liftY) <= 0.016f })
        assertTrue(samples.any { abs(it.tailSwayDegrees) >= 20f })
    }
}
