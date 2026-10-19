package com.rekixo.ludopaws

import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MatchRealtimeClientIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var transportClient: OkHttpClient
    private lateinit var realtimeClient: MatchRealtimeClient

    @Before
    fun setUp() {
        val heldCertificate =
            HeldCertificate
                .Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .addSubjectAlternativeName("127.0.0.1")
                .build()
        val serverCertificates =
            HandshakeCertificates
                .Builder()
                .heldCertificate(heldCertificate)
                .build()
        val clientCertificates =
            HandshakeCertificates
                .Builder()
                .addTrustedCertificate(
                    heldCertificate.certificate,
                )
                .build()

        server = MockWebServer()
        server.useHttps(
            serverCertificates.sslSocketFactory(),
            false,
        )
        server.start()

        transportClient =
            OkHttpClient
                .Builder()
                .sslSocketFactory(
                    clientCertificates.sslSocketFactory(),
                    clientCertificates.trustManager,
                )
                .build()
    }

    @After
    fun tearDown() {
        if (::realtimeClient.isInitialized) {
            realtimeClient.shutdown()
        }
        if (::transportClient.isInitialized) {
            transportClient.dispatcher.executorService.shutdown()
            transportClient.connectionPool.evictAll()
        }
        if (::server.isInitialized) {
            server.shutdown()
        }
    }

    @Test
    fun handshakeFailureReconnectsAndDeliversAuthoritativeRevision() {
        val revisionLatch = CountDownLatch(1)
        val connectedLatch = CountDownLatch(1)
        val connectionEvents = CopyOnWriteArrayList<Boolean>()
        val revisions = CopyOnWriteArrayList<Pair<Int, String?>>()
        val websocketOpens = AtomicInteger(0)

        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("temporary transport failure"),
        )
        server.enqueue(
            MockResponse()
                .withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            websocketOpens.incrementAndGet()
                            webSocket.send(
                                """{"type":"STATE_CHANGED","revision":42,"status":"ACTIVE"}""",
                            )
                        }

                        override fun onClosing(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            webSocket.close(code, reason)
                        }
                    },
                ),
        )

        realtimeClient =
            MatchRealtimeClient(
                baseUrl =
                    server.url("/")
                        .toString()
                        .trimEnd('/'),
                onRevision = {
                        revision,
                        status,
                    ->
                    revisions += revision to status
                    revisionLatch.countDown()
                },
                onConnectionChanged = {
                        connected ->
                    connectionEvents += connected
                    if (connected) {
                        connectedLatch.countDown()
                    }
                },
                client = transportClient,
                retryDelayMillis = { 25L },
            )

        realtimeClient.connect(
            matchId = MATCH_ID.lowercase(),
            playerToken = PLAYER_TOKEN,
        )

        assertTrue(
            "reconnected websocket did not open",
            connectedLatch.await(5, TimeUnit.SECONDS),
        )
        assertTrue(
            "reconnected websocket did not deliver state revision",
            revisionLatch.await(5, TimeUnit.SECONDS),
        )

        val firstRequest =
            server.takeRequest(2, TimeUnit.SECONDS)
        val secondRequest =
            server.takeRequest(2, TimeUnit.SECONDS)

        assertNotNull(firstRequest)
        assertNotNull(secondRequest)
        assertEquals(
            "/api/matches/$MATCH_ID/events",
            firstRequest?.path,
        )
        assertEquals(
            "/api/matches/$MATCH_ID/events",
            secondRequest?.path,
        )
        assertEquals(
            "Bearer $PLAYER_TOKEN",
            secondRequest?.getHeader("Authorization"),
        )
        assertEquals(1, websocketOpens.get())
        assertEquals(
            listOf(42 to "ACTIVE"),
            revisions.toList(),
        )
        assertTrue(
            "initial handshake failure should surface disconnected state",
            connectionEvents.contains(false),
        )
        assertTrue(
            "successful retry should surface connected state",
            connectionEvents.contains(true),
        )
    }

    @Test
    fun friendsVoiceEventsAreDeliveredAndSentOnAuthenticatedSocket() {
        val connectedLatch = CountDownLatch(1)
        val peersLatch = CountDownLatch(1)
        val signalLatch = CountDownLatch(1)
        val outboundLatch = CountDownLatch(2)
        val serverSocket = AtomicReference<WebSocket?>()
        val outboundMessages = CopyOnWriteArrayList<String>()
        val voicePeers = AtomicReference<Set<String>>()
        val incomingSignal = AtomicReference<Pair<String, String>>()

        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        serverSocket.set(webSocket)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        outboundMessages += text
                        outboundLatch.countDown()
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }
                },
            ),
        )
        realtimeClient =
            MatchRealtimeClient(
                baseUrl = server.url("/").toString().trimEnd('/'),
                onRevision = { _, _ -> },
                onConnectionChanged = { if (it) connectedLatch.countDown() },
                onVoicePeers = {
                    voicePeers.set(it)
                    peersLatch.countDown()
                },
                onVoiceSignal = { playerId, signal ->
                    incomingSignal.set(playerId to signal.optString("sdp"))
                    signalLatch.countDown()
                },
                client = transportClient,
                retryDelayMillis = { 25L },
            )

        realtimeClient.connect(MATCH_ID, PLAYER_TOKEN)
        assertTrue("voice WebSocket did not connect", connectedLatch.await(5, TimeUnit.SECONDS))
        assertTrue(realtimeClient.sendVoiceJoin())
        assertTrue(
            realtimeClient.sendVoiceSignal(
                "LPPEER12345",
                org.json.JSONObject().put("type", "offer").put("sdp", "test-offer"),
            ),
        )
        assertTrue("voice messages were not sent", outboundLatch.await(5, TimeUnit.SECONDS))
        val connectedSocket = serverSocket.get()
        assertNotNull(connectedSocket)
        connectedSocket?.send("""{"type":"VOICE_PEERS","playerIds":["LPPEER12345"]}""")
        connectedSocket?.send(
            """{"type":"VOICE_SIGNAL","fromPlayerId":"LPPEER12345","signal":{"type":"answer","sdp":"test-answer"}}""",
        )

        assertTrue("voice peer list was not delivered", peersLatch.await(5, TimeUnit.SECONDS))
        assertTrue("voice signal was not delivered", signalLatch.await(5, TimeUnit.SECONDS))
        assertEquals(setOf("LPPEER12345"), voicePeers.get())
        assertEquals("LPPEER12345" to "test-answer", incomingSignal.get())
        assertTrue(outboundMessages.any { it.contains("VOICE_JOIN") })
        assertTrue(outboundMessages.any { it.contains("test-offer") && it.contains("LPPEER12345") })
    }

    private companion object {
        const val MATCH_ID = "LPABCDEFGH"
        const val PLAYER_TOKEN =
            "lp_abcdefghijklmnopqrstuvwxyzABCDEF"
    }
}
