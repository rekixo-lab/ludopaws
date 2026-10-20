package com.rekixo.ludopaws

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeConnectionFailurePolicyTest {
    @Test fun authorizationFailuresDoNotLoopForever() {
        for (status in listOf(400, 401, 403, 404, 410, 426)) {
            assertTrue(RealtimeConnectionFailurePolicy.isTerminalHttp(status))
        }
    }
    @Test fun transientNetworkFailuresAreRetried() {
        for (status in listOf(408, 425, 429, 500, 502, 503, 504)) {
            assertFalse(RealtimeConnectionFailurePolicy.isTerminalHttp(status))
        }
    }
    @Test fun obsoleteProtocolGetsUsefulUpgradeMessage() {
        assertTrue(RealtimeConnectionFailurePolicy.userMessage(410).contains("update"))
        assertEquals("This match is no longer available.",
            RealtimeConnectionFailurePolicy.userMessage(404))
    }
}
