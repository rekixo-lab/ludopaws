package com.rekixo.ludopaws.feature.dice.domain

import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic

data class DicePalette(val faceColors: IntArray, val borderColor: Int, val pipColor: Int)

/** Factory avoids sharing mutable color arrays across views, previews and tests. */
class DiceDefinition(
    val offer: StoreCosmetic,
    private val paletteFactory: () -> DicePalette,
    val rendererKey: String = "classic",
    val previewFace: Int = 6,
    // Optional authored sounds. Missing files preserve the classic roll/settle pair.
    val rollSoundPaths: List<String> = emptyList(),
    val settleSoundPaths: List<String> = emptyList(),
) {
    init {
        require((rollSoundPaths + settleSoundPaths).all { path ->
            path.startsWith("audio/sfx/") &&
                (path.endsWith(".wav", ignoreCase = true) || path.endsWith(".ogg", ignoreCase = true))
        }) { "Dice audio override must be an authored WAV/OGG SFX asset path" }
    }
    val id: String get() = offer.id
    fun palette(teamColor: Int? = null): DicePalette {
        val style = paletteFactory()
        return if (teamColor == null) style else style.copy(borderColor = teamColor)
    }
}
