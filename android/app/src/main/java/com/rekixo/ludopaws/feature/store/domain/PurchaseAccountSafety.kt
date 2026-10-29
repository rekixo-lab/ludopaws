package com.rekixo.ludopaws.feature.store.domain

/** Guest and saved accounts can purchase; a stale account must never authorize checkout. */
internal object PurchaseAccountSafety {
    fun canLaunch(expectedAccountId: String?, currentAccountId: String?): Boolean =
        !expectedAccountId.isNullOrBlank() && expectedAccountId == currentAccountId
}
