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
    fun canStartCheckout(serverOwned: Boolean, ownership: RemoveAdsPlayOwnership): Boolean =
        state(serverOwned, ownership) == RemoveAdsOfferState.BUY
}
