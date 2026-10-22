package com.rekixo.ludopaws.feature.dice.content.classic

import com.rekixo.ludopaws.feature.dice.domain.*
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object ClassicDiceDefinition {
    val content = DiceDefinition(
        offer = cosmetic("dice_classic","CLASSIC",CosmeticCategory.DICE,CosmeticUnlockKind.FREE,"⚄",0xFFF7F7F7.toInt(),0xFF1B2838.toInt()),
        paletteFactory = { DicePalette(
                intArrayOf(0xFFF4F4F4.toInt(), 0xFFF4F4F4.toInt(), 0xFFF4F4F4.toInt()),
                0xFF8A8A8A.toInt(),
                0xFF3A3A3A.toInt(),
            ) },
    )
}
