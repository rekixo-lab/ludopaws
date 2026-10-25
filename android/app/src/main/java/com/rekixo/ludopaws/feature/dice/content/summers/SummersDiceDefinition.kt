package com.rekixo.ludopaws.feature.dice.content.summers

import com.rekixo.ludopaws.feature.dice.domain.*
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object SummersDiceDefinition {
    val content = DiceDefinition(
        offer = cosmetic("dice_summers","SUMMERS",CosmeticCategory.DICE,CosmeticUnlockKind.REWARDED_ADS,"3",0xFFFFF1D4.toInt(),0xFFE84432.toInt(),ads=30,contentAvailable=false),
        paletteFactory = { DicePalette(
                intArrayOf(0xFFFFFDF2.toInt(), 0xFFFFF1D4.toInt(), 0xFFFFD2A9.toInt()),
                0xFFE84432.toInt(),
                0xFFA62520.toInt(),
            ) },
    )
}
