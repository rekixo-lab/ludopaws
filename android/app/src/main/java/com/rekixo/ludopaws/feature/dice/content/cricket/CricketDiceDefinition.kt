package com.rekixo.ludopaws.feature.dice.content.cricket

import com.rekixo.ludopaws.feature.dice.domain.*
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object CricketDiceDefinition {
    val content = DiceDefinition(
        offer = cosmetic("dice_cricket","CRICKET",CosmeticCategory.DICE,CosmeticUnlockKind.GEMS,"1",0xFF4F9E5F.toInt(),0xFFE8F4D8.toInt(),gems=100,contentAvailable=false),
        paletteFactory = { DicePalette(
                intArrayOf(0xFFE8F4D8.toInt(), 0xFF9DD68E.toInt(), 0xFF4F9E5F.toInt()),
                0xFF2B6C36.toInt(),
                0xFF173F23.toInt(),
            ) },
    )
}
