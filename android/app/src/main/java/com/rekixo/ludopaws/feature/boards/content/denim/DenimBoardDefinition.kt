package com.rekixo.ludopaws.feature.boards.content.denim

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object DenimBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
                id = "board_denim",
                title = "DENIM",
                category = CosmeticCategory.BOARD,
                unlockKind = CosmeticUnlockKind.GEMS,
                previewSymbol = "",
                previewPrimary = 0xFFF7F4EC.toInt(),
                previewSecondary = 0xFFF7F4EC.toInt(),
                gems = 75,
                contentAvailable = false,
            ),
        surfaceColor = 0xFFD8EEFA.toInt(),
        tintColor = 0xFF2D83C5.toInt(),
        tintAmount = .20f,
    )
}
