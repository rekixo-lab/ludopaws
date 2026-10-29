package com.rekixo.ludopaws.feature.team

import com.rekixo.ludopaws.feature.team.data.local.TeamSearchRequests
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamSearchRequestsTest {
    @Test fun queuedAndOverlappingRequestsRemainProtectedUntilBothComplete() {
        val id = "search-race-test"
        TeamSearchRequests.begin(id)
        TeamSearchRequests.begin(id)
        try {
            assertTrue(TeamSearchRequests.contains(id))
            TeamSearchRequests.finish(id)
            assertTrue(TeamSearchRequests.contains(id))
            TeamSearchRequests.finish(id)
            assertFalse(TeamSearchRequests.contains(id))
        } finally { TeamSearchRequests.finish(id); TeamSearchRequests.finish(id) }
    }
    @Test fun completingAnOldRequestDoesNotReleaseAnotherSearch() {
        TeamSearchRequests.begin("older")
        TeamSearchRequests.begin("newer")
        try {
            TeamSearchRequests.finish("older")
            assertFalse(TeamSearchRequests.contains("older"))
            assertTrue(TeamSearchRequests.contains("newer"))
        } finally { TeamSearchRequests.finish("older"); TeamSearchRequests.finish("newer") }
    }
}
