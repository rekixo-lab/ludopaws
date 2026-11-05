package com.rekixo.ludopaws.feature.dice.rendering

import android.content.res.Resources
import com.rekixo.ludopaws.feature.dice.domain.DiceDefinition

internal object DiceRendererRegistry {
    private val factories = GeneratedDiceRendererFactories.factories
    fun supports(key: String): Boolean = key in factories
    fun create(definition: DiceDefinition, resources: Resources): DiceRenderer =
        (factories[definition.rendererKey] ?: error("Missing dice renderer: ${definition.rendererKey}"))(
            resources.displayMetrics.density,
        ).apply { prepare(resources) }
}
