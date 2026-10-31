package com.rekixo.ludopaws.feature.boards.content.penguin

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object PenguinBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
                id = "board_penguin",
                title = "PENGUIN",
                category = CosmeticCategory.BOARD,
                unlockKind = CosmeticUnlockKind.GEMS,
                previewSymbol = "",
                previewPrimary = 0xFFF7F4EC.toInt(),
                previewSecondary = 0xFFF7F4EC.toInt(),
                gems = 250,
                contentAvailable = false,
            ),
        surfaceColor = 0xFFE5F8FF.toInt(),
        neutralCellColor = 0xFFF5FCFF.toInt(),
        tintColor = 0xFFBDEBFF.toInt(),
        tintAmount = .24f,
    )
}
