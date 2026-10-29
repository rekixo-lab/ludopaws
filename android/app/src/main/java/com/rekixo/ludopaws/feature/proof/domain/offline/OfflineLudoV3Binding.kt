package com.rekixo.ludopaws

data class OfflineV3BindingPlayer(
    val playerId: String,
    val color: String,
    val tokens: List<Int>,
)

data class OfflineV3BindingHistory(
    val eventIndex: Int,
    val playerId: String,
    val clientCommitment: String?,
    val proofDigest: String?,
    val randomOutcome: Int?,
    val effectiveOutcome: Int?,
    val moveTokenIndex: Int?,
    val captures: Int,
    val fairnessDigest: String? = null,
)

/**
 * Offline gameplay binding for ruleset v3.
 *
 * EntroNex v4 still proves the unbiased random outcome. Ruleset v3 then applies
 * one deterministic gameplay transform: a player's first roll, while all four
 * tokens are still in the starting yard, is treated as six. Both the policy and
 * the opening-roll-consumed state are included in hashed material, so the bonus
 * cannot silently reappear later in the match.
 */
object OfflineLudoV3Binding {
    const val RULESET_ID =
        "ludoproof-standard-v3"

    val WORLD =
        LocalV4WorldConfig(
            cellsPerOutcome = 16,
            timelineTicks = 512,
            epochCount = 8,
            probeCount = 3,
        )

    fun deriveRoll(
        matchId: String,
        status: String,
        players: List<OfflineV3BindingPlayer>,
        turnSeat: Int,
        eventIndex: Int,
        consecutiveSixes: List<Int>,
        openingRollConsumed: List<Boolean>,
        winnerPlayerId: String?,
        history: List<OfflineV3BindingHistory>,
        rulesetId: String = RULESET_ID,
        opponentStackCapture: String = "CAPTURE_ALL_ON_UNSAFE_CELL",
    ): LocalV4Result {
        require(eventIndex >= 0)
        require(openingRollConsumed.size == players.size) {
            "opening-roll state must match player count"
        }

        val activePlayer =
            players.getOrNull(turnSeat)
                ?: error("offline turn seat is invalid")

        val actorHash =
            EntroNexV4Local.sha256Hex(
                "ludoproof:actor:v1:" + activePlayer.playerId,
            )

        val authoritativeState =
            mapOf(
                "schemaVersion" to 1,
                "matchId" to matchId,
                "status" to status,
                "players" to
                    players.map { player ->
                        mapOf(
                            "playerId" to player.playerId,
                            "color" to player.color,
                            "tokens" to player.tokens,
                        )
                    },
                "turnSeat" to turnSeat,
                "randomEventIndex" to eventIndex,
                "consecutiveSixes" to consecutiveSixes,
                "openingRollConsumed" to openingRollConsumed,
                "winnerPlayerId" to winnerPlayerId,
                "history" to
                    history.map { event ->
                        mapOf(
                            "eventIndex" to event.eventIndex,
                            "playerId" to event.playerId,
                            "clientCommitment" to event.clientCommitment,
                            "proofDigest" to event.proofDigest,
                            "randomOutcome" to event.randomOutcome,
                            "effectiveOutcome" to event.effectiveOutcome,
                            "moveTokenIndex" to event.moveTokenIndex,
                            "captures" to event.captures,
                            "fairnessDigest" to event.fairnessDigest,
                        )
                    },
            )

        val previousStateHash =
            EntroNexV4Local.sha256Hex(
                "entronex:v4:game-state:" +
                    EntroNexV4Local.jcs(authoritativeState),
            )

        val rulesetHash =
            EntroNexV4Local.sha256Hex(
                "entronex:v4:game-ruleset:" +
                    EntroNexV4Local.jcs(ruleset(rulesetId, opponentStackCapture)),
            )

        val config =
            LocalV4Config(
                outcomes = listOf(1, 2, 3, 4, 5, 6),
                context =
                    LocalV4Context(
                        applicationId = "ludoproof",
                        sessionId = matchId,
                        eventId = "roll:$eventIndex",
                        eventType = "DICE_ROLL",
                        eventIndex = eventIndex.toLong(),
                        subjectHash = actorHash,
                        previousStateHash = previousStateHash,
                        metadataDigest = rulesetHash,
                    ),
                world = WORLD,
            )

        return EntroNexV4Local.resolve(
            roundId = EntroNexV4Local.randomRoundId(),
            serverSeed = EntroNexV4Local.randomSeedHex(),
            clientSeed = EntroNexV4Local.randomSeedHex(),
            config = config,
        )
    }

    private fun ruleset(rulesetId: String, opponentStackCapture: String):
        Map<String, Any?> =
        mapOf(
            "id" to rulesetId,
            "boardTrackCells" to 52,
            "homePosition" to 57,
            "tokensPerPlayer" to 4,
            "startOffsets" to
                mapOf(
                    "RED" to 0,
                    "GREEN" to 13,
                    "YELLOW" to 26,
                    "BLUE" to 39,
                ),
            "safeGlobalCells" to
                listOf(0, 8, 13, 21, 26, 34, 39, 47),
            "leaveYardRequiresSix" to true,
            "exactRollToHome" to true,
            "extraTurnOnSix" to true,
            "extraTurnOnCapture" to true,
            "extraTurnOnHome" to true,
            "threeConsecutiveSixesForfeit" to true,
            "captureOnSafeCell" to false,
            "ownTokenStacking" to "ALLOWED",
            "opponentStackCapture" to opponentStackCapture,
            "openingRollPolicy" to "GUARANTEED_SIX_ONCE_PER_PLAYER",
            "openingRollRequiresAllTokensInYard" to true,
            "openingRollCountsTowardConsecutiveSixes" to false,
            "singleLegalMovePolicy" to "AUTO_MOVE",
        ).let { rules ->
            if (rulesetId == OfflineLudoV6Binding.RULESET_ID) rules + ("receiptHistoryWindow" to OfflineLudoV6Binding.HISTORY_WINDOW)
            else rules
        }
}
