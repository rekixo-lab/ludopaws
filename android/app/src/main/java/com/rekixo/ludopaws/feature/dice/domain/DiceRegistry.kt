package com.rekixo.ludopaws.feature.dice.domain

import com.rekixo.ludopaws.feature.dice.content.classic.ClassicDiceDefinition
import com.rekixo.ludopaws.feature.dice.content.pearl.PearlPawDiceDefinition
import com.rekixo.ludopaws.feature.dice.content.pumpkin.PumpkinDiceDefinition
import com.rekixo.ludopaws.feature.dice.content.diwali.DiwaliDiceDefinition
import com.rekixo.ludopaws.feature.dice.content.football.FootballDiceDefinition
import com.rekixo.ludopaws.feature.dice.content.cricket.CricketDiceDefinition
import com.rekixo.ludopaws.feature.dice.content.summers.SummersDiceDefinition
import com.rekixo.ludopaws.feature.dice.content.colors.ColorsDiceDefinition
import com.rekixo.ludopaws.feature.dice.content.heart.HeartDiceDefinition

object DiceRegistry {
    val all = GeneratedDiceDefinitions.all
    private val byId = all.associateBy { it.id }
    init { require(byId.size == all.size) { "Duplicate dice content ID" } }
    fun find(id: String): DiceDefinition? = byId[id]
    fun resolve(id: String): DiceDefinition = find(id) ?: byId.getValue("dice_classic")
}
