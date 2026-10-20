package com.rekixo.ludopaws.feature.store.domain

/** Checkout requires a recoverable account and must not use a stale account-switch result. */
internal object PurchaseAccountSafety {
    fun canLaunch(expectedAccountId: String?, currentAccountId: String?, googleLinked: Boolean): Boolean =
        !expectedAccountId.isNullOrBlank() && expectedAccountId == currentAccountId && googleLinked
}
