package com.rekixo.ludopaws.feature.store.data.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingPurchaseAccountPolicyTest {
    @Test
    fun `same game account can safely verify a saved token`() {
        assertTrue(PendingPurchaseAccountPolicy.mayVerify("account-a", "account-a"))
    }

    @Test
    fun `different game account must defer without transferring ownership`() {
        assertFalse(PendingPurchaseAccountPolicy.mayVerify("account-a", "account-b"))
    }

    @Test
    fun `missing game session cannot claim any purchase`() {
        assertFalse(PendingPurchaseAccountPolicy.mayVerify("account-a", null))
    }
}
