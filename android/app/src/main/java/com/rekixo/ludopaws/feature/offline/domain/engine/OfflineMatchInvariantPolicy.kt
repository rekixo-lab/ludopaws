package com.rekixo.ludopaws

/**
 * Fail-fast consistency checks for local Ludo snapshots.
 *
 * This policy deliberately validates only contracts that are already guaranteed
 * by the local authoritative engine. It does not make moves, roll dice, or
 * derive alternative gameplay rules. Keeping the checks pure makes them usable
 * both by the live local session and by long-running match simulations.
 */
object OfflineMatchInvariantPolicy {
    fun requireValid(
        state: MatchSnapshot,
    ): MatchSnapshot {
        require(state.status == "ACTIVE" || state.status == "FINISHED") {
            "Local match has an unsupported status"
        }
        require(state.players.size in 2..4) {
            "Local match must contain two to four players"
        }
        require(state.turnSeat in state.players.indices) {
            "Local match turn seat is outside the player list"
        }
        require(state.randomEventIndex >= 0) {
            "Local match random event index must be non-negative"
        }
        require(state.rulesetId in setOf(OfflineLudoV3Binding.RULESET_ID, OfflineLudoV5Binding.RULESET_ID)) {
            "Local match is not bound to the current offline ruleset"
        }

        val ids = state.players.map { it.playerId }
        require(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) {
            "Local match player identities must be non-empty and unique"
        }

        val colors = state.players.map { it.color }
        require(
            colors.all { it in OfflinePlayerLayout.COLORS } &&
                colors.distinct().size == colors.size,
        ) {
            "Local match player colors must be valid and unique"
        }

        state.players.forEachIndexed { seat, player ->
            require(player.seat == seat) {
                "Local match player seat does not match list position"
            }
            require(player.tokens.size == 4) {
                "Each local player must have exactly four tokens"
            }
            require(
                player.tokens.all { position ->
                    position in YARD_POSITION..LudoPathEncoding.HOME_POSITION
                },
            ) {
                "Local match contains an invalid token position"
            }
        }

        validateHistory(
            state = state,
            playerIds = ids.toSet(),
        )
        validatePendingRoll(state)
        validateWinner(state)

        return state
    }

    private fun validateHistory(
        state: MatchSnapshot,
        playerIds: Set<String>,
    ) {
        var previousEventIndex = -1
        state.history.forEach { event ->
            require(event.eventIndex >= 0) {
                "Local history event index must be non-negative"
            }
            require(event.eventIndex > previousEventIndex) {
                "Local history event indexes must be strictly increasing"
            }
            require(event.eventIndex < state.randomEventIndex) {
                "Local history cannot include a future random event"
            }
            require(event.playerId in playerIds) {
                "Local history references a player outside the match"
            }
            event.randomOutcome?.let { outcome ->
                require(outcome in 1..6) {
                    "Local history random outcome is outside 1..6"
                }
            }
            (event.effectiveOutcome ?: event.outcome)?.let { outcome ->
                require(outcome in 1..6) {
                    "Local history effective outcome is outside 1..6"
                }
            }
            event.moveTokenIndex?.let { tokenIndex ->
                require(tokenIndex in 0..3) {
                    "Local history move token index is outside 0..3"
                }
            }
            require(event.captures >= 0) {
                "Local history capture count cannot be negative"
            }
            previousEventIndex = event.eventIndex
        }
    }

    private fun validatePendingRoll(
        state: MatchSnapshot,
    ) {
        val pending = state.pendingRoll ?: return
        require(state.status == "ACTIVE") {
            "Finished local match cannot retain a pending roll"
        }
        require(pending.status == "RESOLVED") {
            "Local pending roll must already be resolved"
        }
        require(pending.seat == state.turnSeat) {
            "Local pending roll belongs to a different turn seat"
        }
        require(pending.eventIndex == state.randomEventIndex - 1) {
            "Local pending roll event index is not the latest random event"
        }
        val outcome = requireNotNull(pending.outcome) {
            "Local pending roll is missing its outcome"
        }
        require(outcome in 1..6) {
            "Local pending roll outcome is outside 1..6"
        }
        require(!pending.proofDigest.isNullOrBlank()) {
            "Local pending roll is missing its proof digest"
        }
        require(pending.legalTokenIndexes.isNotEmpty()) {
            "Local pending roll must expose at least one legal token"
        }
        require(pending.legalTokenIndexes.all { it in 0..3 }) {
            "Local pending roll contains an invalid legal token index"
        }

        val activePlayer = state.players[state.turnSeat]
        val expectedLegal =
            activePlayer.tokens
                .indices
                .filterTo(linkedSetOf()) { tokenIndex ->
                    LudoPathEncoding.destinationForRoll(
                        position = activePlayer.tokens[tokenIndex],
                        roll = outcome,
                    ) != null
                }
        require(pending.legalTokenIndexes == expectedLegal) {
            "Local pending legal-token set does not match canonical path legality"
        }

        val historyEvent =
            state.history.lastOrNull {
                it.eventIndex == pending.eventIndex
            }
        requireNotNull(historyEvent) {
            "Local pending roll has no matching history event"
        }
        require(historyEvent.playerId == activePlayer.playerId) {
            "Local pending history event belongs to a different player"
        }
        require(
            (historyEvent.effectiveOutcome ?: historyEvent.outcome) == outcome,
        ) {
            "Local pending outcome disagrees with its history receipt"
        }
        require(historyEvent.proofDigest == pending.proofDigest) {
            "Local pending proof disagrees with its history receipt"
        }
    }

    private fun validateWinner(
        state: MatchSnapshot,
    ) {
        when (state.status) {
            "ACTIVE" ->
                require(state.winnerPlayerId == null) {
                    "Active local match cannot already expose a winner"
                }

            "FINISHED" -> {
                val winnerId = requireNotNull(state.winnerPlayerId) {
                    "Finished local match must expose a winner"
                }
                require(state.pendingRoll == null) {
                    "Finished local match cannot retain a pending roll"
                }
                val winner =
                    state.players.firstOrNull {
                        it.playerId == winnerId
                    }
                requireNotNull(winner) {
                    "Finished local match winner is not seated"
                }
                require(
                    winner.tokens.all {
                        it == LudoPathEncoding.HOME_POSITION
                    },
                ) {
                    "Finished local match winner does not have all four tokens home"
                }
            }
        }
    }

    private const val YARD_POSITION = -1
}
