package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.dice.domain.DiceRegistry

internal typealias DicePalette = com.rekixo.ludopaws.feature.dice.domain.DicePalette

/** Existing call sites and team-border behavior remain compatible. */
internal object DiceStylePalettePolicy {
    fun resolve(styleId: String, teamColor: Int?): DicePalette = DiceRegistry.resolve(styleId).palette(teamColor)
}
