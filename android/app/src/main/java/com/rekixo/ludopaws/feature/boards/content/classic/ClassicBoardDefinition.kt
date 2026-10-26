package com.rekixo.ludopaws.feature.boards.content.classic

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object ClassicBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
                id = "board_classic",
                title = "CLASSIC",
                category = CosmeticCategory.BOARD,
                unlockKind = CosmeticUnlockKind.FREE,
                previewSymbol = "",
                previewPrimary = 0xFF52733E.toInt(),
                previewSecondary = 0xFF8D9861.toInt(),
            ),
        surfaceColor = 0xFF52634A.toInt(),
        neutralCellColor = 0xFFB6B8A0.toInt(),
        yardHabitatEnabled = false,
        classicRoadReliefEnabled = true,
    )
}
