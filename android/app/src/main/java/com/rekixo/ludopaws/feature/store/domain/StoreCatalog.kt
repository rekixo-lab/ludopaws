package com.rekixo.ludopaws.feature.store.domain

import com.rekixo.ludopaws.feature.store.domain.model.StoreProduct
import com.rekixo.ludopaws.feature.store.domain.model.StoreProductKind

object StoreCatalog {
    private const val PRICE_UNAVAILABLE = "Price unavailable"

    const val REMOVE_ADS =
        "remove_ads"
    const val GEMS_300 =
        "gems_300"
    const val GEMS_1500 =
        "gems_1500"
    const val GEMS_3500 =
        "gems_3500"
    const val GEMS_20000 =
        "gems_20000"
    const val GEMS_40000 =
        "gems_40000"
    const val GEMS_200000 =
        "gems_200000"

    val products =
        listOf(
            StoreProduct(
                productId =
                    REMOVE_ADS,
                title =
                    "Remove Ads",
                gemAmount =
                    5,
                kind =
                    StoreProductKind.NON_CONSUMABLE_REMOVE_ADS,
                fallbackPrice =
                    PRICE_UNAVAILABLE,
                featured =
                    true,
            ),
            StoreProduct(
                productId =
                    GEMS_300,
                title =
                    "300 Gems · Starter",
                gemAmount =
                    300,
                kind =
                    StoreProductKind.CONSUMABLE_GEMS,
                fallbackPrice =
                    PRICE_UNAVAILABLE,
            ),
            StoreProduct(
                productId =
                    GEMS_1500,
                title =
                    "1,200 Gems · Value",
                gemAmount =
                    1_200,
                kind =
                    StoreProductKind.CONSUMABLE_GEMS,
                fallbackPrice =
                    PRICE_UNAVAILABLE,
            ),
            StoreProduct(
                productId =
                    GEMS_3500,
                title =
                    "3,000 Gems · Popular",
                gemAmount =
                    3_000,
                kind =
                    StoreProductKind.CONSUMABLE_GEMS,
                fallbackPrice =
                    PRICE_UNAVAILABLE,
            ),
            StoreProduct(
                productId =
                    GEMS_20000,
                title =
                    "7,500 Gems",
                gemAmount =
                    7_500,
                kind =
                    StoreProductKind.CONSUMABLE_GEMS,
                fallbackPrice =
                    PRICE_UNAVAILABLE,
            ),
            StoreProduct(
                productId =
                    GEMS_40000,
                title =
                    "16,000 Gems",
                gemAmount =
                    16_000,
                kind =
                    StoreProductKind.CONSUMABLE_GEMS,
                fallbackPrice =
                    PRICE_UNAVAILABLE,
            ),
            StoreProduct(
                productId =
                    GEMS_200000,
                title =
                    "35,000 Gems · Best Value",
                gemAmount =
                    35_000,
                kind =
                    StoreProductKind.CONSUMABLE_GEMS,
                fallbackPrice =
                    PRICE_UNAVAILABLE,
            ),
        ) + CosmeticPricing.productIds.map { id ->
            StoreProduct(
                productId = id,
                title = id.removePrefix("character_").removePrefix("board_").replace('_', ' '),
                gemAmount = 0,
                kind = StoreProductKind.NON_CONSUMABLE_COSMETIC,
                fallbackPrice = PRICE_UNAVAILABLE,
                cosmeticId = id,
            )
        }

    val playProductIds:
        List<String> =
        products.map {
            it.productId
        }

    fun find(
        productId: String,
    ): StoreProduct? =
        products.firstOrNull {
            it.productId == productId
        }

    fun cosmeticProductId(cosmeticId: String): String? =
        products.firstOrNull { it.cosmeticId == cosmeticId }?.productId
}
