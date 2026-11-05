package com.rekixo.ludopaws.feature.dice.content.environment

import com.rekixo.ludopaws.feature.dice.domain.DiceDefinition
import com.rekixo.ludopaws.feature.dice.domain.DicePalette
import com.rekixo.ludopaws.feature.store.domain.cosmetic
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind

/** Each skin has real local artwork; unlocks use the existing server-authoritative Gems flow. */
private fun environmentDice(id: String, title: String, theme: EnvironmentDiceTheme, preview: Int) = DiceDefinition(
    offer = cosmetic(id, title, CosmeticCategory.DICE, CosmeticUnlockKind.GEMS,
        "${preview}", theme.light, theme.dark, gems = 100),
    paletteFactory = { DicePalette(intArrayOf(theme.light, theme.base, theme.dark), theme.rim, theme.pip) },
    rendererKey = theme.key,
    previewFace = preview,
)

object ForestDiceDefinition {
    val content = environmentDice("dice_forest_dew", "FOREST DEW", EnvironmentDiceTheme.FOREST, 5)
}

object LagoonDiceDefinition {
    val content = environmentDice("dice_lagoon_pearl", "LAGOON PEARL", EnvironmentDiceTheme.LAGOON, 4)
}

object FrostDiceDefinition {
    val content = environmentDice("dice_frost_crystal", "FROST CRYSTAL", EnvironmentDiceTheme.FROST, 6)
}

object StarlightDiceDefinition {
    val content = environmentDice("dice_starlight", "STARLIGHT", EnvironmentDiceTheme.STARLIGHT, 3)
}
