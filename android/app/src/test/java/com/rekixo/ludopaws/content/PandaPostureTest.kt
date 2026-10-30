package com.rekixo.ludopaws.content

import com.rekixo.ludopaws.feature.characters.content.panda.PandaPosture
import org.junit.Assert.*
import org.junit.Test

class PandaPostureTest {
    @Test fun cycleContainsStandingFourPawsAndStandingAgain() {
        assertEquals(0f, PandaPosture.allFours(0f, 1f), .00001f)
        assertEquals(1f, PandaPosture.allFours(4f, 1f), .00001f)
        assertEquals(0f, PandaPosture.allFours(9f, 1f), .00001f)
        for (step in 0..1200) {
            val t = step / 100f
            val q = PandaPosture.allFours(t, 1f)
            assertTrue(q in 0f..1f)
            assertEquals(q, PandaPosture.allFours(t + 12f, 1f), .00001f)
        }
    }

    @Test fun motionAndReactionsReleaseTheFourPawStance() {
        assertEquals(0f, PandaPosture.allFours(4f, 0f), .00001f)
        assertEquals(.5f, PandaPosture.allFours(4f, .5f), .00001f)
    }

    @Test fun transitionsHaveNoVelocitySnapAtHoldsOrLoopBoundary() {
        val h = .001f
        for (t in listOf(0f, 1.2f, 3f, 6f, 7.8f, 12f)) {
            val center = PandaPosture.allFours(t, 1f)
            assertTrue(kotlin.math.abs(center - PandaPosture.allFours(t - h, 1f)) / h < .01f)
            assertTrue(kotlin.math.abs(PandaPosture.allFours(t + h, 1f) - center) / h < .01f)
        }
    }
}
