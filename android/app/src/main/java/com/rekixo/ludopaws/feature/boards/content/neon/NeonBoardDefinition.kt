package com.rekixo.ludopaws.feature.boards.content.neon

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object NeonBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
                id = "board_neon",
                title = "NEON",
                category = CosmeticCategory.BOARD,
                unlockKind = CosmeticUnlockKind.GEMS,
                previewSymbol = "",
                previewPrimary = 0xFFF7F4EC.toInt(),
                previewSecondary = 0xFFF7F4EC.toInt(),
                gems = 150,
                contentAvailable = false,
            ),
        surfaceColor = 0xFF10131C.toInt(),
        neutralCellColor = 0xFF1E2638.toInt(),
        brightness = 1.22f,
    )
}
