package com.rekixo.ludopaws.feature.boards.content.astral

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object AstralBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
            id = "board_astral", title = "ASTRAL GARDEN",
            category = CosmeticCategory.BOARD, unlockKind = CosmeticUnlockKind.FREE,
            previewSymbol = "", previewPrimary = 0xFF12263B.toInt(),
            previewSecondary = 0xFFE3C787.toInt(),
        ),
        surfaceColor = 0xFF12263B.toInt(),
        neutralCellColor = 0xFFF5F0DE.toInt(),
        rendererKey = "astral",
        yardHabitatEnabled = false,
    )
}
