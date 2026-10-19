package com.rekixo.ludopaws.feature.online

import android.content.Context
import android.util.Log
import com.rekixo.ludopaws.GameApiException
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.MainActivity
import com.rekixo.ludopaws.PendingRemoteForfeit
import com.rekixo.ludopaws.RemoteForfeitApi
import com.rekixo.ludopaws.SecureSessionStore
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileGameMode
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileMatchSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private val remoteForfeitExecutor =
    Executors
        .newSingleThreadExecutor { work ->
            Thread(
                work,
                "ludoproof-remote-forfeit",
            ).apply {
                isDaemon = true
            }
        }

private val inFlightForfeits =
    ConcurrentHashMap
        .newKeySet<String>()

internal fun MainActivity.abandonRemoteSessionState(): Boolean {
    val abandonedMatchId = matchId
    val abandonedPlayerToken = playerToken
    val abandonedPlayerId = playerId
    val needsForfeitResolution =
        !abandonedMatchId.isNullOrBlank() &&
            currentState?.status != "FINISHED"

    if (
        needsForfeitResolution &&
        !abandonedMatchId.isNullOrBlank()
    ) {
        if (
            abandonedPlayerToken.isNullOrBlank() ||
            abandonedPlayerId.isNullOrBlank()
        ) {
            showStatus(
                "Could not securely prepare the match exit. Stay in the match and sync before trying again.",
            )
            return false
        }
        try {
            secureSessionStore.savePendingForfeit(
                PendingRemoteForfeit(
                    matchId = abandonedMatchId,
                    playerId = abandonedPlayerId,
                    playerToken = abandonedPlayerToken,
                    ranked = gameMode.ranked,
                ),
            )
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Could not persist the pending match forfeit",
                error,
            )
            showStatus(
                "Could not securely save the forfeit. Stay in the match and try again.",
            )
            return false
        }
    }

    retryPendingRemoteForfeits(this)

    publicMatchmakingStore.clear()
    secureSessionStore.clear()
    cachedMatchStore.clear()

    matchId = null
    playerToken = null
    playerId = null
    currentState = null
    LudoProofTheme.setGameplayBackground(
        root = arcadeRootView,
        enabled = false,
    )

    realtimeConnected = false
    realtimeClient.disconnect()
    return true
}

internal fun retryPendingRemoteForfeits(context: Context) {
    val appContext = context.applicationContext
    remoteForfeitExecutor.execute {
        val store = SecureSessionStore(appContext)
        val pending =
            try {
                store.pendingForfeits()
            } catch (error: Exception) {
                Log.e(
                    TAG,
                    "Could not load pending match forfeits",
                    error,
                )
                return@execute
            }

        pending.forEach { operation ->
            val key = operation.matchId.trim().uppercase()
            if (!inFlightForfeits.add(key)) return@forEach
            try {
                processPendingForfeit(appContext, store, operation)
            } finally {
                inFlightForfeits.remove(key)
            }
        }
    }
}

private fun processPendingForfeit(
    context: Context,
    store: SecureSessionStore,
    operation: PendingRemoteForfeit,
) {
    try {
        var snapshot =
            try {
                RemoteForfeitApi.forfeit(
                    operation.matchId,
                    operation.playerToken,
                )
            } catch (error: GameApiException) {
                when (error.code) {
                    "INVALID_MATCH_STATUS",
                    "PLAYER_FORFEITED",
                    ->
                        try {
                            RemoteForfeitApi.state(
                                operation.matchId,
                                operation.playerToken,
                            )
                        } catch (stateError: GameApiException) {
                            if (stateError.code in TERMINAL_FORFEIT_CODES) {
                                store.clearPendingForfeit(operation.matchId)
                                return
                            }
                            throw stateError
                        }

                    in TERMINAL_FORFEIT_CODES -> {
                        store.clearPendingForfeit(operation.matchId)
                        return
                    }

                    else -> throw error
                }
            }

        repeat(FORFEIT_STATE_CHECKS) {
            if (snapshot.status == FINISHED_STATUS) {
                recordConfirmedResult(
                    context,
                    store,
                    operation,
                    snapshot.winnerPlayerId,
                )
                return
            }
            if (snapshot.status != ACTIVE_STATUS) {
                store.clearPendingForfeit(operation.matchId)
                return
            }
            Thread.sleep(FORFEIT_RECONCILE_DELAY_MS)
            snapshot =
                RemoteForfeitApi.state(
                    operation.matchId,
                    operation.playerToken,
                )
        }

        if (snapshot.status == FINISHED_STATUS) {
            recordConfirmedResult(
                context,
                store,
                operation,
                snapshot.winnerPlayerId,
            )
        }
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        Log.w(TAG, "Pending forfeit reconciliation was interrupted")
    } catch (error: Exception) {
        Log.e(
            TAG,
            "Pending forfeit remains queued for a later retry",
            error,
        )
    }
}

private fun recordConfirmedResult(
    context: Context,
    store: SecureSessionStore,
    operation: PendingRemoteForfeit,
    winnerPlayerId: String?,
) {
    if (operation.ranked) {
        ProfileStore(context)
            .recordCompletedMatch(
                matchId = operation.matchId,
                mode = ProfileGameMode.CLASSIC,
                source = ProfileMatchSource.ONLINE,
                won = winnerPlayerId == operation.playerId,
            )
    }
    store.clearPendingForfeit(operation.matchId)
}

private val TERMINAL_FORFEIT_CODES =
    setOf(
        "INVALID_MATCH_STATUS",
        "MATCH_NOT_FOUND",
        "INVALID_PLAYER_TOKEN",
        "AUTH_REQUIRED",
        "AUTH_INVALID",
    )

private const val TAG = "LudoPawsForfeit"
private const val ACTIVE_STATUS = "ACTIVE"
private const val FINISHED_STATUS = "FINISHED"
private const val FORFEIT_RECONCILE_DELAY_MS = 900L
private const val FORFEIT_STATE_CHECKS = 3
