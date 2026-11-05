package com.rekixo.ludopaws.feature.boards.content.sunstone

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind

object SunstonePalaceBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
            id = "board_sunstone_palace", title = "SUNSTONE PALACE",
            category = CosmeticCategory.BOARD, unlockKind = CosmeticUnlockKind.GEMS,
            previewSymbol = "", previewPrimary = 0xFFF3C369.toInt(),
            previewSecondary = 0xFF176A72.toInt(), gems = 300,
        ),
        surfaceColor = 0xFFEDB35C.toInt(),
        neutralCellColor = 0xFFFFE9B4.toInt(),
        rendererKey = "sunstone_palace",
        yardHabitatEnabled = false,
        stoneRoadOverlayEnabled = false,
    )
}
