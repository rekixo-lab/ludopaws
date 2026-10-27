package com.rekixo.ludopaws.feature.referrals

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferralPopupPolicyTest {
    private val now = 100 * ReferralPopupPolicy.DAY_MS
    private fun allowed(history: ReferralPopupHistory = ReferralPopupHistory(), state: String = "INVITE", eligible: Boolean = true, session: Boolean = false) =
        ReferralPopupPolicy.canShowAutomatically(eligible, state, history, now, session)

    @Test fun firstEligibleOpportunityCanShowWithoutConsentOrLinkingSideEffects() {
        assertTrue(allowed())
        assertFalse(allowed(eligible = false))
        assertFalse(allowed(session = true))
    }

    @Test fun progressReceivedAndUnknownStatesNeverAdvertiseAnotherInvitation() {
        for (state in listOf("FRIEND_JOINED", "VERIFYING", "REWARDED", "", "OTHER")) assertFalse(allowed(state = state))
    }

    @Test fun dayBoundaryIsEnforcedWithoutResettingForAnotherHomeVisit() {
        assertFalse(allowed(ReferralPopupHistory(impressions = 1, shownAt = now - ReferralPopupPolicy.DAY_MS + 1)))
        assertTrue(allowed(ReferralPopupHistory(impressions = 1, shownAt = now - ReferralPopupPolicy.DAY_MS)))
    }

    @Test fun laterAndCloseDelayPromotionForSeventyTwoHours() {
        assertFalse(allowed(ReferralPopupHistory(dismissedAt = now - 3 * ReferralPopupPolicy.DAY_MS + 1)))
        assertTrue(allowed(ReferralPopupHistory(dismissedAt = now - 3 * ReferralPopupPolicy.DAY_MS)))
    }

    @Test fun threeImpressionsAndInvalidClockHistoryFailClosed() {
        assertTrue(allowed(ReferralPopupHistory(impressions = 2)))
        assertFalse(allowed(ReferralPopupHistory(impressions = 3)))
        assertFalse(allowed(ReferralPopupHistory(impressions = -1)))
        assertFalse(allowed(ReferralPopupHistory(shownAt = now + 1)))
        assertFalse(allowed(ReferralPopupHistory(dismissedAt = now + 1)))
    }

    @Test fun leavingAndReturningHomeDoesNotRecreateAConsumedOpportunity() {
        val session = ReferralPopupSession()
        assertFalse(session.takeHomeOpportunity())
        session.completedMatch("match-1")
        assertTrue(session.takeHomeOpportunity())
        assertFalse(session.takeHomeOpportunity())
        session.completedMatch("match-1")
        assertFalse(session.takeHomeOpportunity())
        session.completedMatch("match-2")
        assertTrue(session.takeHomeOpportunity())
    }

    @Test fun fullscreenAdOrCheckoutSuppressesTheFollowingHomeVisit() {
        val session = ReferralPopupSession()
        assertFalse(session.fullscreenRecently(0L))
        session.fullscreenInteraction(10L)
        assertTrue(session.fullscreenRecently(10L))
        assertTrue(session.fullscreenRecently(60_000_000_009L))
        assertFalse(session.fullscreenRecently(60_000_000_010L))
        session.fullscreenInteraction(60_000_000_010L)
        assertTrue(session.fullscreenRecently(60_000_000_010L))
    }

    @Test fun automaticLimitBelongsToTheProcessRatherThanAnActivityInstance() {
        val session = ReferralPopupSession()
        session.markAutomaticShown()
        session.completedMatch("later-match")
        assertTrue(session.automaticShown)
        assertFalse(allowed(session = session.automaticShown))
    }
}
