package com.rekixo.ludopaws.feature.boards.content.autumn

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object AutumnBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
            id = "board_autumn_forest", title = "AUTUMN FOREST",
            category = CosmeticCategory.BOARD, unlockKind = CosmeticUnlockKind.FREE,
            previewSymbol = "", previewPrimary = 0xFFB55F3A.toInt(),
            previewSecondary = 0xFFE5C7A1.toInt(),
        ),
        surfaceColor = 0xFFE5C7A1.toInt(), neutralCellColor = 0xFFE2C59D.toInt(),
        rendererKey = "autumn_forest", yardHabitatEnabled = false,
        stoneRoadOverlayEnabled = false,
    )
}
