package com.rekixo.ludopaws.feature.boards.rendering

import com.rekixo.ludopaws.feature.boards.content.classic.ClassicBoardRenderer
import com.rekixo.ludopaws.feature.boards.content.astral.AstralBoardRenderer
import com.rekixo.ludopaws.feature.boards.content.lagoon.CloudLagoonBoardRenderer
import com.rekixo.ludopaws.feature.boards.content.autumn.AutumnBoardRenderer
import com.rekixo.ludopaws.feature.boards.content.spring.BlossomBoardRenderer
import com.rekixo.ludopaws.feature.boards.content.winter.WinterBoardRenderer
import com.rekixo.ludopaws.feature.boards.content.monsoon.MonsoonBoardRenderer
import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition

internal object BoardRendererRegistry {
    private val factories = GeneratedBoardRendererFactories.factories
    fun supports(key: String): Boolean = key in factories
    fun create(definition: BoardDefinition): BoardRenderer =
        (factories[definition.rendererKey] ?: error("Missing board renderer: ${definition.rendererKey}"))()
}
