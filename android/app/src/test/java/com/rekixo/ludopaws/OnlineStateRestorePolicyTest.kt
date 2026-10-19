package com.rekixo.ludopaws

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineStateRestorePolicyTest {
    @Test fun cachedBoardMustBelongToTheActiveSession() {
        assertTrue(OnlineCachedStateRestorePolicy.shouldApply("LPABCDEFGH", "LPABCDEFGH"))
        assertFalse(OnlineCachedStateRestorePolicy.shouldApply("LPABCDEFGH", "LPIJKLMNPQ"))
        assertFalse(OnlineCachedStateRestorePolicy.shouldApply(null, "LPABCDEFGH"))
        assertFalse(OnlineCachedStateRestorePolicy.shouldApply("LPABCDEFGH", null))
    }
}
