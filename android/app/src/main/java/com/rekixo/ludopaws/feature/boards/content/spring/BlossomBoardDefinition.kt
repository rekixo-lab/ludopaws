package com.rekixo.ludopaws.feature.boards.content.spring

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object BlossomBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
            id = "board_blossom_garden", title = "BLOSSOM GARDEN",
            category = CosmeticCategory.BOARD, unlockKind = CosmeticUnlockKind.FREE,
            previewSymbol = "", previewPrimary = 0xFFE5A4B4.toInt(),
            previewSecondary = 0xFF91B58C.toInt(),
        ),
        surfaceColor = 0xFFDCE8CF.toInt(), neutralCellColor = 0xFFF5EEDF.toInt(),
        rendererKey = "blossom_garden", yardHabitatEnabled = false,
        stoneRoadOverlayEnabled = false,
    )
}
