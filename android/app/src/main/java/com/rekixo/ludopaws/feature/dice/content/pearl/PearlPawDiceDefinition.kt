package com.rekixo.ludopaws.feature.dice.content.pearl

import com.rekixo.ludopaws.feature.dice.domain.DiceDefinition
import com.rekixo.ludopaws.feature.dice.domain.DicePalette
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind

object PearlPawDiceDefinition {
    val content = DiceDefinition(
        offer = cosmetic("dice_pearl_paw", "PEARL PAW", CosmeticCategory.DICE,
            CosmeticUnlockKind.GEMS, "PAW", 0xFFFFF5EB.toInt(), 0xFFCA6791.toInt(),
            gems = 100, ads = 20).copy(unlockChannels = listOf(
                CosmeticUnlockKind.GEMS, CosmeticUnlockKind.REWARDED_ADS,
            )),
        paletteFactory = { DicePalette(
            intArrayOf(0xFFFFFCF3.toInt(), 0xFFFDE6EB.toInt(), 0xFFE9DDF5.toInt()),
            0xFFE8B66A.toInt(), 0xFFCE6B94.toInt(),
        ) },
        rendererKey = "pearl_paw",
        previewFace = 1,
    )
}
