package com.rekixo.ludopaws.feature.team.data.remote

import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.feature.characters.data.local.LudoPawsCharacterRuntime
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class TeamMatchmakingApi(
    baseUrl: String = BuildConfig.LUDOPROOF_API_BASE_URL,
) {
    private val baseUrl = baseUrl.trimEnd('/')

    fun search(
        profileToken: String,
        displayName: String,
        clientRequestId: String,
        characterId: String = LudoPawsCharacterRuntime.selectedCharacterId(),
    ): JSONObject =
        request(
            path = "/api/team-matchmaking/search",
            profileToken = profileToken,
            body = JSONObject()
                .put("displayName", com.rekixo.ludopaws.feature.safety.FamilyOnlineSafety.publicName(displayName))
                .put("clientRequestId", clientRequestId)
                .put("characterId", characterId)
                .put("rulesetKey", "TEAM_UP_V5"),
        )

    fun status(
        profileToken: String,
        clientRequestId: String,
    ): JSONObject =
        request(
            path = "/api/team-matchmaking/status",
            profileToken = profileToken,
            body = JSONObject()
                .put("clientRequestId", clientRequestId),
        )

    fun cancel(
        profileToken: String,
        clientRequestId: String,
    ): JSONObject =
        request(
            path = "/api/team-matchmaking/cancel",
            profileToken = profileToken,
            body = JSONObject()
                .put("clientRequestId", clientRequestId),
        )

    private fun request(
        path: String,
        profileToken: String,
        body: JSONObject,
    ): JSONObject {
        require(baseUrl.startsWith("https://")) {
            "LudoProof API must use HTTPS"
        }
        require(profileToken.isNotBlank())

        val connection =
            URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty(
                "Authorization",
                "Bearer $profileToken",
            )
            connection.setRequestProperty(
                "User-Agent",
                "LudoProof-Android/" + BuildConfig.VERSION_NAME,
            )

            val bytes = body.toString().toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_REQUEST_BYTES) {
                "Team matchmaking request exceeded the safety limit."
            }
            connection.outputStream.use { it.write(bytes) }

            val status = connection.responseCode
            val stream =
                if (status in 200..299) connection.inputStream
                else connection.errorStream
            val text = stream
                ?.use { readUtf8Limited(it, MAX_RESPONSE_BYTES) }
                .orEmpty()
            val json =
                if (text.isBlank()) JSONObject()
                else JSONObject(text)

            if (status !in 200..299) {
                throw TeamMatchmakingApiException(
                    code = json.optString("error").ifBlank { null },
                    message = json.optString(
                        "message",
                        "Team matchmaking failed with HTTP $status",
                    ),
                )
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun readUtf8Limited(
        stream: InputStream,
        maxBytes: Int,
    ): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) {
                throw TeamMatchmakingApiException(
                    code = "RESPONSE_TOO_LARGE",
                    message = "Team matchmaking response exceeded the safety limit.",
                )
            }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val MAX_REQUEST_BYTES = 8 * 1024
        const val MAX_RESPONSE_BYTES = 512 * 1024
    }
}

class TeamMatchmakingApiException(
    val code: String?,
    message: String,
) : Exception(message)
