package com.rekixo.ludopaws

import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal data class RemoteForfeitSnapshot(
    val status: String,
    val winnerPlayerId: String?,
)

internal object RemoteForfeitApi {
    fun forfeit(
        matchId: String,
        playerToken: String,
        baseUrl: String =
            BuildConfig.LUDOPROOF_API_BASE_URL,
    ): RemoteForfeitSnapshot =
        request(
            method = "POST",
            matchId = matchId,
            playerToken = playerToken,
            baseUrl = baseUrl,
            body = JSONObject().put("forfeit", true),
        )

    fun state(
        matchId: String,
        playerToken: String,
        baseUrl: String =
            BuildConfig.LUDOPROOF_API_BASE_URL,
    ): RemoteForfeitSnapshot =
        request(
            method = "GET",
            matchId = matchId,
            playerToken = playerToken,
            baseUrl = baseUrl,
            body = null,
        )

    private fun request(
        method: String,
        matchId: String,
        playerToken: String,
        baseUrl: String,
        body: JSONObject?,
    ): RemoteForfeitSnapshot {
        require(
            baseUrl.startsWith(
                "https://",
            ),
        ) {
            "LudoProof API must use HTTPS"
        }

        val normalizedBase =
            baseUrl.trimEnd('/')
        val normalizedMatchId =
            matchId
                .trim()
                .uppercase()
        val request = Request.Builder()
            .url(
                normalizedBase + "/api/matches/" + normalizedMatchId +
                    if (method == "GET") "/state" else "/start",
            )
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $playerToken")
            .header("User-Agent", "LudoProof-Android/" + BuildConfig.VERSION_NAME)
            .method(
                method,
                body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType()),
            )
            .build()
        val payload = GameHttpTransport.executeJson(
            GameHttpTransport.exitClient,
            request,
            MAX_RESPONSE_BYTES,
        )
        val state =
            payload.optJSONObject("state")
                ?: throw GameApiException(
                    code = "INVALID_FORFEIT_RESPONSE",
                    message = "Server response did not include authoritative match state.",
                )
        val matchStatus =
            state.optString("status")
        if (matchStatus.isBlank()) {
            throw GameApiException(
                code = "INVALID_FORFEIT_RESPONSE",
                message = "Server response did not include match status.",
            )
        }
        return RemoteForfeitSnapshot(
            status = matchStatus,
            winnerPlayerId =
                state.optString("winnerPlayerId")
                    .takeIf(String::isNotBlank),
        )
    }

    private const val MAX_RESPONSE_BYTES = 64 * 1024
}
