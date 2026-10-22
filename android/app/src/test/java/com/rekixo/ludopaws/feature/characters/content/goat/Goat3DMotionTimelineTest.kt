package com.rekixo.ludopaws.feature.characters.content.goat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class Goat3DMotionTimelineTest {
    @Test
    fun hopStartsAndEndsOnGroundWithVisibleApex() {
        val start = Goat3DMotionTimeline.sample(Goat3DMotion.HOP, 0f)
        val airborne = Goat3DMotionTimeline.sample(Goat3DMotion.HOP, 0.25f)
        val apex = Goat3DMotionTimeline.sample(Goat3DMotion.HOP, 0.5f)
        val end = Goat3DMotionTimeline.sample(Goat3DMotion.HOP, 1f)

        assertEquals(0f, start.liftY, 0.0001f)
        assertTrue(apex.liftY > 0.55f)
        assertTrue(apex.earFlickDegrees > 10f)
        assertTrue(apex.beardSwingDegrees > 12f)
        assertTrue(abs(airborne.legSwingDegrees) > 5f)
        assertTrue(apex.legTuckDegrees > 10f)
        assertEquals(0f, end.liftY, 0.0001f)
        assertEquals(0f, end.legSwingDegrees, 0.0001f)
        assertEquals(0f, end.legTuckDegrees, 0.0001f)
    }

    @Test
    fun homeCelebrationSpinsOneFullTurnAndMovesTail() {
        val halfway = Goat3DMotionTimeline.sample(Goat3DMotion.HOME, 0.5f)
        val quarter = Goat3DMotionTimeline.sample(Goat3DMotion.HOME, 0.25f)
        val end = Goat3DMotionTimeline.sample(Goat3DMotion.HOME, 1f)

        assertEquals(180f, halfway.bodyYawDegrees, 0.001f)
        assertTrue(abs(quarter.tailFlickDegrees) > 15f)
        assertEquals(360f, end.bodyYawDegrees, 0.001f)
        assertEquals(0f, end.liftY, 0.0001f)
    }

    @Test
    fun idleMotionStaysSubtle() {
        val samples =
            listOf(0f, 0.125f, 0.25f, 0.375f, 0.5f)
                .map {
                    Goat3DMotionTimeline.sample(Goat3DMotion.IDLE, it)
                }

        assertTrue(samples.all { abs(it.liftY) <= 0.017f })
        assertTrue(samples.any { abs(it.earFlickDegrees) >= 3.5f })
        assertTrue(samples.any { abs(it.tailFlickDegrees) >= 8f })
    }
}
