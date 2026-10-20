package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.online.VoicePeerStatus
import com.rekixo.ludopaws.feature.online.VoicePeerStatusPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePeerStatusPolicyTest {
    @Test fun peersAreNotConnectedJustBecauseSignalingWasCreated() {
        assertEquals("Waiting for a friend to join voice…", VoicePeerStatusPolicy.message(emptyList()))
        assertTrue(VoicePeerStatusPolicy.message(listOf(VoicePeerStatus.CONNECTING)).startsWith("Connecting"))
    }
    @Test fun connectedCountOnlyIncludesWebRtcConnectedPeers() {
        assertEquals("Voice connected to 1 friend",
            VoicePeerStatusPolicy.message(listOf(VoicePeerStatus.CONNECTING, VoicePeerStatus.CONNECTED)))
        assertEquals("Voice connected to 2 friends",
            VoicePeerStatusPolicy.message(listOf(VoicePeerStatus.CONNECTED, VoicePeerStatus.CONNECTED)))
    }
    @Test fun errorAndReconnectionStatesCannotLookSuccessful() {
        assertTrue(VoicePeerStatusPolicy.message(listOf(VoicePeerStatus.INTERRUPTED)).contains("interrupted"))
        assertTrue(VoicePeerStatusPolicy.message(listOf(VoicePeerStatus.FAILED)).contains("failed"))
    }
}
