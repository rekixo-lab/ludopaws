package com.rekixo.ludopaws.feature.online

import android.view.View
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileGameMode
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileMatchSource
import org.json.JSONObject

internal fun MainActivity.applyResponse(
    response: JSONObject,
    announce: Boolean = true,
    silentValidationFailure: Boolean = false,
) {
    val source =
        if (
            response.optBoolean(
                CachedMatchStore.CACHE_SOURCE_MARKER,
                false,
            )
        ) {
            OnlineStateSource.CACHE
        } else {
            OnlineStateSource.AUTHORITATIVE
        }

    val envelope =
        try {
            GameJson.preparedEnvelope(response)
        } catch (error: GameSchemaException) {
            if (silentValidationFailure) return
            diceView.stopRolling()
            currentState?.let { safeState ->
                updateControls(safeState)
            }
            // Refresh action controls from the last known-good snapshot first,
            // then leave the validation warning visible as the final UI status.
            showStatus(
                "Server state validation failed. Your last verified state was kept; refresh to retry.",
            )
            updateRollButton()
            return
        }

    val state = envelope.state
    if (state == null) {
        showStatus(response.toString(2))
        updateRollButton()
        return
    }

    if (
        source == OnlineStateSource.CACHE &&
        !OnlineCachedStateRestorePolicy.shouldApply(
            sessionMatchId = matchId,
            cachedMatchId = state.matchId,
        )
    ) {
        cachedMatchStore.clear()
        updateRollButton()
        return
    }

    val serverRevision =
        response.optJSONObject("state")
            ?.optLong("revision", -1L)
            ?: response.optLong("revision", -1L)
    if (
        !OnlineSnapshotFreshnessPolicy.shouldApply(
            sessionMatchId = matchId,
            incomingMatchId = state.matchId,
            incomingSource = source,
            currentStateSource = uiStateHolder.value.currentStateSource,
            currentStateMatchId = currentState?.matchId,
            incomingRevision = serverRevision,
            lastAppliedAuthoritativeRevision = lastAppliedAuthoritativeRevision,
        )
    ) {
        // Skip all side effects for stale responses: no state replacement,
        // cache write, pending-secret reconciliation, or animation replay.
        return
    }

    envelope.playerId?.let { resolvedPlayerId ->
        playerId = resolvedPlayerId
        val code = matchId
        val token = playerToken
        if (code != null && token != null) {
            persistSessionSecurely(
                code = code,
                id = resolvedPlayerId,
                token = token,
            )
        }
    }

    val previousState = currentState
    currentState = state
    applyOnlineGameplayPacing(previousState, state)
    LudoProofTheme.setGameplayBackground(
        root = arcadeRootView,
        enabled = state.status == "ACTIVE" || state.status == "FINISHED",
    )
    uiStateHolder.update {
        it.copy(
            currentStateSource = source,
        )
    }
    matchId = state.matchId

    if (source == OnlineStateSource.AUTHORITATIVE && serverRevision >= 0L) {
        lastAppliedAuthoritativeRevision = maxOf(
            lastAppliedAuthoritativeRevision,
            serverRevision,
        )
    }
    if (serverRevision > lastRealtimeRevision.toLong()) {
        lastRealtimeRevision = serverRevision.toInt()
    }

    if (state.status == "FINISHED") {
        stopFriendsVoice("Voice ended with the match.")
        realtimeClient.disconnect()
        realtimeConnected = false
        updateConnectionLabel()
    }

    if (
        source == OnlineStateSource.AUTHORITATIVE &&
        state.status == "FINISHED" &&
        gameMode.ranked
    ) {
        runCatching {
            ProfileStore(this).recordCompletedMatch(
                matchId = state.matchId,
                mode = ProfileGameMode.CLASSIC,
                source = ProfileMatchSource.ONLINE,
                won = state.winnerPlayerId == playerId,
            )
        }
    }

    if (source == OnlineStateSource.AUTHORITATIVE) {
        response.optJSONObject("state")?.let { safeState ->
            cachedMatchStore.save(
                envelope.playerId ?: playerId,
                safeState,
            )
        }
    }

    if (boardView.visibility == View.VISIBLE || onlinePresentationRendererOverrideForTests != null) {
        boardView.bind(state, playerId)
    }
    val presentationOverride = onlinePresentationRendererOverrideForTests
    if (presentationOverride != null) {
        presentationOverride(previousState, state)
    } else {
        OnlineLudoPawsPresentation.render(
            activity = this,
            previous = previousState,
            current = state,
        )
    }

    matchInfoText.text =
        buildString {
            append("MATCH • ")
            append(state.matchId)
            append("   •   ")
            append(state.status)
            if (state.matchMode == "TEAM_UP") {
                append("   •   2 VS 2")
            }
            if (state.rulesetId.isNotBlank()) {
                append("   •   ")
                append(state.rulesetId)
            }
        }

    playersText.text =
        state.players.joinToString(separator = "\n") { player ->
            val marker = if (player.playerId == playerId) "  •  YOU" else ""
            val team =
                if (state.matchMode == "TEAM_UP" && player.teamId != null) {
                    "  •  TEAM ${player.teamId}"
                } else {
                    ""
                }
            player.color + "  •  " + player.displayName + team + marker
        }

    updateTurnBanner(state)
    updateVerification(state)
    proofDetailsText.text = proofDetails(state)
    updateControls(state)
    updateRollButton()

    if (secureSessionPersistenceFailed) {
        showStatus(
            "Match is connected, but secure resume could not be saved. Keep this screen open until the match is finished.",
        )
    } else if (state.status == "WAITING" && announce) {
        showStatus("Room synced • waiting for all required players.")
    } else if (state.status == "ACTIVE" || state.status == "FINISHED") {
        // Normal gameplay transitions are presented by the board and player rails,
        // not as a scrolling activity-style status message.
        statusText.text = ""
    }

    val wireState = response.optJSONObject("state") ?: response
    val timeoutNotice = wireState.optJSONObject("turnTimeoutNotice")
    val timeoutPlayerId = timeoutNotice?.optString("playerId").orEmpty()
    val timeoutCount = timeoutNotice?.optInt("count", 0) ?: 0
    val timeoutAt = timeoutNotice?.optLong("at", 0L) ?: 0L
    if (
        source == OnlineStateSource.AUTHORITATIVE &&
        timeoutPlayerId.isNotBlank() &&
        timeoutCount > 0 &&
        timeoutAt > lastTurnTimeoutNoticeAt
    ) {
        lastTurnTimeoutNoticeAt = timeoutAt
        // Keep timeout history in the authoritative state, but do not render it
        // as repetitive activity-log text during gameplay.
    }

    val wirePlayers = wireState.optJSONArray("players")
    val localPlayerWasForfeited = wirePlayers != null && (0 until wirePlayers.length()).any { index ->
        val wirePlayer = wirePlayers.optJSONObject(index)
        wirePlayer != null &&
            wirePlayer.optString("playerId") == playerId &&
            wirePlayer.optBoolean("forfeited", false)
    }
    if (
        source == OnlineStateSource.AUTHORITATIVE &&
        localPlayerWasForfeited &&
        !timeoutExitScheduled
    ) {
        timeoutExitScheduled = true
        mainHandler.postDelayed(
            {
                if (canRenderUi()) {
                    secureSessionStore.clear()
                    cachedMatchStore.clear()
                    publicMatchmakingStore.clear()
                    matchId = null
                    playerToken = null
                    playerId = null
                    currentState = null
                    realtimeConnected = false
                    realtimeClient.disconnect()
                    LudoProofTheme.setGameplayBackground(root = arcadeRootView, enabled = false)
                    finish()
                }
            },
            1_800L,
        )
    }
    scheduleOnlineAutoMove(state)
}

internal fun MainActivity.updateTurnBanner(
    state: MatchSnapshot,
) {
    when (state.status) {
        "WAITING" -> {
            turnText.text =
                if (
                    state.targetPlayerCount != null &&
                    state.players.size < state.targetPlayerCount
                ) {
                    "WAITING FOR PLAYERS • ${state.players.size}/${state.targetPlayerCount}"
                } else if (state.players.size < 2) {
                    "WAITING FOR PLAYER"
                } else if (state.hostPlayerId == playerId) {
                    "READY • START THE MATCH"
                } else {
                    "READY • WAITING FOR HOST"
                }
        }

        "FINISHED" -> {
            turnText.text = "MATCH COMPLETE"
            if (state.matchMode == "TEAM_UP") {
                val me = state.players.find { it.playerId == playerId }
                val winnerTeam = state.winnerTeamId
                val won = winnerTeam != null && me?.teamId == winnerTeam
                val winners =
                    state.players
                        .filter { it.teamId == winnerTeam }
                        .joinToString(" + ") { it.displayName }
                        .ifBlank { "Team ${winnerTeam ?: "—"}" }

                resultTitleText.text =
                    if (won) "YOUR TEAM WON" else "TEAM ${winnerTeam ?: "—"} WINS"
                resultSubtitleText.text =
                    "$winners • server-authoritative team result • match complete"
            } else {
                val rank = playerId?.let { LudoPawsFinishPresentation.ranks(state)[it] }
                val standings = LudoPawsFinishPresentation.standings(state)
                resultTitleText.text = LudoPawsFinishPresentation.headline(rank)
                resultSubtitleText.text = buildString {
                    if (rank == 4) append("WELL PLAYED!\n")
                    append(standings.ifBlank { "Match complete • verified result" })
                }
            }
        }

        else -> {
            val activeSeat = state.actingSeat ?: state.turnSeat
            val active = state.players.getOrNull(activeSeat)
            val mine = active?.playerId == playerId
            val handoff =
                state.matchMode == "TEAM_UP" &&
                    state.actingSeat != null &&
                    state.actingSeat != state.turnSeat
            turnText.text =
                if (mine) {
                    if (handoff) "YOUR TEAM TURN • PARTNER HANDOFF"
                    else "YOUR TURN • ROLL OR MOVE"
                } else {
                    val prefix = if (handoff) "TEAM HANDOFF • " else "WAITING • "
                    prefix + (active?.displayName ?: "Player") + "'S TURN"
                }
        }
    }
}
