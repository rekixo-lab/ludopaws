package com.rekixo.ludopaws.feature.boards.content.checkers

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object CheckersBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
                id = "board_checkers",
                title = "CHECKERS",
                category = CosmeticCategory.BOARD,
                unlockKind = CosmeticUnlockKind.FREE,
                previewSymbol = "",
                previewPrimary = 0xFFF7F4EC.toInt(),
                previewSecondary = 0xFFF7F4EC.toInt(),
                contentAvailable = false,
            ),
        surfaceColor = 0xFFF5EEE2.toInt(),
    )
}
