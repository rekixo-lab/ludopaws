package com.rekixo.ludopaws

class RemoteMatchSession(
    override val mode: GameMode,
    private val snapshotProvider:
        () -> MatchSnapshot?,
    private val playerIdProvider:
        () -> String?,
    private val api: GameApi = GameApi(),
) : MatchSession {
    init {
        require(
            mode.isRemote,
        ) {
            "RemoteMatchSession requires a remote game mode"
        }
    }

    override val rulesetId:
        String
        get() =
            snapshot()
                ?.rulesetId
                ?: ClassicRuleset.ID

    override fun snapshot():
        MatchSnapshot? =
        snapshotProvider()

    override fun localPlayerId():
        String? =
        playerIdProvider()

    /** Runs on the gameplay executor, through the same transport for all remote modes. */
    fun roll(matchId: String, playerToken: String, requestId: String, expectedEventIndex: Int): org.json.JSONObject =
        api.roll(matchId, playerToken, requestId, expectedEventIndex)

    fun move(
        matchId: String,
        playerToken: String,
        tokenIndex: Int,
        eventIndex: Int,
        requestId: String,
    ): org.json.JSONObject =
        api.move(matchId, playerToken, tokenIndex, eventIndex, requestId)
}
