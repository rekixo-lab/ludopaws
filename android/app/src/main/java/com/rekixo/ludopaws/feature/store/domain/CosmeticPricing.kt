package com.rekixo.ludopaws.feature.store.domain

import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic

internal object CosmeticPricing {
    val paidCharacterIds = listOf("panda", "monkey", "cock", "pig", "elephant", "horse", "rabbit")
    val paidBoardIds = listOf("board_astral", "board_cloud_lagoon", "board_autumn_forest",
        "board_blossom_garden", "board_winter_haven", "board_monsoon_mire")
    val productIds = paidCharacterIds.map { "character_$it" } + paidBoardIds

    fun apply(offer: StoreCosmetic): StoreCosmetic {
        if (offer.id == "dice_pearl_paw") return offer.copy(
            unlockKind = CosmeticUnlockKind.GEMS, gemPrice = 100, requiredLevel = 0,
            requiredAdViews = 20,
            unlockChannels = listOf(CosmeticUnlockKind.GEMS, CosmeticUnlockKind.REWARDED_ADS),
        )
        if (offer.id !in productIds) return offer
        val astral = offer.id == "board_astral"
        return offer.copy(
            unlockKind = CosmeticUnlockKind.GEMS,
            gemPrice = if (offer.id == "character_panda" || offer.id == "character_monkey") 150 else 300,
            requiredLevel = 0,
            requiredAdViews = if (astral) 15 else 0,
            unlockChannels = listOf(CosmeticUnlockKind.GEMS, CosmeticUnlockKind.PLAY_PRODUCT) +
                if (astral) listOf(CosmeticUnlockKind.REWARDED_ADS) else emptyList(),
        )
    }
}
