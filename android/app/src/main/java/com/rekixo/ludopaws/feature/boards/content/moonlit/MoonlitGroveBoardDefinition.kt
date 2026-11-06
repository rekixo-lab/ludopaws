package com.rekixo.ludopaws.feature.boards.content.moonlit

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object MoonlitGroveBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(id = "board_moonlit_grove", title = "MOONLIT GROVE", category = CosmeticCategory.BOARD,
            unlockKind = CosmeticUnlockKind.GEMS, previewSymbol = "", previewPrimary = 0xFF132D46.toInt(),
            previewSecondary = 0xFF78B7CB.toInt(), gems = 250),
        surfaceColor = 0xFF132D46.toInt(), neutralCellColor = 0xFFE4F1F2.toInt(),
        rendererKey = "meadow_grove", yardHabitatEnabled = true,
    )
}
