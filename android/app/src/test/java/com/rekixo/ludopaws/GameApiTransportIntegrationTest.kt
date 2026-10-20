package com.rekixo.ludopaws

import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList

class GameApiTransportIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private var realtime: MatchRealtimeClient? = null
    private val serverSocket = AtomicReference<WebSocket>()
    private val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server = MockWebServer()
        server.useHttps(serverTls.sslSocketFactory(), false)
        server.start()
        client = GameHttpTransport.client.newBuilder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .build()
    }

    @After
    fun tearDown() {
        realtime?.shutdown()
        server.shutdown()
        // newBuilder shares the application dispatcher and pool; this fixture owns
        // its TLS connections but must not shut down that shared dispatcher.
        client.connectionPool.evictAll()
    }

    @Test
    fun familyRemotePayloadNeverSendsThePrivateDeviceNickname() {
        server.enqueue(MockResponse().setBody("{}"))
        val privateName = "phone-1234567890"
        GameApi(baseUrl, client).registerFriend(privateName, java.util.UUID.randomUUID().toString())
        val sent = server.takeRequest(2, TimeUnit.SECONDS)!!
        val raw = sent.body.readUtf8()
        assertFalse(raw.contains(privateName))
        assertEquals("Player", JSONObject(raw).getString("displayName"))
    }

    @Test
    fun restUsesAuthenticatedJsonAndReusesConnection() {
        repeat(2) { server.enqueue(MockResponse().setBody("""{"state":{"revision":8}}""")) }
        val api = GameApi(baseUrl, client)
        api.roll(MATCH_ID.lowercase(), PLAYER_TOKEN, "roll-once", 8)
        api.move(MATCH_ID, PLAYER_TOKEN, 2, 8, "move-once")
        val roll = server.takeRequest(2, TimeUnit.SECONDS)!!
        val move = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/matches/$MATCH_ID/roll", roll.path)
        assertEquals("Bearer $PLAYER_TOKEN", roll.getHeader("Authorization"))
        assertTrue(roll.getHeader("Content-Type")!!.startsWith("application/json"))
        val rollBody = JSONObject(roll.body.readUtf8())
        assertEquals("roll-once", rollBody.getString("clientRequestId"))
        assertEquals(8, rollBody.getInt("expectedEventIndex"))
        val moveBody = JSONObject(move.body.readUtf8())
        assertEquals("move-once", moveBody.getString("clientRequestId"))
        assertEquals(2, moveBody.getInt("tokenIndex"))
        assertEquals(8, moveBody.getInt("eventIndex"))
        assertEquals(1, move.sequenceNumber)
    }

    @Test
    fun liveSocketReturnsRollAndMoveWithoutRestRoundTrips() {
        val commands = mutableListOf<JSONObject>()
        connect { socket, command ->
            commands += command
            socket.send(
                JSONObject()
                    .put("type", "command_result")
                    .put("requestId", command.getString("requestId"))
                    .put("statusCode", 200)
                    .put("response", JSONObject().put("state", JSONObject().put("revision", 10)))
                    .toString(),
            )
        }
        val api = GameApi(baseUrl, client, commandChannel = { realtime })
        assertEquals(10, api.roll(MATCH_ID, PLAYER_TOKEN, "roll-live", 8).getJSONObject("state").getInt("revision"))
        assertEquals(10, api.move(MATCH_ID, PLAYER_TOKEN, 1, 10, "move-live").getJSONObject("state").getInt("revision"))
        assertEquals(1, server.requestCount)
        assertEquals(listOf("ROLL", "MOVE"), commands.map { it.getString("action") })
        assertEquals("command", commands.first().getString("type"))
        assertEquals("roll-live", commands.first().getJSONObject("body").getString("clientRequestId"))
        assertFalse(commands.first().has("playerToken"))
    }

    @Test
    fun missingAcknowledgementFallsBackToIdenticalRestMove() {
        val sent = AtomicReference<JSONObject>()
        connect(commandTimeoutMillis = 75L) { _, command -> sent.set(command) }
        server.enqueue(MockResponse().setBody("""{"state":{"revision":12}}"""))
        val api = GameApi(baseUrl, client, commandChannel = { realtime })
        assertEquals(12, api.move(MATCH_ID, PLAYER_TOKEN, 3, 11, "same-move-id").getJSONObject("state").getInt("revision"))
        server.takeRequest(2, TimeUnit.SECONDS) // WebSocket upgrade
        val fallback = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertNotNull(sent.get())
        assertEquals("/api/matches/$MATCH_ID/move", fallback.path)
        assertEquals(sent.get().getJSONObject("body").toString(), JSONObject(fallback.body.readUtf8()).toString())
    }

    @Test
    fun socketLossFallsBackToSameRollIdWithoutWaitingForTimeout() {
        val sent = AtomicReference<JSONObject>()
        connect(commandTimeoutMillis = 5_000L) { socket, command ->
            sent.set(command)
            socket.close(1001, "transport lost after applying command")
        }
        server.enqueue(MockResponse().setBody("""{"state":{"revision":13}}"""))
        val api = GameApi(baseUrl, client, commandChannel = { realtime })
        val start = System.nanoTime()
        assertEquals(13, api.roll(MATCH_ID, PLAYER_TOKEN, "same-roll-id", 8).getJSONObject("state").getInt("revision"))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3_000L)
        server.takeRequest(2, TimeUnit.SECONDS)
        val fallback = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals(sent.get().getJSONObject("body").toString(), JSONObject(fallback.body.readUtf8()).toString())
    }

    @Test
    fun authoritativeCommandRejectionDoesNotRetryRest() {
        connect { socket, command ->
            socket.send(
                JSONObject()
                    .put("type", "command_result")
                    .put("requestId", command.getString("requestId"))
                    .put("statusCode", 409)
                    .put("response", JSONObject().put("error", "NOT_YOUR_TURN").put("message", "Wait for your turn"))
                    .toString(),
            )
        }
        val api = GameApi(baseUrl, client, commandChannel = { realtime })
        val error = assertThrows(GameApiException::class.java) { api.roll(MATCH_ID, PLAYER_TOKEN, "rejected-id", 8) }
        assertEquals("NOT_YOUR_TURN", error.code)
        assertEquals("Wait for your turn", error.message)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun sessionMismatchDoesNotSendCredentialsOrCommandsToPreviousRoom() {
        val received = CountDownLatch(1)
        connect { _, _ -> received.countDown() }
        server.enqueue(MockResponse().setBody("""{"state":{"revision":2}}"""))
        GameApi(baseUrl, client, commandChannel = { realtime }).roll("LPOTHER123", "other-token", "other-roll-id", 8)
        assertFalse(received.await(100, TimeUnit.MILLISECONDS))
        server.takeRequest(2, TimeUnit.SECONDS)
        val rest = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/matches/LPOTHER123/roll", rest.path)
        assertEquals("Bearer other-token", rest.getHeader("Authorization"))
    }

    @Test
    fun realtimeShutdownLeavesSharedRestClientUsable() {
        connect { _, _ -> }
        val stoppedClient = realtime!!
        stoppedClient.shutdown()
        stoppedClient.connect(MATCH_ID, PLAYER_TOKEN)
        realtime = null
        assertFalse(client.dispatcher.executorService.isShutdown)
        server.enqueue(MockResponse().setBody("""{"state":{"revision":4}}"""))
        assertEquals(4, GameApi(baseUrl, client).state(MATCH_ID, PLAYER_TOKEN).getJSONObject("state").getInt("revision"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun oversizedChunkedResponseAndRedirectsAreRejected() {
        val api = GameApi(baseUrl, client)
        server.enqueue(MockResponse().setChunkedBody("x".repeat(512 * 1024 + 1), 8 * 1024))
        val oversized = assertThrows(GameApiException::class.java) { api.state(MATCH_ID, PLAYER_TOKEN) }
        assertEquals("RESPONSE_TOO_LARGE", oversized.code)
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/redirected")))
        val redirect = assertThrows(GameApiException::class.java) { api.state(MATCH_ID, PLAYER_TOKEN) }
        assertEquals("HTTP_302", redirect.code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun plaintextEndpointIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { GameApi("http://localhost", client) }
    }

    @Test
    fun authoritativeSnapshotSkipsRevisionFetchAndLegacyEventsStillRefresh() {
        val snapshotDelivered = CountDownLatch(1)
        val revisionDelivered = CountDownLatch(1)
        val snapshots = CopyOnWriteArrayList<JSONObject>()
        val revisions = CopyOnWriteArrayList<Int>()
        connect(
            onSnapshot = { snapshots += it; snapshotDelivered.countDown() },
            onRevision = { revision, _ -> revisions += revision; revisionDelivered.countDown() },
        ) { _, _ -> }
        val socket = serverSocket.get()
        socket.send("""{"type":"STATE_CHANGED","matchId":"WRONG_ROOM","revision":90,"state":{"matchId":"WRONG_ROOM","revision":90}}""")
        socket.send("""{"type":"STATE_CHANGED","matchId":"$MATCH_ID","revision":12,"state":{"matchId":"$MATCH_ID","revision":12,"status":"ACTIVE"}}""")
        assertTrue(snapshotDelivered.await(2, TimeUnit.SECONDS))
        assertTrue(revisions.isEmpty())
        assertEquals(12, snapshots.single().getJSONObject("state").getInt("revision"))
        socket.send("""{"type":"STATE_CHANGED","matchId":"$MATCH_ID","revision":13,"status":"ACTIVE"}""")
        assertTrue(revisionDelivered.await(2, TimeUnit.SECONDS))
        assertEquals(listOf(13), revisions.toList())
    }

    @Test
    fun replacingRoomIgnoresPreviousSocketSnapshotsAndRevisions() {
        val secondOpened = CountDownLatch(1)
        val delivered = CountDownLatch(1)
        val revisions = CopyOnWriteArrayList<Int>()
        val snapshots = CopyOnWriteArrayList<JSONObject>()
        connect(
            onSnapshot = { snapshots += it; delivered.countDown() },
            onRevision = { revision, _ -> revisions += revision },
        ) { _, _ -> }
        val oldSocket = serverSocket.get()
        val nextSocket = AtomicReference<WebSocket>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                nextSocket.set(webSocket)
                secondOpened.countDown()
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }
        }))
        realtime!!.connect("LPNEWROOM1", PLAYER_TOKEN)
        oldSocket.send("""{"type":"STATE_CHANGED","matchId":"$MATCH_ID","revision":999,"state":{"matchId":"$MATCH_ID","revision":999}}""")
        oldSocket.send("""{"type":"STATE_CHANGED","revision":1000}""")
        assertTrue(secondOpened.await(2, TimeUnit.SECONDS))
        nextSocket.get().send("""{"type":"SYNC","matchId":"LPNEWROOM1","revision":2,"state":{"matchId":"LPNEWROOM1","revision":2}}""")
        assertTrue(delivered.await(2, TimeUnit.SECONDS))
        assertEquals(listOf("LPNEWROOM1"), snapshots.map { it.getJSONObject("state").getString("matchId") })
        assertTrue(revisions.isEmpty())
    }

    private fun connect(
        commandTimeoutMillis: Long = 1_500L,
        onSnapshot: ((JSONObject) -> Unit)? = null,
        onRevision: (Int, String?) -> Unit = { _, _ -> },
        onCommand: (WebSocket, JSONObject) -> Unit,
    ) {
        val opened = CountDownLatch(1)
        val serverOpened = CountDownLatch(1)
        serverSocket.set(null)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    serverSocket.set(webSocket)
                    serverOpened.countDown()
                }
                override fun onMessage(webSocket: WebSocket, text: String) = onCommand(webSocket, JSONObject(text))
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }
            }),
        )
        realtime = MatchRealtimeClient(
            baseUrl = baseUrl,
            onRevision = onRevision,
            onConnectionChanged = { if (it) opened.countDown() },
            client = client,
            retryDelayMillis = { 10_000L },
            commandTimeoutMillis = commandTimeoutMillis,
            onSnapshot = onSnapshot,
        ).also { it.connect(MATCH_ID, PLAYER_TOKEN) }
        assertTrue("WebSocket did not connect", opened.await(5, TimeUnit.SECONDS))
        // The client and server onOpen callbacks execute on different threads.
        // Do not return a socket fixture until the server has published its handle.
        assertTrue("WebSocket server did not open", serverOpened.await(5, TimeUnit.SECONDS))
    }

    private companion object {
        const val MATCH_ID = "LPABCDEFGH"
        const val PLAYER_TOKEN = "lp_abcdefghijklmnopqrstuvwxyzABCDEF"
    }
}
