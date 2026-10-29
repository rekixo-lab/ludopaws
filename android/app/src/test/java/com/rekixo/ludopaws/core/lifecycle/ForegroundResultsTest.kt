package com.rekixo.ludopaws.core.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundResultsTest {
    @Test fun stoppedScreenDefersResultsUntilForegroundAndDeliversOnce() {
        val gate = ForegroundResults()
        var navigation = 0
        gate.deliver { navigation++ }
        assertEquals(0, navigation)
        gate.start()
        gate.start()
        assertEquals(1, navigation)
        gate.stop()
        gate.deliver { navigation++ }
        assertEquals(1, navigation)
        gate.start()
        assertEquals(2, navigation)
    }
    @Test fun destroyedScreenNeverDeliversPendingOrLateResponses() {
        val gate = ForegroundResults()
        var navigation = 0
        gate.deliver { navigation++ }
        gate.close()
        gate.deliver { navigation++ }
        gate.start()
        assertEquals(0, navigation)
    }
    @Test fun navigationStoppingScreenDefersRemainingResults() {
        val gate = ForegroundResults()
        val events = mutableListOf<Int>()
        gate.deliver { events += 1; gate.stop() }
        gate.deliver { events += 2 }
        gate.start()
        assertEquals(listOf(1), events)
        gate.start()
        assertEquals(listOf(1, 2), events)
    }
}
