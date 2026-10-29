package com.rekixo.ludopaws.feature.dice.rendering

import com.rekixo.ludopaws.feature.dice.content.classic.ClassicDiceRenderer
import com.rekixo.ludopaws.feature.dice.content.pearl.PearlPawDiceRenderer
import com.rekixo.ludopaws.feature.dice.domain.DiceDefinition

internal object DiceRendererRegistry {
    private val factories = GeneratedDiceRendererFactories.factories
    fun supports(key: String): Boolean = key in factories
    fun create(definition: DiceDefinition, density: Float): DiceRenderer =
        (factories[definition.rendererKey] ?: error("Missing dice renderer: ${definition.rendererKey}"))(density)
}
