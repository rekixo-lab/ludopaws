package com.rekixo.ludopaws.feature.boards.content.pirate

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object PirateBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
                id = "board_pirate",
                title = "LOCKED BOARD",
                category = CosmeticCategory.BOARD,
                unlockKind = CosmeticUnlockKind.GEMS,
                previewSymbol = "",
                previewPrimary = 0xFFF7F4EC.toInt(),
                previewSecondary = 0xFFF7F4EC.toInt(),
                gems = 200,
                level = 16,
                contentAvailable = false,
            ),
        surfaceColor = 0xFFE8D5A9.toInt(),
        neutralCellColor = 0xFFFFF2D2.toInt(),
        tintColor = 0xFFC9A86A.toInt(),
        tintAmount = .18f,
    )
}
