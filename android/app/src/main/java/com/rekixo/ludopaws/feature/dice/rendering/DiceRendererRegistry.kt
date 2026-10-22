package com.rekixo.ludopaws.feature.dice.rendering

import com.rekixo.ludopaws.feature.dice.content.classic.ClassicDiceRenderer
import com.rekixo.ludopaws.feature.dice.domain.DiceDefinition

internal object DiceRendererRegistry {
    private val factories: Map<String, (Float) -> DiceRenderer> = mapOf("classic" to { ClassicDiceRenderer(it) })
    fun supports(key: String): Boolean = key in factories
    fun create(definition: DiceDefinition, density: Float): DiceRenderer =
        (factories[definition.rendererKey] ?: error("Missing dice renderer: ${definition.rendererKey}"))(density)
}
