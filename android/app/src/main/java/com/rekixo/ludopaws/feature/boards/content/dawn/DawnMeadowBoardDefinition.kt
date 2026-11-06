package com.rekixo.ludopaws.feature.boards.content.dawn

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object DawnMeadowBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(id = "board_dawn_meadow", title = "DAWN MEADOW", category = CosmeticCategory.BOARD,
            unlockKind = CosmeticUnlockKind.GEMS, previewSymbol = "", previewPrimary = 0xFFF6E4CE.toInt(),
            previewSecondary = 0xFFE6AA55.toInt(), gems = 250),
        surfaceColor = 0xFFF6E4CE.toInt(), neutralCellColor = 0xFFFFFAEC.toInt(),
        rendererKey = "meadow_grove", yardHabitatEnabled = true,
    )
}
