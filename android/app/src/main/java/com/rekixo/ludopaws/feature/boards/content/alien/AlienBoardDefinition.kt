package com.rekixo.ludopaws.feature.boards.content.alien

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object AlienBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
                id = "board_alien",
                title = "LOCKED BOARD",
                category = CosmeticCategory.BOARD,
                unlockKind = CosmeticUnlockKind.GEMS,
                previewSymbol = "",
                previewPrimary = 0xFFF7F4EC.toInt(),
                previewSecondary = 0xFFF7F4EC.toInt(),
                gems = 250,
                level = 22,
                contentAvailable = false,
            ),
        surfaceColor = 0xFF18243B.toInt(),
        neutralCellColor = 0xFF24324A.toInt(),
        tintColor = 0xFF4EDA73.toInt(),
        tintAmount = .20f,
    )
}
