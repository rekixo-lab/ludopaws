package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.dice.domain.DiceDefinition
import com.rekixo.ludopaws.feature.dice.domain.DiceRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class LudoPawsDiceAudioOverridesTest {
    @Test
    fun oldDiceDefinitionsRemainClassicAudioByDefault() {
        assertTrue(DiceRegistry.all.all { it.rollSoundPaths.isEmpty() && it.settleSoundPaths.isEmpty() })
    }

    @Test
    fun selectedDiceDefinitionsResolveWithoutChangingTheGameplayOrPurchaseId() {
        for (definition in DiceRegistry.all) {
            val resolved: DiceDefinition = DiceRegistry.resolve(definition.id)
            assertEquals(definition.id, resolved.offer.id)
        }
    }
}
