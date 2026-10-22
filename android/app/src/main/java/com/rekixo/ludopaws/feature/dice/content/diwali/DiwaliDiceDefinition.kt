package com.rekixo.ludopaws.feature.dice.content.diwali

import com.rekixo.ludopaws.feature.dice.domain.*
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object DiwaliDiceDefinition {
    val content = DiceDefinition(
        offer = cosmetic("dice_diwali","DIWALI",CosmeticCategory.DICE,CosmeticUnlockKind.REWARDED_ADS,"6",0xFFB21C32.toInt(),0xFFFFC832.toInt(),ads=5),
        paletteFactory = { DicePalette(
                intArrayOf(0xFFFFE8A0.toInt(), 0xFFFFC832.toInt(), 0xFFB21C32.toInt()),
                0xFFB21C32.toInt(),
                0xFF7C1524.toInt(),
            ) },
    )
}
