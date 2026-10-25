package com.rekixo.ludopaws

import org.json.JSONArray
import org.json.JSONObject

data class PlayerSnapshot(
    val playerId: String,
    val displayName: String,
    val color: String,
    val seat: Int,
    val tokens: List<Int>,
    val teamId: String? = null,
    val characterId: String? = null,
    val playerType: String = "HUMAN",
)

data class PendingRollSnapshot(
    val status: String,
    val seat: Int,
    val eventIndex: Int,
    val eventId: String?,
    val roundId: String?,
    val serverCommitment: String?,
    val clientCommitment: String?,
    val revealDeadlineAt: Long?,
    val proofDigest: String?,
    val outcome: Int?,
    val legalTokenIndexes: Set<Int>,
    val scheduledSeat: Int? = null,
    val randomOutcome: Int? = null,
    val openingRollApplied: Boolean = false,
)

data class HistoryEventSnapshot(
    val eventIndex: Int,
    val playerId: String,
    val roundId: String?,
    val proofDigest: String?,
    val outcome: Int?,
    val moveTokenIndex: Int?,
    val captures: Int,
    val serverCommitment: String? = null,
    val clientCommitment: String? = null,
    val previousStateHash: String? = null,
    val rulesetHash: String? = null,
    val fairnessProtocol: String? = null,
    val previousFairnessDigest: String? = null,
    val fairnessDigest: String? = null,
    val status: String? = null,
    val randomOutcome: Int? = null,
    val effectiveOutcome: Int? = null,
    val openingRollApplied: Boolean = false,
)

data class MatchSnapshot(
    val matchId: String,
    val status: String,
    val hostPlayerId: String,
    val targetPlayerCount: Int? = null,
    val matchMode: String = "ONLINE",
    val players: List<PlayerSnapshot>,
    val turnSeat: Int,
    val randomEventIndex: Int,
    val pendingRoll: PendingRollSnapshot?,
    val winnerPlayerId: String?,
    val rulesetId: String,
    val history: List<HistoryEventSnapshot>,
    val actingSeat: Int? = null,
    val teamAssignments: List<String> = emptyList(),
    val winnerTeamId: String? = null,
    // Ordered player IDs from a trusted finish-order source, when available.
    val finishOrderPlayerIds: List<String> = emptyList(),
    val revision: Long = 0L,
    val turnDeadlineAt: Long? = null,
    val turnRollTimeoutMillis: Long? = null,
    val serverTimeAtSnapshot: Long? = null,
)

data class GameEnvelope(
    val playerId: String?,
    val state: MatchSnapshot?,
)

class GameSchemaException(
    message: String,
) : IllegalArgumentException(message)

object GameJson {
    private val classicRulesetIds =
        setOf(
            "ludoproof-standard-v1",
            "ludoproof-standard-v2",
            "ludoproof-standard-v3",
            "ludoproof-standard-v4",
        "ludoproof-standard-v5",
        )
    private val teamRulesetIds =
        setOf(
            "ludoproof-team-v1",
            "ludoproof-team-v2",
            "ludoproof-team-v3",
            "ludoproof-team-v4",
        "ludoproof-team-v5",
        )
    private val colors = listOf("RED", "GREEN", "YELLOW", "BLUE")
    private val teamAssignments = listOf("A", "B", "A", "B")
    private val statuses = setOf("WAITING", "ACTIVE", "FINISHED")
    private val matchModes = setOf("ONLINE", "FRIENDS", "TEAM_UP")
    private val pendingStatuses = setOf("CREATING", "COMMITTED", "RESOLVING", "RESOLVED")

    private val preparedEnvelopes = java.util.WeakHashMap<JSONObject, Result<GameEnvelope>>()

    fun prepareEnvelope(response: JSONObject) {
        val parsed = runCatching { envelope(response) }
        synchronized(preparedEnvelopes) { preparedEnvelopes[response] = parsed }
    }

    fun preparedEnvelope(response: JSONObject): GameEnvelope {
        val prepared = synchronized(preparedEnvelopes) { preparedEnvelopes[response] }
        return prepared?.getOrThrow() ?: envelope(response)
    }

    fun envelope(response: JSONObject): GameEnvelope {
        val stateObject =
            response.optJSONObject("state")
                ?: if (response.has("matchId") && response.has("players")) {
                    response
                } else {
                    null
                }

        return GameEnvelope(
            playerId =
                response.optString("playerId")
                    .takeIf { it.isNotBlank() },
            state = stateObject?.let(::match),
        )
    }

    private fun match(value: JSONObject): MatchSnapshot {
        validateAuthoritativeMatch(value)
        return MatchSnapshot(
            matchId = value.optString("matchId"),
            status = value.optString("status"),
            hostPlayerId = value.optString("hostPlayerId"),
            targetPlayerCount =
                if (
                    value.has("targetPlayerCount") &&
                    !value.isNull("targetPlayerCount")
                ) {
                    value.optInt("targetPlayerCount")
                } else {
                    null
                },
            matchMode = value.optString("matchMode"),
            players = value.optJSONArray("players").toPlayerList(),
            // WAITING snapshots intentionally carry a null turnSeat on the wire.
            // The UI historically treats that as seat zero while it is waiting;
            // strict validation above prevents a missing/invalid ACTIVE turnSeat
            // from being silently converted to zero.
            turnSeat = value.optInt("turnSeat", 0),
            randomEventIndex = value.optInt("randomEventIndex"),
            pendingRoll = value.optJSONObject("pendingRoll")?.let(::pendingRoll),
            winnerPlayerId =
                value.optString("winnerPlayerId")
                    .takeIf { it.isNotBlank() && it != "null" },
            rulesetId = value.optString("rulesetId"),
            history = value.optJSONArray("history").toHistoryList(),
            actingSeat =
                if (value.has("actingSeat") && !value.isNull("actingSeat")) {
                    value.optInt("actingSeat")
                } else {
                    null
                },
            teamAssignments = value.optJSONArray("teamAssignments").toStringList(),
            winnerTeamId =
                value.optString("winnerTeamId")
                    .takeIf { it == "A" || it == "B" },
            finishOrderPlayerIds = value.optJSONArray("finishOrderPlayerIds").toStringList(),
            revision = value.optLong("revision", 0L),
            turnDeadlineAt =
                if (value.has("turnDeadlineAt") && !value.isNull("turnDeadlineAt")) {
                    value.optLong("turnDeadlineAt").takeIf { it > 0L }
                } else {
                    null
                },
            turnRollTimeoutMillis =
                if (value.has("turnRollTimeoutMs") && !value.isNull("turnRollTimeoutMs")) {
                    value.optLong("turnRollTimeoutMs").takeIf { it > 0L }
                } else {
                    null
                },
            serverTimeAtSnapshot =
                if (value.has("serverNow") && !value.isNull("serverNow")) {
                    value.optLong("serverNow").takeIf { it > 0L }
                } else {
                    null
                },
        )
    }

    private fun validateAuthoritativeMatch(value: JSONObject) {
        val schemaVersion = value.requireInt("schemaVersion")
        schema(schemaVersion == 1, "unsupported match schemaVersion")

        val matchId = value.requireString("matchId")
        schema(Regex("^LP[A-Z2-9]{8}$").matches(matchId), "invalid matchId")

        val status = value.requireString("status")
        schema(status in statuses, "invalid match status")

        val hostPlayerId = value.requireString("hostPlayerId")
        val matchMode = value.requireString("matchMode")
        schema(matchMode in matchModes, "invalid matchMode")

        schema(value.has("targetPlayerCount"), "missing targetPlayerCount")
        val targetPlayerCount =
            if (value.isNull("targetPlayerCount")) {
                null
            } else {
                value.requireInt("targetPlayerCount").also {
                    schema(it in 2..4, "invalid targetPlayerCount")
                }
            }
        if (matchMode == "FRIENDS") {
            schema(targetPlayerCount != null, "friend room requires targetPlayerCount")
        }
        if (matchMode == "TEAM_UP") {
            schema(targetPlayerCount == 4, "Team Up requires targetPlayerCount 4")
        }

        val rulesetId = value.requireString("rulesetId")
        val supportedRulesets =
            if (matchMode == "TEAM_UP") {
                teamRulesetIds
            } else {
                classicRulesetIds
            }
        schema(rulesetId in supportedRulesets, "rulesetId does not match matchMode")

        schema(value.has("revision"), "missing revision")
        schema(value.requireLong("revision") >= 0L, "invalid revision")
        val randomEventIndex = value.requireInt("randomEventIndex")
        schema(randomEventIndex >= 0, "invalid randomEventIndex")

        schema(value.has("turnSeat"), "missing turnSeat")
        val turnSeat =
            if (value.isNull("turnSeat")) {
                schema(status == "WAITING", "turnSeat may be null only while waiting")
                null
            } else {
                value.requireInt("turnSeat")
            }

        val playersArray = value.requireArray("players")
        schema(playersArray.length() in 1..4, "players must contain one to four entries")
        if (targetPlayerCount != null) {
            schema(playersArray.length() <= targetPlayerCount, "player count exceeds targetPlayerCount")
        }
        if (status != "WAITING") {
            schema(playersArray.length() >= 2, "active/finished match requires at least two players")
            schema(turnSeat != null && turnSeat in 0 until playersArray.length(), "turnSeat is outside player seats")
        }
        if (status == "ACTIVE" && targetPlayerCount != null) {
            schema(playersArray.length() == targetPlayerCount, "active match is missing required players")
        }
        if (matchMode == "TEAM_UP" && status != "WAITING") {
            schema(playersArray.length() == 4, "active/finished Team Up requires four players")
        }

        val playerIds = linkedSetOf<String>()
        for (index in 0 until playersArray.length()) {
            val player = playersArray.requireObject(index, "players")
            val playerId = player.requireString("playerId")
            schema(playerIds.add(playerId), "duplicate playerId")
            schema(player.requireInt("seat") == index, "player seat does not match array position")
            // Public two-player Online matches seat the opponent in the Yellow quadrant.
            // Other classic rooms and four-player matches use the canonical seat color order.
            val expectedColor =
                if (
                    matchMode == "ONLINE" &&
                    targetPlayerCount == 2 &&
                    index == 1
                ) {
                    "YELLOW"
                } else {
                    colors[index]
                }
            schema(player.requireString("color") == expectedColor, "player color does not match seat")
            if (player.has("playerType")) {
                schema(
                    player.requireString("playerType") in setOf("HUMAN", "BOT"),
                    "invalid playerType",
                )
            }

            val tokens = player.requireArray("tokens")
            schema(tokens.length() == 4, "player must have exactly four tokens")
            for (tokenIndex in 0 until tokens.length()) {
                val position = tokens.requireInt(tokenIndex, "tokens")
                schema(position in -1..57, "token position is outside -1..57")
            }

            if (matchMode == "TEAM_UP") {
                schema(
                    player.requireString("teamId") == teamAssignments[index],
                    "player teamId does not match seat assignment",
                )
            }
        }
        schema(hostPlayerId in playerIds, "hostPlayerId is not a seated player")

        if (matchMode == "TEAM_UP") {
            val assignments = value.requireArray("teamAssignments")
            schema(assignments.length() == 4, "Team Up requires four team assignments")
            val parsedAssignments =
                List(assignments.length()) { index ->
                    assignments.requireString(index, "teamAssignments")
                }
            schema(parsedAssignments == teamAssignments, "invalid Team Up assignments")

            schema(value.has("actingSeat"), "Team Up snapshot is missing actingSeat")
            if (value.isNull("actingSeat")) {
                schema(status == "WAITING", "actingSeat may be null only while waiting")
            } else {
                val actingSeat = value.requireInt("actingSeat")
                schema(actingSeat in 0 until playersArray.length(), "actingSeat is outside player seats")
            }
            schema(value.has("winnerTeamId"), "Team Up snapshot is missing winnerTeamId")
        }

        schema(value.has("winnerPlayerId"), "missing winnerPlayerId")
        val winnerPlayerId = value.optionalString("winnerPlayerId")
        val winnerTeamId = value.optionalString("winnerTeamId")
        if (winnerPlayerId != null) {
            schema(winnerPlayerId in playerIds, "winnerPlayerId is not a seated player")
        }
        if (value.has("finishOrderPlayerIds") && !value.isNull("finishOrderPlayerIds")) {
            val finishOrder = value.requireArray("finishOrderPlayerIds")
            val finishIds = List(finishOrder.length()) { index ->
                finishOrder.requireString(index, "finishOrderPlayerIds")
            }
            schema(finishIds.distinct().size == finishIds.size, "finish order contains duplicate player IDs")
            schema(finishIds.all { it in playerIds }, "finish order contains an unknown player")
            if (winnerPlayerId != null && finishIds.isNotEmpty()) {
                schema(finishIds.first() == winnerPlayerId, "finish order winner does not match winnerPlayerId")
            }
        }
        if (winnerTeamId != null) {
            schema(winnerTeamId == "A" || winnerTeamId == "B", "invalid winnerTeamId")
        }
        if (status == "FINISHED") {
            if (matchMode == "TEAM_UP") {
                schema(winnerPlayerId == null, "Team Up must not expose an individual winner")
                schema(winnerTeamId != null, "finished Team Up match requires winnerTeamId")
            } else {
                schema(winnerPlayerId != null, "finished classic match requires winnerPlayerId")
                schema(winnerTeamId == null, "classic match must not expose winnerTeamId")
            }
        } else {
            schema(winnerPlayerId == null, "unfinished match must not expose winnerPlayerId")
            schema(winnerTeamId == null, "unfinished match must not expose winnerTeamId")
        }

        schema(value.has("pendingRoll"), "missing pendingRoll")
        if (!value.isNull("pendingRoll")) {
            schema(status == "ACTIVE", "pendingRoll is allowed only for an active match")
            val pending = value.optJSONObject("pendingRoll")
                ?: throw GameSchemaException("pendingRoll must be an object or null")
            validatePendingRoll(
                pending = pending,
                playerCount = playersArray.length(),
                matchMode = matchMode,
                randomEventIndex = randomEventIndex,
            )
        }

        val history = value.requireArray("history")
        var previousEventIndex = -1
        for (index in 0 until history.length()) {
            val event = history.requireObject(index, "history")
            val eventIndex = event.requireInt("eventIndex")
            schema(eventIndex >= 0, "history eventIndex must be non-negative")
            schema(eventIndex > previousEventIndex, "history eventIndex must be strictly increasing")
            schema(eventIndex < randomEventIndex, "history eventIndex must be behind randomEventIndex")
            previousEventIndex = eventIndex

            schema(event.requireString("playerId") in playerIds, "history playerId is not seated")
            if (event.has("outcome") && !event.isNull("outcome")) {
                schema(event.requireInt("outcome") in 1..6, "history outcome is outside 1..6")
            }
            if (event.has("randomOutcome") && !event.isNull("randomOutcome")) {
                schema(event.requireInt("randomOutcome") in 1..6, "history randomOutcome is outside 1..6")
            }
            if (event.has("effectiveOutcome") && !event.isNull("effectiveOutcome")) {
                schema(event.requireInt("effectiveOutcome") in 1..6, "history effectiveOutcome is outside 1..6")
            }
            if (event.has("moveTokenIndex") && !event.isNull("moveTokenIndex")) {
                schema(event.requireInt("moveTokenIndex") in 0..3, "history moveTokenIndex is outside 0..3")
            }
            if (event.has("captures") && !event.isNull("captures")) {
                schema(event.requireInt("captures") >= 0, "history captures must be non-negative")
            }
        }
    }

    private fun validatePendingRoll(
        pending: JSONObject,
        playerCount: Int,
        matchMode: String,
        randomEventIndex: Int,
    ) {
        val status = pending.requireString("status")
        schema(status in pendingStatuses, "invalid pending roll status")

        val seat = pending.requireInt("seat")
        schema(seat in 0 until playerCount, "pending roll seat is outside player seats")

        val eventIndex = pending.requireInt("eventIndex")
        schema(eventIndex >= 0, "pending roll eventIndex must be non-negative")
        if (status == "CREATING") {
            schema(randomEventIndex == eventIndex, "creating roll eventIndex drift")
        } else {
            schema(randomEventIndex == eventIndex + 1, "committed/resolved roll eventIndex drift")
        }

        schema(pending.requireString("eventId") == "roll:$eventIndex", "pending roll eventId mismatch")

        if (matchMode == "TEAM_UP") {
            val scheduledSeat = pending.requireInt("scheduledSeat")
            schema(scheduledSeat in 0 until playerCount, "scheduledSeat is outside player seats")
        }

        schema(pending.has("legalTokenIndexes"), "pending roll is missing legalTokenIndexes")
        if (!pending.isNull("legalTokenIndexes")) {
            val legal = pending.requireArray("legalTokenIndexes")
            val seen = mutableSetOf<Int>()
            for (index in 0 until legal.length()) {
                val tokenIndex = legal.requireInt(index, "legalTokenIndexes")
                schema(tokenIndex in 0..3, "legal token index is outside 0..3")
                schema(seen.add(tokenIndex), "legal token indexes contain duplicates")
            }
            if (status == "RESOLVED") {
                schema(legal.length() > 0, "resolved pending roll must contain a legal move")
            }
        } else {
            schema(status != "RESOLVED", "resolved pending roll requires legalTokenIndexes")
        }

        schema(pending.has("outcome"), "pending roll is missing outcome")
        if (!pending.isNull("outcome")) {
            schema(pending.requireInt("outcome") in 1..6, "pending roll outcome is outside 1..6")
        } else {
            schema(status != "RESOLVED", "resolved pending roll requires outcome")
        }
        if (pending.has("randomOutcome") && !pending.isNull("randomOutcome")) {
            schema(pending.requireInt("randomOutcome") in 1..6, "pending roll randomOutcome is outside 1..6")
        }

        if (status != "RESOLVED" || pending.has("revealDeadlineAt") && !pending.isNull("revealDeadlineAt")) {
            schema(pending.requireLong("revealDeadlineAt") > 0L, "invalid revealDeadlineAt")
        }
    }

    private fun pendingRoll(value: JSONObject): PendingRollSnapshot =
        PendingRollSnapshot(
            status = value.optString("status"),
            seat = value.optInt("seat", -1),
            eventIndex = value.optInt("eventIndex", -1),
            eventId = value.optString("eventId").takeIf { it.isNotBlank() && it != "null" },
            roundId = value.optString("roundId").takeIf { it.isNotBlank() && it != "null" },
            serverCommitment = value.optString("serverCommitment").takeIf { it.isNotBlank() && it != "null" },
            clientCommitment = value.optString("clientCommitment").takeIf { it.isNotBlank() && it != "null" },
            revealDeadlineAt =
                if (value.has("revealDeadlineAt") && !value.isNull("revealDeadlineAt")) {
                    value.optLong("revealDeadlineAt")
                } else {
                    null
                },
            proofDigest = value.optString("proofDigest").takeIf { it.isNotBlank() && it != "null" },
            outcome =
                if (value.has("outcome") && !value.isNull("outcome")) {
                    value.optInt("outcome")
                } else {
                    null
                },
            legalTokenIndexes = value.optJSONArray("legalTokenIndexes").toIntSet(),
            scheduledSeat =
                if (value.has("scheduledSeat") && !value.isNull("scheduledSeat")) {
                    value.optInt("scheduledSeat")
                } else {
                    null
                },
            randomOutcome =
                if (value.has("randomOutcome") && !value.isNull("randomOutcome")) {
                    value.optInt("randomOutcome")
                } else {
                    null
                },
            openingRollApplied = value.optBoolean("openingRollApplied", false),
        )

    private fun JSONArray?.toPlayerList(): List<PlayerSnapshot> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val player = getJSONObject(index)
                add(
                    PlayerSnapshot(
                        playerId = player.getString("playerId"),
                        displayName = player.optString("displayName"),
                        color = player.getString("color"),
                        seat = player.getInt("seat"),
                        tokens = player.getJSONArray("tokens").toIntList(),
                        teamId = player.optString("teamId").takeIf { it == "A" || it == "B" },
                        characterId =
                            player.optString("characterId")
                                .trim()
                                .lowercase()
                                .takeIf { it.isNotBlank() && it != "null" },
                        playerType = player.optString("playerType", "HUMAN"),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toHistoryList(): List<HistoryEventSnapshot> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val event = getJSONObject(index)
                add(
                    HistoryEventSnapshot(
                        eventIndex = event.getInt("eventIndex"),
                        playerId = event.getString("playerId"),
                        roundId = event.optString("roundId").takeIf { it.isNotBlank() && it != "null" },
                        proofDigest = event.optString("proofDigest").takeIf { it.isNotBlank() && it != "null" },
                        outcome =
                            if (event.has("outcome") && !event.isNull("outcome")) {
                                event.getInt("outcome")
                            } else {
                                null
                            },
                        moveTokenIndex =
                            if (event.has("moveTokenIndex") && !event.isNull("moveTokenIndex")) {
                                event.getInt("moveTokenIndex")
                            } else {
                                null
                            },
                        captures = event.optInt("captures", 0),
                        serverCommitment = event.optString("serverCommitment").takeIf { it.isNotBlank() && it != "null" },
                        clientCommitment = event.optString("clientCommitment").takeIf { it.isNotBlank() && it != "null" },
                        previousStateHash = event.optString("previousStateHash").takeIf { it.isNotBlank() && it != "null" },
                        rulesetHash = event.optString("rulesetHash").takeIf { it.isNotBlank() && it != "null" },
                        fairnessProtocol = event.optString("fairnessProtocol").takeIf { it.isNotBlank() && it != "null" },
                        previousFairnessDigest = event.optString("previousFairnessDigest").takeIf { it.isNotBlank() && it != "null" },
                        fairnessDigest = event.optString("fairnessDigest").takeIf { it.isNotBlank() && it != "null" },
                        status = event.optString("status").takeIf { it.isNotBlank() && it != "null" },
                        randomOutcome =
                            if (event.has("randomOutcome") && !event.isNull("randomOutcome")) {
                                event.getInt("randomOutcome")
                            } else {
                                null
                            },
                        effectiveOutcome =
                            if (event.has("effectiveOutcome") && !event.isNull("effectiveOutcome")) {
                                event.getInt("effectiveOutcome")
                            } else {
                                null
                            },
                        openingRollApplied = event.optBoolean("openingRollApplied", false),
                    ),
                )
            }
        }
    }

    private fun JSONObject.requireString(name: String): String {
        schema(has(name) && !isNull(name), "missing $name")
        val raw = opt(name)
        schema(raw is String, "$name must be a string")
        val value = raw as String
        schema(value.isNotBlank(), "$name must not be blank")
        return value
    }

    private fun JSONObject.optionalString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        val raw = opt(name)
        schema(raw is String, "$name must be a string or null")
        val value = raw as String
        schema(value.isNotBlank(), "$name must not be blank")
        return value
    }

    private fun JSONObject.requireInt(name: String): Int {
        schema(has(name) && !isNull(name), "missing $name")
        return requireIntegralInt(opt(name), name)
    }

    private fun JSONObject.requireLong(name: String): Long {
        schema(has(name) && !isNull(name), "missing $name")
        val raw = opt(name)
        schema(raw is Number, "$name must be an integer")
        val number = raw as Number
        val value = number.toLong()
        schema(number.toDouble() == value.toDouble(), "$name must be an integer")
        return value
    }

    private fun JSONObject.requireArray(name: String): JSONArray {
        schema(has(name) && !isNull(name), "missing $name")
        return optJSONArray(name) ?: throw GameSchemaException("$name must be an array")
    }

    private fun JSONArray.requireObject(index: Int, field: String): JSONObject =
        optJSONObject(index)
            ?: throw GameSchemaException("$field[$index] must be an object")

    private fun JSONArray.requireInt(index: Int, field: String): Int =
        requireIntegralInt(opt(index), "$field[$index]")

    private fun JSONArray.requireString(index: Int, field: String): String {
        val raw = opt(index)
        schema(raw is String, "$field[$index] must be a string")
        val value = raw as String
        schema(value.isNotBlank(), "$field[$index] must not be blank")
        return value
    }

    private fun requireIntegralInt(raw: Any?, field: String): Int {
        schema(raw is Number, "$field must be an integer")
        val number = raw as Number
        val longValue = number.toLong()
        schema(number.toDouble() == longValue.toDouble(), "$field must be an integer")
        schema(longValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong(), "$field is outside integer range")
        return longValue.toInt()
    }

    private fun schema(condition: Boolean, message: String) {
        if (!condition) throw GameSchemaException("Invalid authoritative match snapshot: $message")
    }

    private fun JSONArray?.toIntList(): List<Int> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) add(getInt(index))
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) add(getString(index))
        }
    }

    private fun JSONArray?.toIntSet(): Set<Int> = toIntList().toSet()
}
