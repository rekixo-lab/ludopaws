package com.rekixo.ludopaws.feature.dice.content.colors

import com.rekixo.ludopaws.feature.dice.domain.*
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object ColorsDiceDefinition {
    val content = DiceDefinition(
        offer = cosmetic("dice_colors","COLORS",CosmeticCategory.DICE,CosmeticUnlockKind.GEMS,"3",0xFFFFD63B.toInt(),0xFFEF3F8A.toInt(),gems=200),
        paletteFactory = { DicePalette(
                intArrayOf(0xFFFFF08B.toInt(), 0xFFFFD63B.toInt(), 0xFFEF3F8A.toInt()),
                0xFFEF3F8A.toInt(),
                0xFF5C1645.toInt(),
            ) },
    )
}
