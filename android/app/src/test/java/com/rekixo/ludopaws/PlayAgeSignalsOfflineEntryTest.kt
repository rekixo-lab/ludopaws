package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.safety.AgeBand
import com.rekixo.ludopaws.feature.safety.PlayAgeSignalsObserver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayAgeSignalsOfflineEntryTest {
    @Test fun noAgeSignalRequiresNoDobAndNeverClaimsOnlineEnabled() {
        val text = PlayAgeSignalsObserver.offlineExplanationFor(AgeBand.UNKNOWN)
        assertTrue(text.contains("No birthday is required"))
        assertTrue(text.contains("offline"))
        assertFalse(text.contains("Enter your date"))
    }

    @Test fun evenAdultBandNeverUnlocksMultiplayer() {
        val text = PlayAgeSignalsObserver.offlineExplanationFor(AgeBand.ADULT)
        assertTrue(text.contains("disabled"))
        assertTrue(text.contains("server safety"))
        assertTrue(text.contains("offline"))
    }

    @Test fun minorBandStaysOfflineWithoutExposingAge() {
        val text = PlayAgeSignalsObserver.offlineExplanationFor(AgeBand.CHILD)
        assertTrue(text.contains("offline"))
        assertFalse(text.contains("13"))
        assertFalse(text.contains("18"))
    }
}
