package com.rekixo.ludopaws.feature.store.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoveAdsPurchasePolicyTest {
    @Test fun newPurchaseRequiresConfirmedAbsenceOfPlayOwnership() {
        assertTrue(RemoveAdsPurchasePolicy.canStartCheckout(false, RemoveAdsPlayOwnership.NOT_OWNED))
        for (state in RemoveAdsPlayOwnership.entries.filter { it != RemoveAdsPlayOwnership.NOT_OWNED }) {
            assertFalse(RemoveAdsPurchasePolicy.canStartCheckout(false, state))
        }
    }

    @Test fun serverOwnedAlwaysMeansActiveWithoutCheckout() {
        for (state in RemoveAdsPlayOwnership.entries) {
            assertEquals(RemoveAdsOfferState.ACTIVE, RemoveAdsPurchasePolicy.state(true, state))
            assertFalse(RemoveAdsPurchasePolicy.canStartCheckout(true, state))
        }
    }

    @Test fun existingCurrentPurchaseRequiresServerVerification() {
        assertEquals(RemoveAdsOfferState.VERIFYING,
            RemoveAdsPurchasePolicy.state(false, RemoveAdsPlayOwnership.OWNED_BY_CURRENT_ACCOUNT))
    }

    @Test fun otherAccountPurchaseCannotBeTransferredOrBoughtAgain() {
        assertEquals(RemoveAdsOfferState.RESTORE_ACCOUNT,
            RemoveAdsPurchasePolicy.state(false, RemoveAdsPlayOwnership.OWNED_BY_OTHER_ACCOUNT))
    }

    @Test fun missingBindingRequiresSupportNotFreeEntitlement() {
        assertEquals(RemoveAdsOfferState.CONTACT_SUPPORT,
            RemoveAdsPurchasePolicy.state(false, RemoveAdsPlayOwnership.OWNED_WITHOUT_BINDING))
    }

    @Test fun pendingPurchaseWaitsForGooglePlay() {
        assertEquals(RemoveAdsOfferState.PAYMENT_PENDING,
            RemoveAdsPurchasePolicy.state(false, RemoveAdsPlayOwnership.PENDING))
    }

    @Test fun ownershipClassificationPrefersOriginalGameAccount() {
        val original = "a".repeat(64)
        val other = "b".repeat(64)
        assertEquals(RemoveAdsPlayOwnership.OWNED_BY_CURRENT_ACCOUNT,
            RemoveAdsPurchasePolicy.classifyOwnership(listOf(other, original), false, original))
        assertEquals(RemoveAdsPlayOwnership.OWNED_BY_OTHER_ACCOUNT,
            RemoveAdsPurchasePolicy.classifyOwnership(listOf(other), false, original))
        assertEquals(RemoveAdsPlayOwnership.OWNED_WITHOUT_BINDING,
            RemoveAdsPurchasePolicy.classifyOwnership(listOf(null, "invalid"), false, original))
        assertEquals(RemoveAdsPlayOwnership.PENDING,
            RemoveAdsPurchasePolicy.classifyOwnership(emptyList(), true, original))
        assertEquals(RemoveAdsPlayOwnership.NOT_OWNED,
            RemoveAdsPurchasePolicy.classifyOwnership(emptyList(), false, original))
    }

    @Test fun failedQueryRequiresRetry() {
        assertEquals(RemoveAdsOfferState.RETRY_CHECK,
            RemoveAdsPurchasePolicy.state(false, RemoveAdsPlayOwnership.QUERY_FAILED))
    }
}
