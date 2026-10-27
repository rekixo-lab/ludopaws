package com.rekixo.ludopaws.feature.boards.content.winter

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind

object WinterBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
            id = "board_winter_haven", title = "WINTER HAVEN",
            category = CosmeticCategory.BOARD, unlockKind = CosmeticUnlockKind.FREE,
            previewSymbol = "", previewPrimary = 0xFFB9D8DE.toInt(),
            previewSecondary = 0xFF567B80.toInt(),
        ),
        surfaceColor = 0xFFC9DCE1.toInt(), neutralCellColor = 0xFFEAF1F0.toInt(),
        rendererKey = "winter_haven", yardHabitatEnabled = false,
        stoneRoadOverlayEnabled = false,
    )
}
