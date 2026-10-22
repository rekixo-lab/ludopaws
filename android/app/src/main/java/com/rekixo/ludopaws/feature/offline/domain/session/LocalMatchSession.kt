package com.rekixo.ludopaws

import android.content.Context

class LocalMatchSession(
    context: Context,
    override val mode: GameMode,
) : MatchSession {
    init {
        require(
            mode.isLocal,
        ) {
            "LocalMatchSession requires a local game mode"
        }
    }

    val engine =
        OfflineGameEngine(
            context =
                context.applicationContext,
            mode =
                mode,
        )

    override val rulesetId: String
        get() = engine.snapshot()?.rulesetId ?: OfflineLudoV5Binding.RULESET_ID

    override fun snapshot():
        MatchSnapshot? =
        engine.snapshot()
            ?.let(
                OfflineMatchInvariantPolicy::requireValid,
            )

    override fun localPlayerId():
        String? =
        engine.humanPlayerId()

    fun hasSavedGame():
        Boolean =
        engine.hasSavedGame()

    fun start(
        playerCount: Int,
        preferredColor: String,
        colorsBySeat: List<String>? = null,
    ): MatchSnapshot {
        MatchSpec.classic(
            mode =
                mode,
            playerCount =
                playerCount,
            rulesetId =
                OfflineLudoV3Binding.RULESET_ID,
        )
        return OfflineMatchInvariantPolicy.requireValid(
            engine.start(
                playerCount =
                    playerCount,
                preferredColor =
                    preferredColor,
                colorsBySeat = colorsBySeat,
            ),
        )
    }

    fun roll():
        MatchSnapshot =
        OfflineMatchInvariantPolicy.requireValid(
            engine.roll(),
        )

    fun move(
        tokenIndex: Int,
    ): MatchSnapshot =
        OfflineMatchInvariantPolicy.requireValid(
            engine.move(
                tokenIndex,
            ),
        )

    fun clear() {
        engine.clear()
    }
}
