package com.rekixo.ludopaws.feature.boards.content.diwali

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object DiwaliBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
                id = "board_diwali",
                title = "DIWALI",
                category = CosmeticCategory.BOARD,
                unlockKind = CosmeticUnlockKind.GEMS,
                previewSymbol = "",
                previewPrimary = 0xFFF7F4EC.toInt(),
                previewSecondary = 0xFFF7F4EC.toInt(),
                gems = 75,
                contentAvailable = false,
            ),
        surfaceColor = 0xFFFFE7A0.toInt(),
        neutralCellColor = 0xFFFFF6DC.toInt(),
        brightness = 1.08f,
    )
}
