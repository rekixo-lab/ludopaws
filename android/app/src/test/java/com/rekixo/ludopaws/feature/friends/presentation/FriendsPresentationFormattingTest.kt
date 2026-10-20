package com.rekixo.ludopaws.feature.friends.presentation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FriendsPresentationFormattingTest {
    @Test fun lastSeenLabelsPreserveBoundarySemantics() {
        assertEquals("Offline", formatLastSeen(0L, 100_000L))
        assertEquals("Offline • Last seen just now", formatLastSeen(60_000L, 100_000L))
        assertEquals("Offline • Last seen 1m ago", formatLastSeen(40_000L, 100_000L))
        assertEquals("Offline • Last seen 1h ago", formatLastSeen(1_000L, 3_601_000L))
    }
    @Test fun playedAndMessageLabelsUseMatchingUnits() {
        assertEquals("Played recently", formatPlayed(0L, 100_000L))
        assertEquals("Played 1m ago", formatPlayed(40_000L, 100_000L))
        assertEquals("1m ago", formatMessageTime(40_000L, 100_000L))
        assertEquals("", formatMessageTime(0L, 100_000L))
    }
    @Test fun handoffRejectsUntrustedOrIncompleteSession() {
        val noToken = JSONObject().put("matchId", "LPABCDEFGH")
        assertFalse(isValidFriendRoomHandoff(noToken, "LPABCDEFGH", "player", 2))
    }
}
