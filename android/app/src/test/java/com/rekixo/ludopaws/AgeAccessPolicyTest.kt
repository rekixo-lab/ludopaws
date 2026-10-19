package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.safety.AgeAccessPolicy
import com.rekixo.ludopaws.feature.safety.AgeBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AgeAccessPolicyTest {
    @Test fun exactPlayAdultsAllowed() {
        assertEquals(AgeBand.ADULT, AgeAccessPolicy.fromPlayRange(18, null))
        assertEquals(AgeBand.ADULT, AgeAccessPolicy.fromPlayRange(18, 24))
    }

    @Test fun ambiguousAndUnsharedSignalsFailClosed() {
        assertEquals(AgeBand.UNKNOWN, AgeAccessPolicy.fromPlayRange(null, null))
        assertEquals(AgeBand.UNKNOWN, AgeAccessPolicy.fromPlayRange(15, null))
        assertEquals(AgeBand.UNKNOWN, AgeAccessPolicy.fromPlayRange(16, 21))
        assertEquals(AgeBand.UNKNOWN, AgeAccessPolicy.fromPlayRange(19, 14))
        assertEquals(AgeBand.UNKNOWN, AgeAccessPolicy.fromPlayRange(-1, 17))
    }

    @Test fun childrenAndTeensAreNotAdults() {
        assertEquals(AgeBand.CHILD, AgeAccessPolicy.fromPlayRange(0, 12))
        assertEquals(AgeBand.TEEN, AgeAccessPolicy.fromPlayRange(13, 17))
        assertFalse(AgeAccessPolicy.canUseConnectedFeatures(AgeBand.UNKNOWN))
        assertFalse(AgeAccessPolicy.canUseConnectedFeatures(AgeBand.CHILD))
        assertFalse(AgeAccessPolicy.canUseConnectedFeatures(AgeBand.TEEN))
        assertTrue(AgeAccessPolicy.canUseConnectedFeatures(AgeBand.ADULT))
    }

    @Test fun fallbackNeverRetainsBirthDateAndHandlesLeapDays() {
        val now = LocalDate.of(2026, 10, 19)
        assertEquals(AgeBand.ADULT, AgeAccessPolicy.fromBirthDate(19, 10, 2008, now))
        assertEquals(AgeBand.TEEN, AgeAccessPolicy.fromBirthDate(20, 10, 2008, now))
        assertEquals(AgeBand.UNKNOWN, AgeAccessPolicy.fromBirthDate(31, 2, 2010, now))
        assertEquals(AgeBand.UNKNOWN, AgeAccessPolicy.fromBirthDate(20, 10, 2026, now))
    }
}
