package com.rekixo.ludopaws.feature.boards.content.lagoon

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object CloudLagoonBoardDefinition {
    val content = BoardDefinition(
        offer = cosmetic(
            id = "board_cloud_lagoon", title = "CLOUD LAGOON",
            category = CosmeticCategory.BOARD, unlockKind = CosmeticUnlockKind.FREE,
            previewSymbol = "", previewPrimary = 0xFFE2F0EA.toInt(),
            previewSecondary = 0xFF73BFB8.toInt(),
        ),
        surfaceColor = 0xFFE2F0EA.toInt(),
        neutralCellColor = 0xFFF5F2E8.toInt(),
        rendererKey = "cloud_lagoon", yardHabitatEnabled = false,
    )
}
