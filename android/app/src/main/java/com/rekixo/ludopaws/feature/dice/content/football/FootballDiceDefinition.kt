package com.rekixo.ludopaws.feature.dice.content.football

import com.rekixo.ludopaws.feature.dice.domain.*
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object FootballDiceDefinition {
    val content = DiceDefinition(
        offer = cosmetic("dice_football","FOOTBALL",CosmeticCategory.DICE,CosmeticUnlockKind.REWARDED_ADS,"6",0xFFF5F5F5.toInt(),0xFF222222.toInt(),ads=10),
        paletteFactory = { DicePalette(
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFF5F5F5.toInt(), 0xFFD4D4D4.toInt()),
                0xFF222222.toInt(),
                0xFF222222.toInt(),
            ) },
    )
}
