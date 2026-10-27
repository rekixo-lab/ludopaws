package com.rekixo.ludopaws.feature.boards.content.monsoon

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind

object MonsoonBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
            id = "board_monsoon_mire", title = "MONSOON MIRE",
            category = CosmeticCategory.BOARD, unlockKind = CosmeticUnlockKind.FREE,
            previewSymbol = "", previewPrimary = 0xFF78908A.toInt(),
            previewSecondary = 0xFF806249.toInt(),
        ),
        surfaceColor = 0xFF89988A.toInt(), neutralCellColor = 0xFFD0C3A5.toInt(),
        rendererKey = "monsoon_mire", yardHabitatEnabled = false,
        stoneRoadOverlayEnabled = false, monsoonEffectsEnabled = true,
    )
}
