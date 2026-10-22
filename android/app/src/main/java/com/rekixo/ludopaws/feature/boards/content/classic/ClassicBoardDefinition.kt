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
                previewPrimary = 0xFFF7F4EC.toInt(),
                previewSecondary = 0xFFF7F4EC.toInt(),
            ),

    )
}
