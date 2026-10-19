package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.online.OnlineRollActionKind
import com.rekixo.ludopaws.feature.online.OnlineRollActionPolicy
import org.junit.Assert.*
import org.junit.Test

class OnlineRollActionPolicyTest {
    @Test fun freshAuthoritativeTurnCanRoll() {
        assertEquals(OnlineRollActionKind.NEW_ROLL, decision().kind)
        assertTrue(decision().enabled)
    }
    @Test fun cacheOfflineAndOpponentCannotAuthorizeRoll() {
        assertFalse(decision(authoritative = false).enabled)
        assertFalse(decision(online = false).enabled)
        assertFalse(decision(myTurn = false).enabled)
    }
    @Test fun resolvedDiceMustBeMovedBeforeAnotherRoll() {
        assertEquals(OnlineRollActionKind.MOVE_REQUIRED, decision(pending = "RESOLVED").kind)
        assertFalse(decision(pending = "RESOLVED").enabled)
    }
    @Test fun unexpectedPendingStatesRequireSync() {
        listOf("CREATING", "COMMITTED", "RESOLVING", "invalid").forEach {
            assertEquals(OnlineRollActionKind.WAIT_FOR_SYNC, decision(pending = it).kind)
            assertFalse(decision(pending = it).enabled)
        }
    }
    private fun decision(online: Boolean = true, authoritative: Boolean = true,
                         myTurn: Boolean = true, pending: String? = null) =
        OnlineRollActionPolicy.resolve(online, authoritative, "ACTIVE", myTurn, pending)
}
