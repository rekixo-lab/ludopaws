package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.characters.data.local.LudoPawsCharacterRuntime
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

class GameApi(
    baseUrl: String = BuildConfig.LUDOPROOF_API_BASE_URL,
    private val client: OkHttpClient = GameHttpTransport.client,
    private val commandChannel: (() -> MatchCommandChannel?)? = null,
) {
    private val baseUrl = baseUrl.trimEnd('/')

    init {
        require(this.baseUrl.startsWith("https://")) { "LudoProof API must use HTTPS" }
    }

    fun registerFriend(displayName: String, clientRequestId: String): JSONObject =
        request(
            method = "POST",
            path = "/api/friends/register",
            body = JSONObject()
                .put("displayName", displayName)
                .put("clientRequestId", clientRequestId),
        )

    fun friendSnapshot(friendToken: String): JSONObject =
        request(
            method = "GET",
            path = "/api/friends/snapshot",
            friendToken = friendToken,
        )

    fun friendHeartbeat(friendToken: String, displayName: String): JSONObject =
        request(
            method = "POST",
            path = "/api/friends/heartbeat",
            friendToken = friendToken,
            body = JSONObject().put("displayName", displayName),
        )

    fun sendFriendRequest(friendToken: String, friendId: String): JSONObject =
        request(
            method = "POST",
            path = "/api/friends/request",
            friendToken = friendToken,
            body = JSONObject().put("friendId", friendId),
        )

    fun respondFriendRequest(friendToken: String, requestId: String, accept: Boolean): JSONObject =
        request(
            method = "POST",
            path = "/api/friends/request/respond",
            friendToken = friendToken,
            body = JSONObject()
                .put("requestId", requestId)
                .put("accept", accept),
        )

    fun removeFriend(friendToken: String, friendId: String): JSONObject =
        request(
            method = "POST",
            path = "/api/friends/remove",
            friendToken = friendToken,
            body = JSONObject().put("friendId", friendId),
        )

    fun blockFriend(friendToken: String, friendId: String): JSONObject =
        request(
            method = "POST",
            path = "/api/friends/block",
            friendToken = friendToken,
            body = JSONObject().put("friendId", friendId),
        )

    fun unblockFriend(friendToken: String, friendId: String): JSONObject =
        request(
            method = "POST",
            path = "/api/friends/unblock",
            friendToken = friendToken,
            body = JSONObject().put("friendId", friendId),
        )

    fun reportFriend(
        friendToken: String,
        friendId: String,
        reason: String,
        clientRequestId: String,
        messageId: String? = null,
    ): JSONObject =
        request(
            method = "POST",
            path = "/api/friends/report",
            friendToken = friendToken,
            body = JSONObject()
                .put("friendId", friendId)
                .put("reason", reason)
                .put("clientRequestId", clientRequestId)
                .apply { if (messageId != null) put("messageId", messageId) },
        )

    fun sendFriendInvite(
        friendToken: String,
        friendId: String,
        matchId: String,
        roomPlayerToken: String,
        clientRequestId: String,
    ): JSONObject =
        request(
            method = "POST",
            path = "/api/friends/invite",
            friendToken = friendToken,
            roomPlayerToken = roomPlayerToken,
            body = JSONObject()
                .put("friendId", friendId)
                .put("matchId", matchId)
                .put("clientRequestId", clientRequestId),
        )

    fun respondFriendInvite(
        friendToken: String,
        inviteId: String,
        accept: Boolean,
        clientRequestId: String? = null,
    ): JSONObject =
        request(
            method = "POST",
            path = "/api/friends/invite/respond",
            friendToken = friendToken,
            body = JSONObject()
                .put("inviteId", inviteId)
                .put("accept", accept)
                .apply {
                    if (accept) {
                        require(!clientRequestId.isNullOrBlank()) {
                            "clientRequestId is required when accepting a friend invite"
                        }
                        put("clientRequestId", clientRequestId)
                    }
                },
        )

    fun sendFriendMessage(
        friendToken: String,
        friendId: String,
        text: String,
        clientRequestId: String,
    ): JSONObject =
        request(
            method = "POST",
            path = "/api/friends/messages/send",
            friendToken = friendToken,
            body = JSONObject()
                .put("friendId", friendId)
                .put("text", text)
                .put("clientRequestId", clientRequestId),
        )

    fun friendMessages(friendToken: String, friendId: String): JSONObject =
        request(
            method = "POST",
            path = "/api/friends/messages/list",
            friendToken = friendToken,
            body = JSONObject().put("friendId", friendId),
        )

    fun friendRecent(friendToken: String): JSONObject =
        request(
            method = "POST",
            path = "/api/friends/recent",
            friendToken = friendToken,
            body = JSONObject(),
        )

    fun createFriendRoom(
        friendToken: String,
        displayName: String,
        clientRequestId: String,
        playerCount: Int,
        characterId: String = LudoPawsCharacterRuntime.selectedCharacterId(),
    ): JSONObject =
        request(
            method = "POST",
            path = "/api/matches",
            friendToken = friendToken,
            body = JSONObject()
                .put("displayName", displayName)
                .put("clientRequestId", clientRequestId)
                .put("characterId", characterId)
                .put("targetPlayerCount", playerCount)
                .put("matchMode", "FRIENDS")
                .put("rulesetKey", "CLASSIC_V4"),
        )

    fun searchPublicMatch(
        profileToken: String,
        displayName: String,
        clientRequestId: String,
        playerCount: Int,
        characterId: String = LudoPawsCharacterRuntime.selectedCharacterId(),
        rulesetKey: String = "CLASSIC_V4",
    ): JSONObject =
        request(
            method = "POST",
            path = "/api/matchmaking/search",
            profileToken = profileToken,
            body = JSONObject()
                .put("displayName", displayName)
                .put("clientRequestId", clientRequestId)
                .put("characterId", characterId)
                .put("playerCount", playerCount)
                .put("rulesetKey", rulesetKey),
        )

    fun publicMatchStatus(
        profileToken: String,
        clientRequestId: String,
        playerCount: Int,
    ): JSONObject =
        request(
            method = "POST",
            path = "/api/matchmaking/status",
            profileToken = profileToken,
            body = JSONObject()
                .put("clientRequestId", clientRequestId)
                .put("playerCount", playerCount),
        )

    fun cancelPublicMatch(
        profileToken: String,
        clientRequestId: String,
        playerCount: Int,
    ): JSONObject =
        request(
            method = "POST",
            path = "/api/matchmaking/cancel",
            profileToken = profileToken,
            body = JSONObject()
                .put("clientRequestId", clientRequestId)
                .put("playerCount", playerCount),
        )

    fun createMatch(
        profileToken: String,
        displayName: String,
        clientRequestId: String,
        characterId: String = LudoPawsCharacterRuntime.selectedCharacterId(),
        rulesetKey: String = "CLASSIC_V4",
    ): JSONObject =
        request(
            method = "POST",
            path = "/api/matches",
            profileToken = profileToken,
            body = JSONObject()
                .put("displayName", displayName)
                .put("clientRequestId", clientRequestId)
                .put("characterId", characterId)
                .put("rulesetKey", rulesetKey),
        )

    fun joinMatch(
        matchId: String,
        displayName: String,
        clientRequestId: String,
        profileToken: String? = null,
        friendJoinToken: String? = null,
        characterId: String = LudoPawsCharacterRuntime.selectedCharacterId(),
    ): JSONObject =
        request(
            method = "POST",
            path = "/api/matches/${matchId.uppercase()}/join",
            profileToken = profileToken,
            friendJoinToken = friendJoinToken,
            body = JSONObject()
                .put("displayName", displayName)
                .put("clientRequestId", clientRequestId)
                .put("characterId", characterId),
        )

    fun state(matchId: String, playerToken: String): JSONObject =
        request(
            method = "GET",
            path = "/api/matches/${matchId.uppercase()}/state",
            playerToken = playerToken,
        )

    fun start(matchId: String, playerToken: String): JSONObject =
        request(
            method = "POST",
            path = "/api/matches/${matchId.uppercase()}/start",
            playerToken = playerToken,
            body = JSONObject(),
        )

    fun roll(
        matchId: String,
        playerToken: String,
        clientRequestId: String,
        expectedEventIndex: Int,
    ): JSONObject =
        command(
            matchId = matchId,
            playerToken = playerToken,
            action = "ROLL",
            path = "roll",
            body = JSONObject()
                .put("clientRequestId", clientRequestId)
                .put("expectedEventIndex", expectedEventIndex),
        )

    fun move(
        matchId: String,
        playerToken: String,
        tokenIndex: Int,
        eventIndex: Int,
        clientRequestId: String = UUID.randomUUID().toString(),
    ): JSONObject =
        command(
            matchId = matchId,
            playerToken = playerToken,
            action = "MOVE",
            path = "move",
            body = JSONObject()
                .put("tokenIndex", tokenIndex)
                .put("eventIndex", eventIndex)
                .put("clientRequestId", clientRequestId),
        )

    private fun command(
        matchId: String,
        playerToken: String,
        action: String,
        path: String,
        body: JSONObject,
    ): JSONObject {
        require(body.optString("clientRequestId").isNotBlank()) { "clientRequestId is required" }
        val response = commandChannel?.invoke()?.executeCommand(matchId, playerToken, action, body)
        if (response != null) {
            if (response.has("error")) {
                throw GameApiException(
                    code = response.optString("error", "COMMAND_FAILED"),
                    message = response.optString("message", "Match command failed"),
                )
            }
            return response
        }
        // Socket acknowledgement loss is ambiguous: keep the command ID so the
        // authoritative server can replay its result without taking a second turn.
        return request(
            method = "POST",
            path = "/api/matches/${matchId.uppercase()}/$path",
            playerToken = playerToken,
            body = body,
        )
    }

    private fun request(
        method: String,
        path: String,
        playerToken: String? = null,
        friendToken: String? = null,
        profileToken: String? = null,
        roomPlayerToken: String? = null,
        friendJoinToken: String? = null,
        body: JSONObject? = null,
    ): JSONObject {
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("Accept", "application/json")
            .header("User-Agent", "LudoProof-Android/" + BuildConfig.VERSION_NAME)
        val bearerToken = playerToken ?: friendToken ?: profileToken
        if (bearerToken != null) request.header("Authorization", "Bearer $bearerToken")
        if (roomPlayerToken != null) request.header("X-LudoProof-Room-Token", roomPlayerToken)
        if (friendJoinToken != null) request.header("X-LudoProof-Friend-Join-Token", friendJoinToken)
        request.method(method, body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType()))
        return GameHttpTransport.executeJson(client, request.build(), MAX_RESPONSE_BYTES)
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 512 * 1024
    }
}

class GameApiException(
    val code: String,
    override val message: String,
) : RuntimeException(message)
