package com.rekixo.ludopaws

object ClassicRuleset {
    const val KEY = "CLASSIC_V1"
    const val ID = "ludoproof-standard-v1"

    const val V2_KEY = "CLASSIC_V2"
    const val V2_ID = "ludoproof-standard-v2"

    const val V3_KEY = "CLASSIC_V3"
    const val V3_ID = "ludoproof-standard-v3"

    const val V5_ID = "ludoproof-standard-v5"
    const val V5_KEY = "CLASSIC_V5"

    val SUPPORTED_IDS =
        setOf(
            ID,
            V2_ID,
            V3_ID,
            V5_ID,
        )

    const val BOARD_TRACK_CELLS = 52
    const val HOME_POSITION = 57
    const val TOKENS_PER_PLAYER = 4
}

data class MatchSpec(
    val mode: GameMode,
    val playerCount: Int,
    val rulesetId: String = ClassicRuleset.ID,
) {
    init {
        require(mode.supportsPlayerCount(playerCount)) {
            mode.wireValue +
                " does not support " +
                playerCount +
                " players"
        }
        require(rulesetId in ClassicRuleset.SUPPORTED_IDS) {
            "Unsupported ruleset: $rulesetId"
        }
    }

    val authority: MatchAuthority
        get() = mode.authority

    val ranked: Boolean
        get() = mode.ranked

    companion object {
        fun classic(
            mode: GameMode,
            playerCount: Int,
            rulesetId: String = ClassicRuleset.ID,
        ): MatchSpec =
            MatchSpec(
                mode = mode,
                playerCount = playerCount,
                rulesetId = rulesetId,
            )
    }
}
