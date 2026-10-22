package com.rekixo.ludopaws

import org.junit.Assert.*
import org.junit.Test

class LudoPaws3DDisplayPacerTest {
    @Test fun usesWholeRefreshesAt60And120HzWithoutPeriodicDoubleFrames() {
        for (hz in listOf(60, 120)) {
            for (targetDelay in listOf(16L, 33L)) {
                val pacer = LudoPaws3DDisplayPacer()
                val refresh = 1_000_000_000L / hz
                val selected = (0 until hz * 2).filter {
                    pacer.shouldRender(10_000_000_000L + it * refresh, refresh, targetDelay)
                }
                val stride = hz / if (targetDelay == 16L) 60 else 30
                assertEquals("$hz Hz / $targetDelay ms", 120 / if (targetDelay == 16L) 1 else 2, selected.size)
                assertTrue(selected.zipWithNext().all { (a, b) -> b - a == stride })
            }
        }
    }

    @Test fun wakesAtNextRefreshAndDoesNotReplayMissedFrames() {
        val pacer = LudoPaws3DDisplayPacer()
        val period = 16_666_666L
        val start = 10_000_000_000L
        assertTrue(pacer.shouldRender(start, period, 33))
        assertTrue("new move starts on next refresh", pacer.shouldRender(start + period, period, 16))
        assertTrue(pacer.shouldRender(start + period * 7, period, 16))
        assertFalse(pacer.shouldRender(start + period * 7, period, 16))
        assertTrue(pacer.shouldRender(start + period * 8, period, 16))
        pacer.reset()
        assertTrue(pacer.shouldRender(start + period * 8, period, 33))
    }
}
