package com.rekixo.ludopaws.feature.store.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseAccountSafetyTest {
    @Test fun guestCheckoutDoesNotNeedGoogleBackup() { assertTrue(PurchaseAccountSafety.canLaunch("guest", "guest")) }
    @Test fun savedCurrentAccountCanPurchase() { assertTrue(PurchaseAccountSafety.canLaunch("saved", "saved")) }
    @Test fun switchedAccountCannotUsePreviousApproval() { assertFalse(PurchaseAccountSafety.canLaunch("first", "second")) }
    @Test fun deletedSessionCannotUsePreviousApproval() { assertFalse(PurchaseAccountSafety.canLaunch("saved", null)) }
    @Test fun blankAccountNeverPermitsCheckout() { assertFalse(PurchaseAccountSafety.canLaunch("", "")) }
    @Test fun missingAccountNeverPermitsCheckout() { assertFalse(PurchaseAccountSafety.canLaunch(null, null)) }
}
