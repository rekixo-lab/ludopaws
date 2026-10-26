package com.rekixo.ludopaws.feature.boards.rendering

import com.rekixo.ludopaws.feature.boards.content.classic.ClassicBoardRenderer
import com.rekixo.ludopaws.feature.boards.content.astral.AstralBoardRenderer
import com.rekixo.ludopaws.feature.boards.content.lagoon.CloudLagoonBoardRenderer
import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition

internal object BoardRendererRegistry {
    private val factories: Map<String, () -> BoardRenderer> = mapOf(
        "classic" to { ClassicBoardRenderer() },
        "astral" to { AstralBoardRenderer() },
        "cloud_lagoon" to { CloudLagoonBoardRenderer() },
    )
    fun supports(key: String): Boolean = key in factories
    fun create(definition: BoardDefinition): BoardRenderer =
        (factories[definition.rendererKey] ?: error("Missing board renderer: ${definition.rendererKey}"))()
}
