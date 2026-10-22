package com.rekixo.ludopaws.feature.store.domain

import com.rekixo.ludopaws.feature.store.domain.model.*

internal fun cosmetic(
        id: String,
        title: String,
        category: CosmeticCategory,
        unlockKind: CosmeticUnlockKind,
        previewSymbol: String,
        previewPrimary: Int,
        previewSecondary: Int,
        gems: Int = 0,
        level: Int = 0,
        ads: Int = 0,
        characterPackId: String? = null,
        eventKey: String? = null,
        contentAvailable: Boolean = true,
    ) =
        StoreCosmetic(
            id = id,
            title = title,
            category = category,
            unlockKind = unlockKind,
            gemPrice = gems,
            requiredLevel = level,
            requiredAdViews = ads,
            previewSymbol = previewSymbol,
            previewPrimary = previewPrimary,
            previewSecondary = previewSecondary,
            characterPackId = characterPackId,
            eventKey = eventKey,
            contentAvailable = contentAvailable,
        )
