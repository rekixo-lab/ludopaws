package com.rekixo.ludopaws

import android.content.Context
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileGameMode
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileMatchSource
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class OfflineGameEngine(
    context: Context,
    val mode: GameMode = GameMode.PASS_AND_PLAY,
) {
    constructor(
        context: Context,
        computerMode: Boolean,
    ) : this(
        context = context,
        mode = if (computerMode) GameMode.COMPUTER else GameMode.PASS_AND_PLAY,
    )

    init {
        require(mode.isLocal) {
            "OfflineGameEngine requires a local game mode"
        }
    }

    private val computerMode: Boolean
        get() = mode == GameMode.COMPUTER

    private val profileContext = context.applicationContext
    private val profileStore =
        ProfileStore(context.applicationContext)

    private val prefs =
        context.getSharedPreferences(
            if (computerMode) PREFS_NAME_COMPUTER else PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    private var state: LocalState? = synchronized(checkpoints) {
        if (checkpoints.containsKey(mode)) checkpoints[mode]?.frozenCopy()
        else loadState().also { checkpoints[mode] = it?.frozenCopy() }
    }

    fun hasSavedGame(): Boolean = state != null

    fun snapshot(): MatchSnapshot? {
        state?.takeIf { it.status == "FINISHED" }?.let { current ->
            checkpointWriter.execute { reconcileFinishedResult(current) }
        }
        return state?.toSnapshot()
    }

    private fun reconcileFinishedResult(current: LocalState) {
        if (!current.profileOwnerKnown || current.status != "FINISHED") return
        runCatching {
            com.rekixo.ludopaws.feature.offline.data.local.OfflineResultRecovery.record(
                profileContext, current.matchId, current.profileOwnerAccountId,
                current.winnerPlayerId == current.players.firstOrNull()?.playerId, computerMode,
            )
        }.onFailure { android.util.Log.w("OfflineSave", "Match statistics remain pending", it) }
    }

    fun lastRandomnessAudit(): OfflineRandomnessAudit? = state?.lastAudit

    fun activePlayerId(): String? =
        state?.players?.getOrNull(state?.turnSeat ?: -1)?.playerId

    fun humanPlayerId(): String? = state?.players?.firstOrNull()?.playerId

    fun humanColor(): String? = state?.players?.firstOrNull()?.color

    fun isComputerPlayer(playerId: String?): Boolean =
        computerMode && playerId?.startsWith("cpu-player-") == true

    fun start(
        playerCount: Int,
        preferredColor: String = "RED",
        colorsBySeat: List<String>? = null,
    ): MatchSnapshot {
        MatchSpec.classic(
            mode = mode,
            playerCount = playerCount,
        )
        require(preferredColor in OfflinePlayerLayout.COLORS) {
            "Unsupported offline color"
        }

        // Honor the selected Pass & Play seat colors without rewriting them.
        if (colorsBySeat != null) {
            require(colorsBySeat.size == playerCount)
            require(colorsBySeat.all { it in OfflinePlayerLayout.COLORS })
            require(colorsBySeat.distinct().size == playerCount)
            require(colorsBySeat.first() == preferredColor)
        }
        val colorOrder = colorsBySeat?.toList()
            ?: OfflinePlayerLayout.colorsFor(playerCount, preferredColor)
        val humanName =
            profileStore.snapshot().displayName.ifBlank { "Player 1" }

        val players =
            (0 until playerCount)
                .map { seat ->
                    val isCpu = computerMode && seat > 0
                    LocalPlayer(
                        playerId =
                            if (isCpu) {
                                "cpu-player-${seat + 1}"
                            } else {
                                "offline-player-${seat + 1}"
                            },
                        displayName =
                            when {
                                seat == 0 -> humanName
                                isCpu -> "Robot $seat"
                                else -> "Player ${seat + 1}"
                            },
                        color = colorOrder[seat],
                        tokens = mutableListOf(-1, -1, -1, -1),
                    )
                }
                .toMutableList()

        state =
            LocalState(
                profileOwnerKnown = true,
                profileOwnerAccountId = com.rekixo.ludopaws.feature.account.data.AccountSessionStore(profileContext).load()?.accountId,
                matchId = "offline-${UUID.randomUUID()}",
                status = "ACTIVE",
                players = players,
                turnSeat = 0,
                eventIndex = 0,
                consecutiveSixes = MutableList(playerCount) { 0 },
                openingRollConsumed = MutableList(playerCount) { false },
                pendingOutcome = null,
                pendingLegal = mutableSetOf(),
                pendingEventIndex = null,
                pendingRoundId = null,
                pendingClientCommitment = null,
                pendingProofDigest = null,
                winnerPlayerId = null,
                finishedPlayerIds = mutableListOf(),
                history = mutableListOf(),
                lastAudit = null,
                revision = 1,
            )
        persist()
        return requireNotNull(snapshot())
    }

    internal data class PreparedRoll(val matchId: String, val revision: Int, val result: LocalV4Result, val audit: OfflineRandomnessAudit)

    fun roll(): MatchSnapshot = applyPreparedRoll(prepareRoll().invoke())

    internal fun prepareRoll(): () -> PreparedRoll {
        val current = requireActiveState().frozenCopy()
        check(current.pendingOutcome == null) {
            "Move a highlighted token before rolling again"
        }

        return {
        val seat = current.turnSeat
        val event = current.eventIndex
        val player = current.players[seat]

        val result =
            OfflineLudoV3Binding.deriveRoll(
                rulesetId = current.rulesetId,
                opponentStackCapture = if (current.rulesetId in setOf(OfflineLudoV5Binding.RULESET_ID, OfflineLudoV6Binding.RULESET_ID))
                    OfflineLudoV5Binding.OPPONENT_STACK_CAPTURE else "CAPTURE_ALL_ON_UNSAFE_CELL",
                matchId = current.matchId,
                status = current.status,
                players =
                    current.players.map { bindingPlayer ->
                        OfflineV3BindingPlayer(
                            playerId = bindingPlayer.playerId,
                            color = bindingPlayer.color,
                            tokens = bindingPlayer.tokens.toList(),
                        )
                    },
                turnSeat = current.turnSeat,
                eventIndex = event,
                consecutiveSixes = current.consecutiveSixes.toList(),
                openingRollConsumed = current.openingRollConsumed.toList(),
                winnerPlayerId = current.winnerPlayerId,
                history =
                    current.history.map { history ->
                        OfflineV3BindingHistory(
                            eventIndex = history.eventIndex,
                            playerId = history.playerId,
                            clientCommitment = history.clientCommitment,
                            proofDigest = history.proofDigest,
                            randomOutcome = history.randomOutcome,
                            effectiveOutcome = history.outcome,
                            moveTokenIndex = history.moveTokenIndex,
                            captures = history.captures,
                            fairnessDigest = history.fairnessDigest,
                        )
                    },
            )

        check(result.outcome in 1..6) {
            "Local EntroNex v4 outcome is outside the locked Ludo range"
        }
        check(EntroNexV4Local.verify(result)) {
            "Local EntroNex v4 proof verification failed"
        }

        val audit =
            OfflineRandomnessAudit.fromResult(
                eventIndex = event,
                result = result,
            )
        check(audit.verify()) {
            "Local EntroNex v4 audit verification failed"
        }

        PreparedRoll(current.matchId, current.revision, result, audit)
        }
    }

    internal fun applyPreparedRoll(prepared: PreparedRoll): MatchSnapshot {
        val current = requireActiveState()
        check(current.matchId == prepared.matchId && current.revision == prepared.revision && current.pendingOutcome == null) { "Offline roll was superseded" }
        val seat = current.turnSeat
        val event = current.eventIndex
        val player = current.players[seat]
        val result = prepared.result
        val audit = prepared.audit
        val randomOutcome = result.outcome
        val openingRollApplied =
            LudoTurnAutomationPolicy.shouldGuaranteeOpeningSix(
                tokens = player.tokens,
                openingRollConsumed = current.openingRollConsumed[seat],
            )

        // The entitlement is consumed by the player's first resolved roll. It
        // never becomes available again if all tokens are captured back home.
        current.openingRollConsumed[seat] = true

        val outcome =
            LudoTurnAutomationPolicy.effectiveOutcome(
                verifiedOutcome = randomOutcome,
                guaranteeOpeningSix = openingRollApplied,
            )

        current.eventIndex += 1
        current.lastAudit = audit

        // The deterministic opening bonus is deliberately excluded from the
        // three-consecutive-sixes penalty.
        when {
            openingRollApplied ->
                current.consecutiveSixes[seat] = 0
            outcome == 6 ->
                current.consecutiveSixes[seat] =
                    current.consecutiveSixes[seat] + 1
            else ->
                current.consecutiveSixes[seat] = 0
        }

        val fairnessMaterial =
            OfflineFairnessChain.seal(
                OfflineFairnessMaterial(
                    eventIndex = event,
                    playerId = player.playerId,
                    color = player.color,
                    roundId = result.roundId,
                    serverCommitment = result.serverCommitment,
                    clientCommitment = result.clientCommitment,
                    actorHash = result.config.context.subjectHash,
                    previousStateHash = result.config.context.previousStateHash,
                    rulesetHash = result.config.context.metadataDigest,
                    proofDigest = result.proofDigest,
                    // The proof receipt always records the actual verified
                    // EntroNex result. Gameplay may apply the versioned opening
                    // bonus separately.
                    outcome = randomOutcome,
                ),
                current.history
                    .asReversed()
                    .firstOrNull { it.fairnessDigest != null }
                    ?.fairnessDigest,
            )
        check(OfflineFairnessChain.verify(fairnessMaterial)) {
            "Local fairness receipt verification failed"
        }

        current.history +=
            LocalHistoryEvent(
                eventIndex = event,
                playerId = player.playerId,
                color = player.color,
                roundId = result.roundId,
                serverCommitment = result.serverCommitment,
                clientCommitment = result.clientCommitment,
                actorHash = result.config.context.subjectHash,
                previousStateHash = result.config.context.previousStateHash,
                rulesetHash = result.config.context.metadataDigest,
                proofDigest = result.proofDigest,
                fairnessProtocol = fairnessMaterial.fairnessProtocol,
                previousFairnessDigest = fairnessMaterial.previousFairnessDigest,
                fairnessDigest = fairnessMaterial.fairnessDigest,
                randomOutcome = randomOutcome,
                outcome = outcome,
                openingRollApplied = openingRollApplied,
                moveTokenIndex = null,
                captures = 0,
            )

        if (
            !openingRollApplied &&
            outcome == 6 &&
            current.consecutiveSixes[seat] >= 3
        ) {
            current.consecutiveSixes[seat] = 0
            clearPending(current)
            advanceTurn(current)
            current.revision += 1
            persist()
            return current.toSnapshot()
        }

        val legal =
            legalTokenIndexes(
                player.tokens,
                outcome,
            )

        if (legal.isEmpty()) {
            clearPending(current)
            if (outcome != 6) {
                advanceTurn(current)
            }
            current.revision += 1
            persist()
            return current.toSnapshot()
        }

        current.pendingOutcome = outcome
        current.pendingLegal = legal.toMutableSet()
        current.pendingEventIndex = event
        current.pendingRoundId = result.roundId
        current.pendingClientCommitment = result.clientCommitment
        current.pendingProofDigest = result.proofDigest
        current.revision += 1
        persist()
        return current.toSnapshot()
    }

    fun move(tokenIndex: Int): MatchSnapshot {
        val current = requireActiveState()
        val outcome =
            current.pendingOutcome
                ?: error("Roll the dice before moving")
        val event =
            current.pendingEventIndex
                ?: error("Offline roll state is incomplete")

        require(tokenIndex in current.pendingLegal) {
            "That token cannot use this roll"
        }

        val seat = current.turnSeat
        val player = current.players[seat]
        val oldPosition = player.tokens[tokenIndex]
        val destination =
            LudoPathEncoding.destinationForRoll(
                position = oldPosition,
                roll = outcome,
            ) ?: error("Offline move exceeded the home path")

        player.tokens[tokenIndex] = destination
        val captures =
            captureOpponents(
                current = current,
                movingSeat = seat,
                destination = destination,
            )

        val historyIndex =
            current.history.indexOfLast { it.eventIndex == event }
        if (historyIndex >= 0) {
            val previous = current.history[historyIndex]
            current.history[historyIndex] =
                previous.copy(
                    moveTokenIndex = tokenIndex,
                    captures = captures,
                )
        }

        clearPending(current)

        if (player.tokens.all { it == HOME_POSITION }) {
            if (player.playerId !in current.finishedPlayerIds) {
                current.finishedPlayerIds += player.playerId
            }

            if (
                current.finishedPlayerIds.size >=
                current.players.size - 1
            ) {
                current.status = "FINISHED"
                // Keep the first finisher as the match winner. The last remaining
                // player is the final placement and the match ends only then.
                current.winnerPlayerId =
                    current.finishedPlayerIds.firstOrNull()
                current.revision += 1
                persist()
                com.rekixo.ludopaws.feature.referrals.ReferralPopupSession.current.completedMatch(current.matchId)
                return current.toSnapshot()
            }

            // A player who has completed all four paws is placed and removed
            // from turn rotation. The remaining players continue normally.
            advanceTurn(current)
            current.revision += 1
            persist()
            return current.toSnapshot()
        }

        val extraTurn =
            LudoExtraTurnPolicy.grantsExtraTurn(
                roll = outcome,
                captures = captures,
                destination = destination,
            )
        if (!extraTurn) {
            advanceTurn(current)
        }

        current.revision += 1
        persist()
        return current.toSnapshot()
    }

    fun clear() {
        state = null
        synchronized(checkpoints) {
            checkpoints[mode] = null
            checkpointWrites.submit(mode) {
                runCatching { check(prefs.edit().clear().commit()) }
                    .onFailure { android.util.Log.e("OfflineSave", "Could not clear checkpoint", it) }
            }
        }
    }

    private fun requireActiveState(): LocalState {
        val current = state ?: error("Start an offline game first")
        check(current.status == "ACTIVE") {
            "Offline game is already finished"
        }
        return current
    }

    private fun clearPending(current: LocalState) {
        current.pendingOutcome = null
        current.pendingLegal.clear()
        current.pendingEventIndex = null
        current.pendingRoundId = null
        current.pendingClientCommitment = null
        current.pendingProofDigest = null
    }

    private fun legalTokenIndexes(
        tokens: List<Int>,
        roll: Int,
    ): Set<Int> =
        buildSet {
            tokens.forEachIndexed { index, position ->
                if (
                    LudoPathEncoding.destinationForRoll(
                        position = position,
                        roll = roll,
                    ) != null
                ) {
                    add(index)
                }
            }
        }

    private fun captureOpponents(
        current: LocalState,
        movingSeat: Int,
        destination: Int,
    ): Int {
        if (!LudoPathEncoding.isTrackPosition(destination)) {
            return 0
        }

        val mover = current.players[movingSeat]
        val global = globalCell(mover.color, destination) ?: return 0
        if (global in SAFE_GLOBAL_CELLS) {
            return 0
        }

        var captures = 0
        current.players.forEachIndexed { seat, opponent ->
            if (seat == movingSeat) return@forEachIndexed

            val matchingTokenIndexes =
                opponent.tokens.mapIndexedNotNull { index, position ->
                    index.takeIf {
                        globalCell(opponent.color, position) == global
                    }
                }

            if (matchingTokenIndexes.size != 1) return@forEachIndexed

            opponent.tokens[matchingTokenIndexes.single()] = -1
            captures += 1
        }
        return captures
    }

    private fun globalCell(
        color: String,
        relativePosition: Int,
    ): Int? {
        if (!LudoPathEncoding.isTrackPosition(relativePosition)) {
            return null
        }
        val offset = START_OFFSETS[color] ?: return null
        return (offset + relativePosition) % 52
    }

    private fun advanceTurn(current: LocalState) {
        // Colors follow the perimeter in every rotated perspective. Keep player/seat
        // identities intact, including restored games and custom Pass & Play selections.
        val colors = OfflinePlayerLayout.COLORS
        val currentColor = current.players[current.turnSeat].color
        val start = colors.indexOf(currentColor)
        for (offset in 1..colors.size) {
            val nextColor = colors[(start + offset) % colors.size]
            val candidate = current.players.indexOfFirst { it.color == nextColor }
            if (candidate >= 0 && current.players[candidate].playerId !in current.finishedPlayerIds) {
                current.turnSeat = candidate
                return
            }
        }
        error("No active player remains before match completion")
    }

    private fun LocalState.frozenCopy(): LocalState = copy(
        players = players.map { it.copy(tokens = it.tokens.toMutableList()) }.toMutableList(),
        consecutiveSixes = consecutiveSixes.toMutableList(),
        openingRollConsumed = openingRollConsumed.toMutableList(),
        pendingLegal = pendingLegal.toMutableSet(),
        finishedPlayerIds = finishedPlayerIds.toMutableList(),
        history = history.toMutableList(),
    )

    private fun persist() {
        val active = state ?: return
        if (active.rulesetId == OfflineLudoV6Binding.RULESET_ID && active.history.size > OfflineLudoV6Binding.HISTORY_WINDOW) {
            active.history.subList(0, active.history.size - OfflineLudoV6Binding.HISTORY_WINDOW).clear()
        }
        val current = active.frozenCopy()
        synchronized(checkpoints) {
            checkpoints[mode] = current
            if (current.status == "FINISHED") checkpointWriter.execute { reconcileFinishedResult(current) }
            checkpointWrites.submit(mode) {
                runCatching { writeCheckpoint(current) }
                    .onFailure { android.util.Log.e("OfflineSave", "Could not serialize checkpoint", it) }
            }
        }
    }

    private fun writeCheckpoint(current: LocalState) {
        val json =
            JSONObject()
                .put("profileOwnerKnown", current.profileOwnerKnown)
                .put("profileOwnerAccountId", current.profileOwnerAccountId)
                .put("rulesetId", current.rulesetId)
                .put("matchId", current.matchId)
                .put("status", current.status)
                .put("turnSeat", current.turnSeat)
                .put("eventIndex", current.eventIndex)
                .put("revision", current.revision)
                .put("winnerPlayerId", current.winnerPlayerId)
                .put(
                    "finishedPlayerIds",
                    JSONArray(current.finishedPlayerIds),
                )
                .put(
                    "players",
                    JSONArray().apply {
                        current.players.forEach { player ->
                            put(
                                JSONObject()
                                    .put("playerId", player.playerId)
                                    .put("displayName", player.displayName)
                                    .put("color", player.color)
                                    .put("tokens", JSONArray(player.tokens)),
                            )
                        }
                    },
                )
                .put("consecutiveSixes", JSONArray(current.consecutiveSixes))
                .put("openingRollConsumed", JSONArray(current.openingRollConsumed))
                .put(
                    "history",
                    JSONArray().apply {
                        current.history.takeLast(MAX_HISTORY).forEach { event ->
                            put(
                                JSONObject()
                                    .put("eventIndex", event.eventIndex)
                                    .put("playerId", event.playerId)
                                    .put("color", event.color)
                                    .put("roundId", event.roundId)
                                    .put("serverCommitment", event.serverCommitment)
                                    .put("clientCommitment", event.clientCommitment)
                                    .put("actorHash", event.actorHash)
                                    .put("previousStateHash", event.previousStateHash)
                                    .put("rulesetHash", event.rulesetHash)
                                    .put("proofDigest", event.proofDigest)
                                    .put("fairnessProtocol", event.fairnessProtocol)
                                    .put("previousFairnessDigest", event.previousFairnessDigest)
                                    .put("fairnessDigest", event.fairnessDigest)
                                    .put("randomOutcome", event.randomOutcome)
                                    .put("outcome", event.outcome)
                                    .put("openingRollApplied", event.openingRollApplied)
                                    .put("moveTokenIndex", event.moveTokenIndex)
                                    .put("captures", event.captures),
                            )
                        }
                    },
                )
                .put("lastAudit", current.lastAudit?.toJson())

        current.pendingOutcome?.let { outcome ->
            json.put(
                "pending",
                JSONObject()
                    .put("outcome", outcome)
                    .put("eventIndex", current.pendingEventIndex)
                    .put("roundId", current.pendingRoundId)
                    .put("clientCommitment", current.pendingClientCommitment)
                    .put("proofDigest", current.pendingProofDigest)
                    .put("legal", JSONArray(current.pendingLegal.sorted())),
            )
        }

        if (!prefs.edit().putString(KEY_STATE, json.toString()).commit()) {
            android.util.Log.e("OfflineSave", "Could not persist checkpoint revision ${current.revision}")
        }
    }

    private fun loadState(): LocalState? {
        val raw = prefs.getString(KEY_STATE, null) ?: return null

        return runCatching {
            val json = JSONObject(raw)
            val playersJson = json.getJSONArray("players")
            val players =
                buildList {
                    for (index in 0 until playersJson.length()) {
                        val value = playersJson.getJSONObject(index)
                        val tokensJson = value.getJSONArray("tokens")
                        val tokens =
                            MutableList(tokensJson.length()) { tokenIndex ->
                                tokensJson.getInt(tokenIndex)
                            }
                        add(
                            LocalPlayer(
                                playerId = value.getString("playerId"),
                                displayName =
                                    value.getString("displayName").let { savedName ->
                                        val cpuNumber = Regex("^CPU\\s+(\\d+)$")
                                            .matchEntire(savedName.trim())
                                            ?.groupValues
                                            ?.getOrNull(1)
                                        if (computerMode && value.getString("playerId").startsWith("cpu-player-") && cpuNumber != null) {
                                            "Robot $cpuNumber"
                                        } else {
                                            savedName
                                        }
                                    },
                                color = value.getString("color"),
                                tokens = tokens,
                            ),
                        )
                    }
                }.toMutableList()

            val sixesJson = json.getJSONArray("consecutiveSixes")
            val sixes =
                MutableList(sixesJson.length()) { index ->
                    sixesJson.getInt(index)
                }

            // Legacy local saves predate the opening entitlement. Treat every
            // seat as already consumed so an app update can never inject a free
            // six into a match that was already underway.
            val openingJson = json.optJSONArray("openingRollConsumed")
            val openingRollConsumed =
                if (
                    openingJson != null &&
                    openingJson.length() == players.size
                ) {
                    MutableList(openingJson.length()) { index ->
                        openingJson.getBoolean(index)
                    }
                } else {
                    MutableList(players.size) { true }
                }

            val history = mutableListOf<LocalHistoryEvent>()
            json.optJSONArray("history")?.let { historyJson ->
                for (index in 0 until historyJson.length()) {
                    val value = historyJson.getJSONObject(index)
                    val effectiveOutcome =
                        if (
                            value.has("outcome") &&
                            !value.isNull("outcome")
                        ) {
                            value.getInt("outcome")
                        } else {
                            null
                        }
                    val randomOutcome =
                        if (
                            value.has("randomOutcome") &&
                            !value.isNull("randomOutcome")
                        ) {
                            value.getInt("randomOutcome")
                        } else {
                            effectiveOutcome
                        }

                    history +=
                        LocalHistoryEvent(
                            eventIndex = value.getInt("eventIndex"),
                            playerId = value.getString("playerId"),
                            color = value.nullableString("color"),
                            roundId = value.nullableString("roundId"),
                            serverCommitment = value.nullableString("serverCommitment"),
                            clientCommitment = value.nullableString("clientCommitment"),
                            actorHash = value.nullableString("actorHash"),
                            previousStateHash = value.nullableString("previousStateHash"),
                            rulesetHash = value.nullableString("rulesetHash"),
                            proofDigest = value.nullableString("proofDigest"),
                            fairnessProtocol = value.nullableString("fairnessProtocol"),
                            previousFairnessDigest = value.nullableString("previousFairnessDigest"),
                            fairnessDigest = value.nullableString("fairnessDigest"),
                            randomOutcome = randomOutcome,
                            outcome = effectiveOutcome,
                            openingRollApplied = value.optBoolean("openingRollApplied", false),
                            moveTokenIndex =
                                if (
                                    value.has("moveTokenIndex") &&
                                    !value.isNull("moveTokenIndex")
                                ) {
                                    value.getInt("moveTokenIndex")
                                } else {
                                    null
                                },
                            captures = value.optInt("captures", 0),
                        )
                }
            }

            check(
                OfflineFairnessChain.verifyHistory(
                    history.map { it.toFairnessMaterial() },
                ),
            ) {
                "Offline fairness history chain verification failed"
            }

            val pending = json.optJSONObject("pending")
            val restoredProofDigest = pending?.nullableString("proofDigest")
            val restorePending = pending != null && restoredProofDigest != null
            val legal = mutableSetOf<Int>()
            if (restorePending) {
                pending?.optJSONArray("legal")?.let { values ->
                    for (index in 0 until values.length()) {
                        legal += values.getInt(index)
                    }
                }
            }

            val restoredFinishedPlayerIds =
                json.optJSONArray("finishedPlayerIds")
                    ?.let { values ->
                        buildList {
                            for (index in 0 until values.length()) {
                                add(values.getString(index))
                            }
                        }
                    }
                    ?.toMutableList()
                    ?: players
                        .filter { it.tokens.all { position -> position == HOME_POSITION } }
                        .map { it.playerId }
                        .toMutableList()

            LocalState(
                profileOwnerKnown = json.optBoolean("profileOwnerKnown", false),
                profileOwnerAccountId = json.nullableString("profileOwnerAccountId"),
                rulesetId = json.optString("rulesetId", OfflineLudoV3Binding.RULESET_ID),
                matchId =
                    json.optString("matchId")
                        .takeIf { it.isNotBlank() }
                        ?: "offline-legacy-${UUID.randomUUID()}",
                status = json.optString("status", "ACTIVE"),
                players = players,
                turnSeat = json.optInt("turnSeat", 0),
                eventIndex = json.optInt("eventIndex", 0),
                consecutiveSixes = sixes,
                openingRollConsumed = openingRollConsumed,
                pendingOutcome =
                    if (
                        restorePending &&
                        pending != null &&
                        pending.has("outcome")
                    ) {
                        pending.optInt("outcome")
                    } else {
                        null
                    },
                pendingLegal = legal,
                pendingEventIndex =
                    if (
                        restorePending &&
                        pending != null &&
                        pending.has("eventIndex")
                    ) {
                        pending.optInt("eventIndex")
                    } else {
                        null
                    },
                pendingRoundId =
                    if (restorePending) pending?.nullableString("roundId") else null,
                pendingClientCommitment =
                    if (restorePending) pending?.nullableString("clientCommitment") else null,
                pendingProofDigest =
                    if (restorePending) restoredProofDigest else null,
                winnerPlayerId = json.nullableString("winnerPlayerId"),
                finishedPlayerIds = restoredFinishedPlayerIds,
                history = history,
                lastAudit =
                    OfflineRandomnessAudit.fromJson(
                        json.optJSONObject("lastAudit"),
                    ),
                revision = json.optInt("revision", 1),
            ).also { restored ->
                validateRestoredState(restored)
            }
        }.getOrElse {
            prefs.edit().clear().apply()
            null
        }
    }

    private fun validateRestoredState(restored: LocalState) {
        check(restored.rulesetId in setOf(OfflineLudoV3Binding.RULESET_ID, OfflineLudoV5Binding.RULESET_ID, OfflineLudoV6Binding.RULESET_ID))

        check(restored.players.size in 2..4) {
            "Offline save has an invalid player count"
        }
        check(restored.turnSeat in restored.players.indices) {
            "Offline save has an invalid turn seat"
        }
        check(restored.eventIndex >= 0) {
            "Offline save has an invalid event index"
        }
        check(restored.revision >= 1) {
            "Offline save has an invalid revision"
        }
        check(restored.status == "ACTIVE" || restored.status == "FINISHED") {
            "Offline save has an invalid status"
        }

        val ids = restored.players.map { it.playerId }
        check(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) {
            "Offline save has invalid player identities"
        }

        val colors = restored.players.map { it.color }
        check(
            colors.all { it in OfflinePlayerLayout.COLORS } &&
                colors.distinct().size == colors.size,
        ) {
            "Offline save has invalid player colors"
        }

        check(
            restored.players.all { player ->
                player.tokens.size == 4 &&
                    player.tokens.all { position -> position in -1..HOME_POSITION }
            },
        ) {
            "Offline save has invalid token positions"
        }

        check(
            restored.consecutiveSixes.size == restored.players.size &&
                restored.consecutiveSixes.all { it in 0..3 },
        ) {
            "Offline save has invalid consecutive-six state"
        }
        check(restored.openingRollConsumed.size == restored.players.size) {
            "Offline save has invalid opening-roll state"
        }

        val pendingOutcome = restored.pendingOutcome
        val pendingEventIndex = restored.pendingEventIndex
        check(pendingOutcome == null || pendingOutcome in 1..6) {
            "Offline save has an invalid pending dice outcome"
        }
        check(restored.pendingLegal.all { it in 0..3 }) {
            "Offline save has invalid legal token indexes"
        }
        check(pendingEventIndex == null || pendingEventIndex >= 0) {
            "Offline save has an invalid pending event index"
        }

        if (pendingOutcome != null) {
            check(
                pendingEventIndex != null &&
                    pendingEventIndex < restored.eventIndex &&
                    restored.pendingLegal.isNotEmpty(),
            ) {
                "Offline save has an incomplete pending move"
            }
        } else {
            check(restored.pendingLegal.isEmpty()) {
                "Offline save has stale legal moves"
            }
        }

        val winner = restored.winnerPlayerId
        check(winner == null || winner in ids) {
            "Offline save has an invalid winner"
        }
        check(
            restored.finishedPlayerIds.distinct().size ==
                restored.finishedPlayerIds.size &&
                restored.finishedPlayerIds.all { it in ids },
        ) {
            "Offline save has invalid finish-order state"
        }
        check(
            restored.finishedPlayerIds.size <=
                restored.players.size - 1 ||
                restored.status == "FINISHED",
        ) {
            "Offline active save cannot have all players finished"
        }
        if (restored.status == "FINISHED") {
            check(winner != null) {
                "Finished offline save is missing a winner"
            }
        } else {
            check(winner == null) {
                "Active offline save cannot already have a winner"
            }
        }
    }

    private fun JSONObject.nullableString(name: String): String? =
        optString(name)
            .takeIf { it.isNotBlank() && it != "null" }

    private fun LocalState.toSnapshot(): MatchSnapshot =
        MatchSnapshot(
            matchId = matchId,
            status = status,
            hostPlayerId = players.firstOrNull()?.playerId.orEmpty(),
            players =
                players.mapIndexed { seat, player ->
                    PlayerSnapshot(
                        playerId = player.playerId,
                        displayName = player.displayName,
                        color = player.color,
                        seat = seat,
                        tokens = player.tokens.toList(),
                    )
                },
            turnSeat = turnSeat,
            randomEventIndex = eventIndex,
            pendingRoll =
                pendingOutcome?.let { outcome ->
                    val pendingHistory =
                        history.lastOrNull { it.eventIndex == pendingEventIndex }
                    PendingRollSnapshot(
                        status = "RESOLVED",
                        seat = turnSeat,
                        eventIndex = pendingEventIndex ?: (eventIndex - 1),
                        eventId = "roll:${pendingEventIndex ?: (eventIndex - 1)}",
                        roundId = pendingRoundId,
                        serverCommitment =
                            lastAudit
                                ?.takeIf { it.eventIndex == pendingEventIndex }
                                ?.serverCommitment,
                        clientCommitment = pendingClientCommitment,
                        revealDeadlineAt = null,
                        proofDigest = pendingProofDigest,
                        outcome = outcome,
                        legalTokenIndexes = pendingLegal.toSet(),
                        randomOutcome = pendingHistory?.randomOutcome,
                        openingRollApplied =
                            pendingHistory?.openingRollApplied == true,
                    )
                },
            winnerPlayerId = winnerPlayerId,
            rulesetId = rulesetId,
            history = history.map { it.toSnapshot() },
            finishOrderPlayerIds = finishedPlayerIds.toList(),
        )

    private data class LocalPlayer(
        val playerId: String,
        val displayName: String,
        val color: String,
        var tokens: MutableList<Int>,
    )

    private data class LocalHistoryEvent(
        val eventIndex: Int,
        val playerId: String,
        val color: String?,
        val roundId: String?,
        val serverCommitment: String?,
        val clientCommitment: String?,
        val actorHash: String?,
        val previousStateHash: String?,
        val rulesetHash: String?,
        val proofDigest: String?,
        val fairnessProtocol: String?,
        val previousFairnessDigest: String?,
        val fairnessDigest: String?,
        val randomOutcome: Int?,
        val outcome: Int?,
        val openingRollApplied: Boolean,
        val moveTokenIndex: Int?,
        val captures: Int,
    ) {
        fun toSnapshot(): HistoryEventSnapshot =
            HistoryEventSnapshot(
                eventIndex = eventIndex,
                playerId = playerId,
                roundId = roundId,
                proofDigest = proofDigest,
                outcome = outcome,
                moveTokenIndex = moveTokenIndex,
                captures = captures,
                serverCommitment = serverCommitment,
                clientCommitment = clientCommitment,
                previousStateHash = previousStateHash,
                rulesetHash = rulesetHash,
                fairnessProtocol = fairnessProtocol,
                previousFairnessDigest = previousFairnessDigest,
                fairnessDigest = fairnessDigest,
                status = "RESOLVED",
                randomOutcome = randomOutcome,
                effectiveOutcome = outcome,
                openingRollApplied = openingRollApplied,
            )

        fun toFairnessMaterial(): OfflineFairnessMaterial =
            OfflineFairnessMaterial(
                eventIndex = eventIndex,
                playerId = playerId,
                color = color,
                roundId = roundId,
                serverCommitment = serverCommitment,
                clientCommitment = clientCommitment,
                actorHash = actorHash,
                previousStateHash = previousStateHash,
                rulesetHash = rulesetHash,
                proofDigest = proofDigest,
                outcome = randomOutcome,
                fairnessProtocol = fairnessProtocol,
                previousFairnessDigest = previousFairnessDigest,
                fairnessDigest = fairnessDigest,
            )
    }

    private data class LocalState(
        val profileOwnerKnown: Boolean = false,
        val profileOwnerAccountId: String? = null,
        val rulesetId: String = OfflineLudoV6Binding.RULESET_ID,
        val matchId: String,
        var status: String,
        val players: MutableList<LocalPlayer>,
        var turnSeat: Int,
        var eventIndex: Int,
        val consecutiveSixes: MutableList<Int>,
        val openingRollConsumed: MutableList<Boolean>,
        var pendingOutcome: Int?,
        var pendingLegal: MutableSet<Int>,
        var pendingEventIndex: Int?,
        var pendingRoundId: String?,
        var pendingClientCommitment: String?,
        var pendingProofDigest: String?,
        var winnerPlayerId: String?,
        val finishedPlayerIds: MutableList<String>,
        val history: MutableList<LocalHistoryEvent>,
        var lastAudit: OfflineRandomnessAudit?,
        var revision: Int,
    )

    private companion object {
        val checkpoints = mutableMapOf<GameMode, LocalState?>()
        val checkpointWriter = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "LudoPawsCheckpoint").apply { isDaemon = true }
        }
        val checkpointWrites = LatestWriteQueue<GameMode>(checkpointWriter) {
            android.util.Log.e("OfflineSave", "Could not write checkpoint", it)
        }
        const val PREFS_NAME = "ludoproof_offline_game"
        const val PREFS_NAME_COMPUTER = "ludoproof_computer_game"
        const val KEY_STATE = "state"
        const val HOME_POSITION = 57
        const val MAX_HISTORY = 100

        val START_OFFSETS =
            mapOf(
                "RED" to 0,
                "GREEN" to 13,
                "YELLOW" to 26,
                "BLUE" to 39,
            )

        val SAFE_GLOBAL_CELLS =
            setOf(
                0,
                8,
                13,
                21,
                26,
                34,
                39,
                47,
            )
    }
}
