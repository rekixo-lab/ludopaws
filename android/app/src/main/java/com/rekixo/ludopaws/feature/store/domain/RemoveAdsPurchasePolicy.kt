package com.rekixo.ludopaws.feature.store.domain

/** Play ownership is a checkout signal, NEVER a server entitlement grant. */
enum class RemoveAdsPlayOwnership {
    UNKNOWN, CHECKING, NOT_OWNED, OWNED_BY_CURRENT_ACCOUNT,
    OWNED_BY_OTHER_ACCOUNT, OWNED_WITHOUT_BINDING, PENDING, QUERY_FAILED,
}
enum class RemoveAdsOfferState {
    BUY, ACTIVE, VERIFYING, RESTORE_ACCOUNT, CONTACT_SUPPORT,
    PAYMENT_PENDING, CHECKING, RETRY_CHECK,
}
internal object RemoveAdsPurchasePolicy {
    fun state(serverOwned: Boolean, playOwnership: RemoveAdsPlayOwnership): RemoveAdsOfferState {
        if (serverOwned) return RemoveAdsOfferState.ACTIVE
        return when (playOwnership) {
            RemoveAdsPlayOwnership.NOT_OWNED -> RemoveAdsOfferState.BUY
            RemoveAdsPlayOwnership.OWNED_BY_CURRENT_ACCOUNT -> RemoveAdsOfferState.VERIFYING
            RemoveAdsPlayOwnership.OWNED_BY_OTHER_ACCOUNT -> RemoveAdsOfferState.RESTORE_ACCOUNT
            RemoveAdsPlayOwnership.OWNED_WITHOUT_BINDING -> RemoveAdsOfferState.CONTACT_SUPPORT
            RemoveAdsPlayOwnership.PENDING -> RemoveAdsOfferState.PAYMENT_PENDING
            RemoveAdsPlayOwnership.UNKNOWN, RemoveAdsPlayOwnership.CHECKING -> RemoveAdsOfferState.CHECKING
            RemoveAdsPlayOwnership.QUERY_FAILED -> RemoveAdsOfferState.RETRY_CHECK
        }
    }
    private val billingAccountPattern = Regex("^[a-f0-9]{64}$")

    /** The immutable Play result contains only a billing-account hash, not a game entitlement. */
    fun classifyOwnership(
        purchaseBindings: List<String?>,
        hasPending: Boolean,
        currentBillingAccountId: String,
    ): RemoveAdsPlayOwnership {
        if (purchaseBindings.any { it != null && it == currentBillingAccountId && billingAccountPattern.matches(it) })
            return RemoveAdsPlayOwnership.OWNED_BY_CURRENT_ACCOUNT
        if (purchaseBindings.any { it != null && billingAccountPattern.matches(it) })
            return RemoveAdsPlayOwnership.OWNED_BY_OTHER_ACCOUNT
        if (purchaseBindings.isNotEmpty()) return RemoveAdsPlayOwnership.OWNED_WITHOUT_BINDING
        if (hasPending) return RemoveAdsPlayOwnership.PENDING
        return RemoveAdsPlayOwnership.NOT_OWNED
    }

    fun canStartCheckout(serverOwned: Boolean, ownership: RemoveAdsPlayOwnership): Boolean =
        state(serverOwned, ownership) == RemoveAdsOfferState.BUY
}
