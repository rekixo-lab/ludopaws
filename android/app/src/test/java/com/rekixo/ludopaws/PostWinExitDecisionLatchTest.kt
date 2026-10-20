package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.store.data.ads.PostWinExitDecisionLatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostWinExitDecisionLatchTest {
    @Test fun completionAndTimeoutAreMutuallyExclusive() {
        val latch = PostWinExitDecisionLatch()
        assertTrue(latch.complete())
        assertFalse(latch.complete())
    }
    @Test fun closedScreensCannotRunTheirPendingNavigationCallbacks() {
        val latch = PostWinExitDecisionLatch()
        latch.cancel()
        assertFalse(latch.complete())
    }
}
