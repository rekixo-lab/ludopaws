package com.rekixo.ludopaws.feature.dice.content.pumpkin

import com.rekixo.ludopaws.feature.dice.domain.*
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object PumpkinDiceDefinition {
    val content = DiceDefinition(
        offer = cosmetic("dice_pumpkin","PUMPKIN",CosmeticCategory.DICE,CosmeticUnlockKind.FREE,"1",0xFFD9802F.toInt(),0xFF6A351C.toInt(),contentAvailable=false),
        paletteFactory = { DicePalette(
                intArrayOf(0xFFFFB65C.toInt(), 0xFFD9802F.toInt(), 0xFFB65E25.toInt()),
                0xFF6A351C.toInt(),
                0xFF3D2418.toInt(),
            ) },
    )
}
