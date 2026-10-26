package com.rekixo.ludopaws.feature.boards.content.classic

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.LudoPawsBoardPalette
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
                previewPrimary = LudoPawsBoardPalette.stoneLight,
                previewSecondary = LudoPawsBoardPalette.stoneLight,
            ),
        surfaceColor = LudoPawsBoardPalette.joint,
        neutralCellColor = LudoPawsBoardPalette.stone,
        classicRoadReliefEnabled = true,
    )
}
