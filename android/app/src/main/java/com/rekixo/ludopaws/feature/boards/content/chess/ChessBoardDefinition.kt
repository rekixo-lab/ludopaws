package com.rekixo.ludopaws.feature.boards.content.chess

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object ChessBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
                id = "board_chess",
                title = "LOCKED BOARD",
                category = CosmeticCategory.BOARD,
                unlockKind = CosmeticUnlockKind.REWARDED_ADS,
                previewSymbol = "",
                previewPrimary = 0xFFF7F4EC.toInt(),
                previewSecondary = 0xFFF7F4EC.toInt(),
                ads = 5,
                contentAvailable = false,
            ),
        surfaceColor = 0xFFF1E3C4.toInt(),
    )
}
