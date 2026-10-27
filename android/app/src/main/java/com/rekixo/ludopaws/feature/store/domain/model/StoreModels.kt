package com.rekixo.ludopaws.feature.store.domain.model

enum class StoreTab(
    val label: String,
) {
    GEMS("GEMS"),
    PAWS("PAWS"),
    BOARD("BOARD"),
    DICE("DICE"),
}

enum class StoreProductKind {
    CONSUMABLE_GEMS,
    NON_CONSUMABLE_REMOVE_ADS,
    NON_CONSUMABLE_COSMETIC,
}

data class StoreProduct(
    val productId: String,
    val title: String,
    val gemAmount: Int,
    val kind: StoreProductKind,
    val fallbackPrice: String,
    val featured: Boolean = false,
    val cosmeticId: String? = null,
)

data class StoreBillingProduct(
    val productId: String,
    val formattedPrice: String,
)

data class VerifiedStorePurchase(
    val productId: String,
    val purchaseToken: String,
)
