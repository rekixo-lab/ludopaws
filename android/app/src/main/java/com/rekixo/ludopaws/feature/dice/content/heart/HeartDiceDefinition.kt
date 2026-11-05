package com.rekixo.ludopaws.feature.dice.content.heart

import com.rekixo.ludopaws.feature.dice.domain.*
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.*

object HeartDiceDefinition {
    val content = DiceDefinition(
        offer = cosmetic("dice_heart","HEART",CosmeticCategory.DICE,CosmeticUnlockKind.GEMS,"♥",0xFFF8F8F8.toInt(),0xFFD82E3D.toInt(),gems=200,contentAvailable=false),
        paletteFactory = { DicePalette(
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF8F8.toInt(), 0xFFF3C8D0.toInt()),
                0xFFD82E3D.toInt(),
                0xFFD82E3D.toInt(),
            ) },
    )
}
