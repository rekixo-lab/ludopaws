package com.rekixo.ludopaws

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import com.rekixo.ludopaws.ui.quickchat.QuickChatEmojiCatalog
import java.io.InterruptedIOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class MatchRealtimeClient(
    baseUrl: String =
        BuildConfig.LUDOPROOF_API_BASE_URL,
    private val onRevision:
        (
            revision: Int,
            status: String?,
        ) -> Unit,
    private val onConnectionChanged:
        (
            connected: Boolean,
        ) -> Unit = {},
    private val onQuickChat:
        (
            playerId: String,
            displayName: String,
            emoji: String,
        ) -> Unit = { _, _, _ -> },
    private val onVoicePeers:
        (
            playerIds: Set<String>,
        ) -> Unit = {},
    private val onVoiceSignal:
        (
            fromPlayerId: String,
            signal: JSONObject,
        ) -> Unit = { _, _ -> },
    private val onVoiceEnded:
        () -> Unit = {},
    private val onUnavailable: (String) -> Unit = {},
    private val client: OkHttpClient = GameHttpTransport.client,
    private val retryDelayMillis:
        (attempt: Int) -> Long =
        { attempt ->
            RealtimeReconnectBackoff
                .delayMillis(
                    attempt = attempt,
                    jitterUnit =
                        ThreadLocalRandom
                            .current()
                            .nextDouble(),
                )
        },
    private val commandTimeoutMillis: Long = 1_500L,
    private val onSnapshot: ((JSONObject) -> Unit)? = null,
) : MatchCommandChannel {
    private val wsBaseUrl =
        baseUrl
            .trimEnd('/')
            .replaceFirst(
                "https://",
                "wss://",
            )

    private val generation =
        AtomicLong(0L)
    private val stateLock =
        Any()
    private val pendingCommands = mutableMapOf<String, PendingCommand>()
    private val retryExecutor =
        Executors.newSingleThreadScheduledExecutor {
                runnable ->
            Thread(
                runnable,
                "ludo-paws-realtime-retry",
            ).apply {
                isDaemon = true
            }
        }

    private var desiredSession:
        DesiredSession? =
        null
    private var retryFuture:
        ScheduledFuture<*>? =
        null
    private var reconnectAttempt =
        0
    private var connecting =
        false
    private var connected =
        false
    private var shutDown = false
    private var terminalSession: DesiredSession? = null

    @Volatile
    private var socket:
        WebSocket? =
        null

    init {
        require(
            wsBaseUrl
                .startsWith(
                    "wss://",
                ),
        ) {
            "LudoProof realtime must use WSS"
        }
    }

    fun connect(
        matchId: String,
        playerToken: String,
    ) {
        val session =
            DesiredSession(
                matchId =
                    matchId.uppercase(),
                playerToken =
                    playerToken,
            )
        var staleSocket:
            WebSocket? =
            null
        val shouldOpen =
            synchronized(
                stateLock,
            ) {
                if (shutDown || terminalSession == session) return
                if (
                    desiredSession !=
                    session
                ) {
                    releasePendingCommandsLocked()
                    desiredSession =
                        session
                    reconnectAttempt =
                        0
                    retryFuture
                        ?.cancel(false)
                    retryFuture =
                        null
                    generation
                        .incrementAndGet()
                    staleSocket =
                        socket
                    socket =
                        null
                    connecting =
                        false
                    connected =
                        false
                }

                val retryScheduled =
                    retryFuture
                        ?.let {
                            !it.isDone &&
                                !it.isCancelled
                        }
                        ?: false

                !connected &&
                    !connecting &&
                    !retryScheduled
            }

        staleSocket
            ?.cancel()

        if (shouldOpen) {
            openSocket(
                session,
            )
        }
    }

    private fun openSocket(
        session: DesiredSession,
    ) {
        val currentGeneration =
            synchronized(
                stateLock,
            ) {
                if (
                    shutDown || desiredSession !=
                        session ||
                    connected ||
                    connecting
                ) {
                    return
                }

                retryFuture =
                    null
                connecting =
                    true
                generation
                    .incrementAndGet()
            }

        val request =
            Request
                .Builder()
                .url(
                    wsBaseUrl +
                        "/api/matches/" +
                        session.matchId +
                        "/events",
                )
                .header(
                    "Authorization",
                    "Bearer ${session.playerToken}",
                )
                .header(
                    "User-Agent",
                    "LudoProof-Android/" +
                        BuildConfig.VERSION_NAME,
                )
                .build()

        val createdSocket =
            client
                .newWebSocket(
                    request,
                    listener(
                        session =
                            session,
                        connectionGeneration =
                            currentGeneration,
                    ),
                )

        val keepSocket =
            synchronized(
                stateLock,
            ) {
                val keep = desiredSession ==
                    session &&
                    generation.get() ==
                    currentGeneration &&
                    (
                        connecting ||
                            connected
                    )
                if (keep) socket = createdSocket
                keep
            }
        if (!keepSocket) {
            createdSocket.cancel()
        }
    }

    private fun listener(
        session: DesiredSession,
        connectionGeneration: Long,
    ): WebSocketListener =
        object :
            WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                val accepted =
                    synchronized(
                        stateLock,
                    ) {
                        if (
                            shutDown || generation.get() !=
                                connectionGeneration ||
                            desiredSession !=
                                session
                        ) {
                            false
                        } else {
                            socket = webSocket
                            connecting =
                                false
                            connected =
                                true
                            reconnectAttempt =
                                0
                            retryFuture
                                ?.cancel(false)
                            retryFuture =
                                null
                            // Preserve callback order with disconnect/reconnect:
                            // the UI must never see an old socket become connected.
                            onConnectionChanged(true)
                            true
                        }
                    }

                if (!accepted) {
                    webSocket.cancel()
                    return
                }
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                if (
                    !isCurrent(
                        session,
                        connectionGeneration,
                    )
                ) {
                    return
                }

                if (text.length > MAX_REALTIME_MESSAGE_CHARS) return

                val payload =
                    runCatching {
                        JSONObject(
                            text,
                        )
                    }
                        .getOrNull()
                        ?: return

                val type =
                    payload
                        .optString(
                            "type",
                        )
                if (type == "command_result") {
                    synchronized(stateLock) {
                        if (!isCurrent(session, connectionGeneration)) return
                        val pending = pendingCommands.remove(payload.optString("requestId"))
                        if (pending != null) {
                            pending.response = payload.optJSONObject("response")
                            val statusCode = payload.optInt("statusCode", 200)
                            if (statusCode !in 200..299 && pending.response?.has("error") == false) {
                                pending.response?.put("error", "HTTP_$statusCode")
                            }
                            pending.completed.countDown()
                        }
                    }
                    return
                }
                if (type == "VOICE_PEERS") {
                    val peers = payload.optJSONArray("playerIds")
                    val playerIds = buildSet {
                        if (peers != null) {
                            for (index in 0 until peers.length()) {
                                peers.optString(index)
                                    .takeIf(String::isNotBlank)
                                    ?.let(::add)
                            }
                        }
                    }
                    onVoicePeers(playerIds)
                    return
                }
                if (type == "VOICE_SIGNAL") {
                    val sender = payload.optString("fromPlayerId").trim()
                    val signal = payload.optJSONObject("signal")
                    if (sender.isNotEmpty() && signal != null) {
                        onVoiceSignal(sender, signal)
                    }
                    return
                }
                if (type == "VOICE_ENDED") {
                    onVoiceEnded()
                    return
                }
                if (type == "QUICK_CHAT") {
                    val senderId = payload.optString("playerId").trim()
                    val displayName = payload.optString("displayName").trim()
                    val emoji = payload.optString("emoji")
                    if (
                        senderId.isNotEmpty() &&
                        displayName.isNotEmpty() &&
                        emoji in QuickChatEmojiCatalog.EMOJIS
                    ) {
                        onQuickChat(senderId, displayName, emoji)
                    }
                    return
                }
                if (
                    type !=
                        "SYNC" &&
                    type !=
                        "STATE_CHANGED"
                ) {
                    return
                }

                val incomingMatchId = payload.optString("matchId")
                if (incomingMatchId.isNotBlank() && incomingMatchId != session.matchId) return

                val snapshot = payload.optJSONObject("state")
                if (
                    onSnapshot != null && snapshot != null &&
                    snapshot.optString("matchId") == session.matchId &&
                    snapshot.optLong("revision", -1L) >= 0L &&
                    snapshot.optLong("revision") == payload.optLong("revision", -1L)
                ) {
                    synchronized(stateLock) {
                        if (isCurrent(session, connectionGeneration)) onSnapshot.invoke(payload)
                    }
                    return
                }

                val revision =
                    payload
                        .optInt(
                            "revision",
                            -1,
                        )
                if (
                    revision >=
                    0
                ) {
                    synchronized(stateLock) {
                        if (isCurrent(session, connectionGeneration)) {
                            onRevision(
                                revision,
                                payload.optString("status").takeIf { it.isNotBlank() },
                            )
                        }
                    }
                }
            }

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                webSocket.close(
                    code,
                    reason,
                )
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                handleTransportClosed(
                    session =
                        session,
                    connectionGeneration =
                        connectionGeneration,
                    terminalReason = if (code == 1008) "Match connection is no longer authorized." else null,
                )
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                val terminal = response?.code?.takeIf(RealtimeConnectionFailurePolicy::isTerminalHttp)
                handleTransportClosed(
                    session = session,
                    connectionGeneration = connectionGeneration,
                    terminalReason = terminal?.let(RealtimeConnectionFailurePolicy::userMessage),
                )
            }
        }

    private fun handleTransportClosed(
        session: DesiredSession,
        connectionGeneration: Long,
        terminalReason: String? = null,
    ) {
        val wasActive = synchronized(stateLock) {
            if (generation.get() != connectionGeneration || desiredSession != session) return
            val active = connected || connecting
            connected = false
            releasePendingCommandsLocked()
            connecting = false
            socket = null
            if (terminalReason != null) {
                // Repeated Activity polling must not reconnect with credentials already rejected.
                terminalSession = session
                desiredSession = null
                retryFuture?.cancel(false)
                retryFuture = null
            } else {
                scheduleRetryLocked(session)
            }
            active
        }
        if (wasActive) onConnectionChanged(false)
        if (terminalReason != null) onUnavailable(terminalReason)
    }

    private fun scheduleRetryLocked(
        session: DesiredSession,
    ) {
        if (
            shutDown || desiredSession !=
                session ||
            connected ||
            connecting ||
            retryExecutor.isShutdown
        ) {
            return
        }

        val existing =
            retryFuture
        if (
            existing != null &&
            !existing.isDone &&
            !existing.isCancelled
        ) {
            return
        }

        val attempt =
            reconnectAttempt
        val delayMillis =
            retryDelayMillis(
                attempt,
            ).coerceAtLeast(0L)
        reconnectAttempt =
            (
                reconnectAttempt +
                    1
                )
                .coerceAtMost(4)

        retryFuture =
            retryExecutor.schedule(
                {
                    val nextSession =
                        synchronized(
                            stateLock,
                        ) {
                            retryFuture =
                                null
                            desiredSession
                                ?.takeIf {
                                    it ==
                                        session &&
                                        !connected &&
                                        !connecting
                                }
                        }
                    if (
                        nextSession !=
                        null
                    ) {
                        openSocket(
                            nextSession,
                        )
                    }
                },
                delayMillis,
                TimeUnit.MILLISECONDS,
            )
    }

    private fun isCurrent(
        session: DesiredSession,
        connectionGeneration: Long,
    ): Boolean =
        synchronized(
            stateLock,
        ) {
            generation.get() ==
                connectionGeneration &&
                desiredSession ==
                session
        }

    /** Called from the same worker thread used for REST; never block the UI or socket callback. */
    override fun executeCommand(
        matchId: String,
        playerToken: String,
        action: String,
        body: JSONObject,
    ): JSONObject? {
        require(action == "ROLL" || action == "MOVE") { "Unsupported match command" }
        require(body.optString("clientRequestId").isNotBlank()) { "clientRequestId is required" }
        val requestId = UUID.randomUUID().toString()
        val pending = PendingCommand()
        synchronized(stateLock) {
            val session = desiredSession
            val activeSocket = socket
            if (
                !connected || activeSocket == null || session == null ||
                session.matchId != matchId.uppercase() || session.playerToken != playerToken ||
                pendingCommands.size >= MAX_PENDING_COMMANDS
            ) return null
            pendingCommands[requestId] = pending
            val sent = runCatching {
                activeSocket.send(
                    JSONObject()
                        .put("type", "command")
                        .put("requestId", requestId)
                        .put("action", action)
                        .put("body", body)
                        .toString(),
                )
            }.getOrDefault(false)
            if (!sent) {
                pendingCommands.remove(requestId)
                return null
            }
        }
        try {
            if (!pending.completed.await(commandTimeoutMillis.coerceAtLeast(1L), TimeUnit.MILLISECONDS)) {
                return null
            }
            return pending.response
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Match command interrupted")
        } finally {
            synchronized(stateLock) { pendingCommands.remove(requestId) }
        }
    }

    private fun releasePendingCommandsLocked() {
        pendingCommands.values.forEach { it.completed.countDown() }
        pendingCommands.clear()
    }

    fun sendQuickChat(emoji: String): Boolean {
        if (emoji !in QuickChatEmojiCatalog.EMOJIS) return false
        return sendRealtimeMessage(
            JSONObject()
                .put("type", "QUICK_CHAT_SEND")
                .put("emoji", emoji),
        )
    }

    fun sendVoiceJoin(): Boolean =
        sendRealtimeMessage(
            JSONObject().put("type", "VOICE_JOIN"),
        )

    fun sendVoiceLeave(): Boolean =
        sendRealtimeMessage(
            JSONObject().put("type", "VOICE_LEAVE"),
        )

    fun sendVoiceSignal(
        playerId: String,
        signal: JSONObject,
    ): Boolean {
        if (playerId.isBlank()) return false
        return sendRealtimeMessage(
            JSONObject()
                .put("type", "VOICE_SIGNAL")
                .put("toPlayerId", playerId)
                .put("signal", signal),
        )
    }

    private fun sendRealtimeMessage(payload: JSONObject): Boolean {
        val activeSocket =
            synchronized(stateLock) {
                if (connected && desiredSession != null) socket else null
            } ?: return false
        return runCatching { activeSocket.send(payload.toString()) }.getOrDefault(false)
    }

    fun disconnect() {
        val staleSocket =
            synchronized(
                stateLock,
            ) {
                releasePendingCommandsLocked()
                generation
                    .incrementAndGet()
                desiredSession =
                    null
                terminalSession =
                    null
                retryFuture
                    ?.cancel(false)
                retryFuture =
                    null
                reconnectAttempt =
                    0
                connecting =
                    false
                connected =
                    false
                val previousSocket = socket
                socket = null
                onConnectionChanged(false)
                previousSocket
            }

        staleSocket
            ?.close(
                1000,
                "screen stopped",
            )
    }

    fun shutdown() {
        synchronized(stateLock) { shutDown = true }
        disconnect()
        retryExecutor
            .shutdownNow()
        // The process-wide client (or injected client) also serves REST and other
        // screens. Its owner controls its pool and dispatcher lifetime.
    }

    private data class DesiredSession(
        val matchId: String,
        val playerToken: String,
    )

    private class PendingCommand {
        val completed = CountDownLatch(1)
        @Volatile var response: JSONObject? = null
    }

    private companion object {
        const val MAX_PENDING_COMMANDS = 16
        const val MAX_REALTIME_MESSAGE_CHARS = 512 * 1024
    }
}
