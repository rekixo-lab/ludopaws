package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.online.*
import com.rekixo.ludopaws.ui.online.*

/**
 * Realtime transport callback composition for Online/Friends/Team Up.
 * The Activity owns all UI lifecycle checks; callbacks never bypass it.
 * Poll intervals are injected from MainActivity to keep one configuration source.
 */
internal fun MainActivity.createRealtimeClient(
    statePollLiveMs: Long,
    stateReconcileDelayMs: Long,
): MatchRealtimeClient {
    return MatchRealtimeClient(
            onSnapshot = { response ->
                mainHandler.post {
                    val incomingMatchId = response.optJSONObject("state")?.optString("matchId")
                    if (canRenderUi() && screenStarted && incomingMatchId == matchId) {
                        mainHandler.removeCallbacks(realtimeRefreshRunnable)
                        applyResponse(response, announce = false, silentValidationFailure = false)
                    }
                }
            },
            onRevision = {
                    revision,
                    _,
                ->
                mainHandler.post {
                    if (
                        !canRenderUi()
                    ) {
                        return@post
                    }

                    if (revision > lastRealtimeRevision) {
                        // Roll HTTP responses apply authoritative snapshots directly.
                        // Avoid another GET if this socket revision was already applied.
                        if (revision.toLong() <= lastAppliedAuthoritativeRevision) {
                            lastRealtimeRevision = maxOf(lastRealtimeRevision, revision)
                        } else {
                            lastRealtimeRevision = revision
                            mainHandler.removeCallbacks(realtimeRefreshRunnable)
                            mainHandler.postDelayed(realtimeRefreshRunnable, 40L)
                        }
                    }
                }
            },
            onConnectionChanged = {
                    connected ->
                mainHandler.post {
                    if (
                        !canRenderUi()
                    ) {
                        return@post
                    }
                    realtimeConnected =
                        connected
                    updateConnectionLabel()
                    if (screenStarted && playerToken != null) {
                        mainHandler.removeCallbacks(statePollRunnable)
                        if (connected) {
                            refreshState(silent = true)
                            mainHandler.postDelayed(
                                statePollRunnable,
                                statePollLiveMs,
                            )
                        } else {
                            mainHandler.postDelayed(
                                statePollRunnable,
                                stateReconcileDelayMs,
                            )
                        }
                    }
                }
            },
            onQuickChat = { senderPlayerId, displayName, emoji ->
                mainHandler.post {
                    if (!canRenderUi()) return@post
                    OnlineLudoPawsPresentation.presentRemoteQuickChat(
                        activity = this,
                        senderPlayerId = senderPlayerId,
                        displayName = displayName,
                        emoji = emoji,
                    )
                }
            },
            onUnavailable = { message ->
                mainHandler.post {
                    if (!canRenderUi()) return@post
                    remoteSessionUnavailable = true
                    mainHandler.removeCallbacks(statePollRunnable)
                    mainHandler.removeCallbacks(realtimeReconnectRunnable)
                    showStatus(message)
                }
            },
        )
}
