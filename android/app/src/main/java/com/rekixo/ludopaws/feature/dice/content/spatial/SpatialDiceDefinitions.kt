package com.rekixo.ludopaws.feature.dice.content.spatial

import com.rekixo.ludopaws.feature.dice.domain.DiceDefinition
import com.rekixo.ludopaws.feature.dice.domain.DicePalette
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind

internal enum class SpatialDiceMaterial(
    val key: String,
    val light: Int,
    val mid: Int,
    val dark: Int,
    val metal: Int,
    val pip: Int,
) {
    FORGE("ember_forge", 0xFF785445.toInt(), 0xFF432620.toInt(), 0xFF23191E.toInt(),
        0xFFC17342.toInt(), 0xFFFFC36B.toInt()),
    PRISM("prism_royal", 0xFF36B7C6.toInt(), 0xFF3459B4.toInt(), 0xFF32265D.toInt(),
        0xFF94BFE4.toInt(), 0xFFECFBFF.toInt()),
}

private fun spatialDice(id: String, title: String, material: SpatialDiceMaterial, preview: Int) = DiceDefinition(
    offer = cosmetic(id, title, CosmeticCategory.DICE, CosmeticUnlockKind.GEMS,
        preview.toString(), material.light, material.dark, gems = 150),
    paletteFactory = { DicePalette(intArrayOf(material.light, material.mid, material.dark), material.metal, material.pip) },
    rendererKey = material.key,
    previewFace = preview,
)

object EmberForgeDiceDefinition {
    val content = spatialDice("dice_ember_forge", "EMBER FORGE", SpatialDiceMaterial.FORGE, 5)
}

object PrismRoyalDiceDefinition {
    val content = spatialDice("dice_prism_royal", "PRISM ROYAL", SpatialDiceMaterial.PRISM, 6)
}
