package com.rekixo.ludopaws

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GameApiCommandFallbackTest {
    private fun respondingClient(requestBodies: MutableList<String>) =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val body = Buffer()
            chain.request().body?.writeTo(body)
            requestBodies.add(body.readUtf8())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"ok":true,"replayed":true}""".toResponseBody())
                .build()
        }).build()

    @Test fun rollFallbackPreservesOriginalIdWhenSocketAcknowledgementWasLost() {
        val requests = mutableListOf<String>()
        var fromSocket: String? = null
        val socket = MatchCommandChannel { _, _, action, body ->
            assertEquals("ROLL", action)
            fromSocket = body.optString("clientRequestId")
            null // ambiguous socket delivery -> REST uses exactly the same request ID
        }
        val api = GameApi("https://api.example.test", respondingClient(requests), { socket })
        val requestId = "00000000-0000-4000-8000-000000000011"
        assertTrue(api.roll("lpabcdefgh", "test-player-token", requestId, 3).optBoolean("replayed"))
        assertEquals(requestId, fromSocket)
        assertEquals(1, requests.size)
        val fallback = JSONObject(requests.single())
        assertEquals(requestId, fallback.optString("clientRequestId"))
        assertEquals(3, fallback.optInt("expectedEventIndex"))
    }

    @Test fun moveFallbackRetainsAllFieldsAndDoesNotCreateANewMutation() {
        val requests = mutableListOf<String>()
        val api = GameApi("https://api.example.test", respondingClient(requests),
            { MatchCommandChannel { _, _, _, _ -> null } })
        val id = "00000000-0000-4000-8000-000000000012"
        api.move("lpabcdefgh", "test-player-token", 2, 7, id)
        val body = JSONObject(requests.single())
        assertEquals(id, body.getString("clientRequestId"))
        assertEquals(2, body.getInt("tokenIndex"))
        assertEquals(7, body.getInt("eventIndex"))
    }

    @Test fun authoritativeSocketRejectionMustNotBeSilentlyRetriedOverRest() {
        val requests = mutableListOf<String>()
        val api = GameApi("https://api.example.test", respondingClient(requests),
            { MatchCommandChannel { _, _, _, _ ->
                JSONObject().put("error", "NOT_YOUR_TURN").put("message", "Please wait")
            } })
        try {
            api.roll("LPABCDEFGH", "test-player-token", "test-request", 0)
            fail("Expected authoritative socket rejection")
        } catch (error: GameApiException) {
            assertEquals("NOT_YOUR_TURN", error.code)
        }
        assertTrue(requests.isEmpty())
    }
}
