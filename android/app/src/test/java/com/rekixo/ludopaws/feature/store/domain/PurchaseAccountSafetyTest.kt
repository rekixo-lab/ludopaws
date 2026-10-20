package com.rekixo.ludopaws.feature.store.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseAccountSafetyTest {
    @Test fun guestCheckoutNeedsBackup() { assertFalse(PurchaseAccountSafety.canLaunch("guest", "guest", false)) }
    @Test fun savedCurrentAccountCanPurchase() { assertTrue(PurchaseAccountSafety.canLaunch("saved", "saved", true)) }
    @Test fun switchedAccountCannotUsePreviousApproval() { assertFalse(PurchaseAccountSafety.canLaunch("first", "second", true)) }
    @Test fun deletedSessionCannotUsePreviousApproval() { assertFalse(PurchaseAccountSafety.canLaunch("saved", null, true)) }
    @Test fun missingAccountNeverPermitsCheckout() { assertFalse(PurchaseAccountSafety.canLaunch(null, null, true)) }
}
