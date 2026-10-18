package com.rekixo.ludopaws

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchSpecV3Test {
    @Test
    fun localClassicSpecCanExplicitlyUseRulesetV3() {
        val spec =
            MatchSpec.classic(
                mode = GameMode.COMPUTER,
                playerCount = 2,
                rulesetId = ClassicRuleset.V3_ID,
            )

        assertEquals(ClassicRuleset.V3_ID, spec.rulesetId)
        assertEquals(ClassicRuleset.V3_ID, spec.rulesetId)
    }
}
