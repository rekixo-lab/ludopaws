package com.rekixo.ludopaws

/** A counter of -1 is inactive; 0..2 tracks eligible rolls without a six. */
object LudoComebackPolicy {
    const val RULESET_ID = "ludoproof-standard-v7"
    const val TEAM_RULESET_ID = "ludoproof-team-v6"

    const val YARD_RULESET_ID = ClassicRuleset.V8_ID
    const val TEAM_YARD_RULESET_ID = "ludoproof-team-v7"
    const val TEAM_YARD_RULESET_KEY = "TEAM_UP_V7"

    fun countsWithoutCapture(rulesetId: String): Boolean =
        rulesetId == YARD_RULESET_ID || rulesetId == TEAM_YARD_RULESET_ID

    fun enabled(rulesetId: String): Boolean =
        countsWithoutCapture(rulesetId) ||
            rulesetId in setOf(RULESET_ID, TEAM_RULESET_ID, QuickMatchPolicy.RULESET_ID)

    fun stranded(tokens: List<Int>): Boolean =
        tokens.any { it == -1 } && tokens.all { it == -1 || it == 57 }

    fun applies(tokens: List<Int>, failedRolls: Int, outcome: Int): Boolean =
        stranded(tokens) && failedRolls == 2 && outcome in 1..5

    fun hint(state: MatchSnapshot): String? {
        if (!enabled(state.rulesetId)) return null
        if (state.status != "ACTIVE") return null
        if (state.pendingRoll?.comebackBonusApplied == true) return "Comeback bonus • one paw returns • no extra turn"
        if (state.pendingRoll != null) return null
        val seat = state.actingSeat ?: state.turnSeat
        val player = state.players.getOrNull(seat) ?: return null
        val failed = state.comebackWaits.getOrNull(seat) ?: return null
        if (failed < 0 || !stranded(player.tokens)) return null
        return if (failed == 2) "Comeback ready • one paw returns on this roll"
        else "Roll a 6 • comeback guaranteed within ${3 - failed} turns"
    }
}
