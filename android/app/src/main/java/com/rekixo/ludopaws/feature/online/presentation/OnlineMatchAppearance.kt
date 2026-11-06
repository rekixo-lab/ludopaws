package com.rekixo.ludopaws.feature.online

import com.rekixo.ludopaws.PlayerSnapshot
import com.rekixo.ludopaws.feature.dice.domain.DiceRegistry

internal fun PlayerSnapshot.matchDiceStyle() =
    diceId?.let(DiceRegistry::find)?.takeIf { it.offer.contentAvailable }
        ?: DiceRegistry.resolve("dice_classic")
