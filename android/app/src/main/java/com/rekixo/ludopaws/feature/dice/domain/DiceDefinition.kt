package com.rekixo.ludopaws.feature.dice.domain

import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic

data class DicePalette(val faceColors: IntArray, val borderColor: Int, val pipColor: Int)

/** Factory avoids sharing mutable color arrays across views, previews and tests. */
class DiceDefinition(
    val offer: StoreCosmetic,
    private val paletteFactory: () -> DicePalette,
    val rendererKey: String = "classic",
    val previewFace: Int = 6,
) {
    val id: String get() = offer.id
    fun palette(teamColor: Int? = null): DicePalette {
        val style = paletteFactory()
        return if (teamColor == null) style else style.copy(borderColor = teamColor)
    }
}
