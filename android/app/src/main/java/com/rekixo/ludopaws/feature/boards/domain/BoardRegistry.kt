package com.rekixo.ludopaws.feature.boards.domain

import com.rekixo.ludopaws.feature.boards.content.classic.ClassicBoardDefinition
import com.rekixo.ludopaws.feature.boards.content.astral.AstralBoardDefinition
import com.rekixo.ludopaws.feature.boards.content.lagoon.CloudLagoonBoardDefinition
import com.rekixo.ludopaws.feature.boards.content.autumn.AutumnBoardDefinition
import com.rekixo.ludopaws.feature.boards.content.spring.BlossomBoardDefinition
import com.rekixo.ludopaws.feature.boards.content.winter.WinterBoardDefinition
import com.rekixo.ludopaws.feature.boards.content.monsoon.MonsoonBoardDefinition
import com.rekixo.ludopaws.feature.boards.content.checkers.CheckersBoardDefinition
import com.rekixo.ludopaws.feature.boards.content.chess.ChessBoardDefinition
import com.rekixo.ludopaws.feature.boards.content.diwali.DiwaliBoardDefinition
import com.rekixo.ludopaws.feature.boards.content.denim.DenimBoardDefinition
import com.rekixo.ludopaws.feature.boards.content.neon.NeonBoardDefinition
import com.rekixo.ludopaws.feature.boards.content.pirate.PirateBoardDefinition
import com.rekixo.ludopaws.feature.boards.content.alien.AlienBoardDefinition
import com.rekixo.ludopaws.feature.boards.content.penguin.PenguinBoardDefinition

object BoardRegistry {
    val all = listOf(
        ClassicBoardDefinition.content,
        AutumnBoardDefinition.content,
        BlossomBoardDefinition.content,
        WinterBoardDefinition.content,
        MonsoonBoardDefinition.content,
        CloudLagoonBoardDefinition.content,
        AstralBoardDefinition.content,
        CheckersBoardDefinition.content,
        ChessBoardDefinition.content,
        DiwaliBoardDefinition.content,
        DenimBoardDefinition.content,
        NeonBoardDefinition.content,
        PirateBoardDefinition.content,
        AlienBoardDefinition.content,
        PenguinBoardDefinition.content,
    )
    private val byId = all.associateBy { it.id }
    init { require(byId.size == all.size) { "Duplicate board content ID" } }
    fun find(id: String): BoardDefinition? = byId[id]
    fun resolve(id: String): BoardDefinition = find(id) ?: byId.getValue("board_classic")
}
