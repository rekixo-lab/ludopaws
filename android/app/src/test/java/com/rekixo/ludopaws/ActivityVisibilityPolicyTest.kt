package com.rekixo.ludopaws

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityVisibilityPolicyTest {
    @Test fun navigationAndRecreationDoNotRestartForegroundWork() {
        val policy = ActivityVisibilityPolicy()
        assertTrue(policy.started())
        assertFalse(policy.started()) // second screen starts before first stops
        assertFalse(policy.stopped())
        assertFalse(policy.backgroundTimeout())
        assertTrue(policy.stopped()) // configuration gap
        assertFalse(policy.started())
        assertFalse(policy.backgroundTimeout())
        assertTrue(policy.isForeground)
    }

    @Test fun actualBackgroundStopsOnceAndReturningStartsOnce() {
        val policy = ActivityVisibilityPolicy()
        assertTrue(policy.started())
        assertTrue(policy.stopped())
        assertTrue(policy.backgroundTimeout())
        assertFalse(policy.backgroundTimeout())
        assertFalse(policy.isForeground)
        assertTrue(policy.started())
    }
}
