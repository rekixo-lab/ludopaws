package com.rekixo.ludopaws.feature.online

import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.ui.online.*
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore

internal fun MainActivity.updateVerification(
    state: MatchSnapshot,
    previousState: MatchSnapshot? = null,
) {
    val pending = state.pendingRoll
    val presentation =
        OnlineVerifiedDicePresentationPolicy.resolve(state)

    verificationText.text =
        if (presentation != null) {
            val activeSeat = state.actingSeat ?: state.turnSeat
            val rollerId = if (pending != null) {
                state.players.getOrNull(pending.seat)?.playerId
            } else {
                state.history.lastOrNull()?.playerId
            }
            val activePlayerId = state.players.getOrNull(activeSeat)?.playerId
            val rollView = rollerId?.let { OnlineLudoPawsPresentation.diceForPlayer(this, it) }
                ?: diceView.takeIf { rollerId == activePlayerId }
            val unseenEvent = uiStateHolder.value.presentedDiceEventKey != presentation.eventKey
            val shouldAnimate = unseenEvent && state.status == "ACTIVE" &&
                (rollerId == activePlayerId || previousState?.matchId == state.matchId)
            val minimumRollDurationMillis =
                LudoPawsGameplayPacingPolicy.diceResultHoldMillis(
                    GameSettingsStore(this).snapshot().gameSpeed,
                )
            rollView?.showOutcome(
                outcome = presentation.outcome,
                animate = shouldAnimate,
                eventKey = presentation.eventKey,
                minimumRollDurationMillis = minimumRollDurationMillis,
            )
            if (unseenEvent) {
                uiStateHolder.update {
                    it.copy(presentedDiceEventKey = presentation.eventKey)
                }
            }
            if (shouldAnimate && rollView != null && rollerId != null) {
                val remaining = rollView.remainingOutcomePresentationMillis(minimumRollDurationMillis)
                if (remaining > 0L) {
                    val until = SystemClock.uptimeMillis() + remaining
                    onlineGameplayActionBlockedUntilMillis = maxOf(onlineGameplayActionBlockedUntilMillis, until)
                    OnlineLudoPawsPresentation.holdOutgoingDice(this, state, rollerId, until)
                    scheduleOnlineGameplayUnlock()
                }
            }

            buildString {
                append("Dice ")
                append(presentation.outcome)
                if (presentation.openingRollApplied) {
                    append(" • opening bonus")
                }
                val comeback = state.pendingRoll?.comebackBonusApplied
                    ?: state.history.lastOrNull()?.comebackBonusApplied ?: false
                if (comeback) append(" • comeback bonus: one paw returns, no extra turn")
                LudoComebackPolicy.hint(state)?.let { append("\n"); append(it) }
                presentation.eventIndex?.let { eventIndex ->
                    append("\nEvent ")
                    append(eventIndex)
                }
            }
        } else {
            if (pending != null) {
                diceView.startRolling()
            } else {
                diceView.stopRolling()
            }

            when (pending?.status) {
                "RESOLVED" -> "Syncing the dice result…"
                else -> LudoComebackPolicy.hint(state) ?: "Ready to roll."
            }
        }
}

/**
 * Cached snapshots are display-only. Losing connectivity also invalidates the
 * in-memory action authority so a reconnect must obtain a fresh server snapshot
 * before any mutation can be submitted.
 */
internal fun MainActivity.hasAuthoritativeActionState(): Boolean {
    if (!isOnline) {
        if (uiStateHolder.value.currentStateSource != null) {
            uiStateHolder.update {
                it.copy(
                    currentStateSource = null,
                )
            }
        }
        return false
    }
    return uiStateHolder.value.currentStateSource ==
        OnlineStateSource.AUTHORITATIVE
}

internal fun MainActivity.onlineRollActionDecision(
    state: MatchSnapshot,
): OnlineRollActionDecision {
    val mySeat =
        state.players.indexOfFirst {
            it.playerId == playerId
        }
    val actionableSeat = state.actingSeat ?: state.turnSeat
    val myTurn =
        state.status == "ACTIVE" &&
            mySeat >= 0 &&
            actionableSeat == mySeat

    return OnlineRollActionPolicy.resolve(
        isOnline = isOnline,
        hasAuthoritativeState = hasAuthoritativeActionState(),
        matchStatus = state.status,
        myTurn = myTurn,
        pendingStatus = state.pendingRoll?.status,
    )
}

internal fun MainActivity.updateControls(
    state: MatchSnapshot,
) {
    val mySeat =
        state.players.indexOfFirst {
            it.playerId == playerId
        }
    val actionableSeat = state.actingSeat ?: state.turnSeat
    val myTurn =
        state.status == "ACTIVE" &&
            mySeat >= 0 &&
            actionableSeat == mySeat
    val authoritativeActions =
        hasAuthoritativeActionState()

    val waiting = state.status == "WAITING"
    val active = state.status == "ACTIVE"
    onlineHeroPanel?.visibility = if (waiting) View.VISIBLE else View.GONE
    val finished = state.status == "FINISHED"

    val showMatchStage = active || finished

    // Keep the same board stage through the final move and result.
    // The parent ScrollView remains scrollable in the lobby, but the active game uses
    // the full viewport and gives the board the flexible space between player rails.
    val gameplayContent = boardFrame.parent as? LinearLayout
    val gameplayContentHost = gameplayContent?.parent as? FrameLayout
    val gameplayScroll = gameplayContentHost?.parent as? ScrollView
    val viewportHeight =
        if (showMatchStage) FrameLayout.LayoutParams.MATCH_PARENT
        else FrameLayout.LayoutParams.WRAP_CONTENT

    gameplayContentHost?.let { host ->
        val params = host.layoutParams
        if (params.height != viewportHeight) {
            params.height = viewportHeight
            host.layoutParams = params
        }
    }

    gameplayContent?.let { content ->
        val params = content.layoutParams as? FrameLayout.LayoutParams
        if (params != null && params.height != viewportHeight) {
            params.height = viewportHeight
            content.layoutParams = params
        }
    }

    (boardFrame.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
        val targetHeight =
            if (showMatchStage) 0 else LinearLayout.LayoutParams.WRAP_CONTENT
        val targetWeight = if (showMatchStage) 1f else 0f
        if (params.height != targetHeight || params.weight != targetWeight) {
            params.height = targetHeight
            params.weight = targetWeight
            boardFrame.layoutParams = params
        }
    }

    if (showMatchStage && gameplayScroll != null && gameplayScroll.scrollY != 0) {
        gameplayScroll.post { gameplayScroll.scrollTo(0, 0) }
    }

    lobbyPanel.visibility = View.GONE
    setPublicSearchUi(searching = false)
    nameInput.visibility = View.GONE
    matchInput.visibility = View.GONE
    createButton.visibility = View.GONE
    joinButton.visibility = View.GONE
    nameInput.isEnabled = false
    matchInput.isEnabled = false
    createButton.isEnabled = false
    joinButton.isEnabled = false

    matchStatusPanel.visibility = View.VISIBLE
    if (waiting) {
        matchStatusPanel.background =
            LudoProofTheme.hudPanelDrawable(this, goldBorder = false)
        matchStatusPanel.elevation = dp(3).toFloat()
        matchStatusPanel.setPadding(
            dp(if (isCompactOnline()) 8 else 10),
            dp(if (isCompactOnline()) 5 else 7),
            dp(if (isCompactOnline()) 8 else 10),
            dp(if (isCompactOnline()) 5 else 7),
        )
    } else {
        matchStatusPanel.background = null
        matchStatusPanel.elevation = 0f
        matchStatusPanel.setPadding(0, 0, 0, 0)
    }
    turnText.visibility = if (waiting) View.VISIBLE else View.GONE
    val finishingPresentation = SystemClock.uptimeMillis() < onlineGameplayActionBlockedUntilMillis
    resultPanel.visibility = if (finished && !finishingPresentation) View.VISIBLE else View.GONE
    if (finished && !finishingPresentation) com.rekixo.ludopaws.feature.store.presentation.PromotionInterest.showWhenFinished(this)
    boardFrame.visibility = if (showMatchStage) View.VISIBLE else View.GONE
    statusText.minLines = if (showMatchStage) 2 else 0
    gameplayPromptText.visibility = when {
        active -> View.VISIBLE
        finished -> View.INVISIBLE
        else -> View.GONE
    }
    refreshGameplayPrompt()
    actionPanel.visibility = View.VISIBLE
    if (waiting) {
        actionPanel.background =
            LudoProofTheme.hudPanelDrawable(this, goldBorder = false)
        actionPanel.setPadding(dp(8), dp(6), dp(8), dp(6))
    } else {
        actionPanel.background = null
        actionPanel.setPadding(0, 0, 0, 0)
    }

    // During actual play the character rails carry player identity/progress.
    // Hide duplicate protocol/match copy so the Ludo board stays visually primary.
    matchInfoText.visibility = if (waiting) View.VISIBLE else View.GONE
    playersText.visibility = if (waiting) View.VISIBLE else View.GONE

    verificationPanel.visibility = View.GONE

    diceView.visibility = View.VISIBLE
    diceView.alpha = 1f
    verificationText.visibility = View.VISIBLE

    startButton.visibility = if (waiting) View.VISIBLE else View.GONE
    val requiredPlayers = state.targetPlayerCount ?: 2
    startButton.isEnabled =
        authoritativeActions &&
            waiting &&
            state.players.size >= requiredPlayers &&
            state.hostPlayerId == playerId

    // Realtime keeps active matches fresh. Keep manual sync visible only in the
    // waiting room, while its enabled state still mirrors network availability
    // for the remote-action safety contract and instrumentation checks.
    refreshButton.visibility = if (waiting) View.VISIBLE else View.GONE
    refreshButton.isEnabled = isOnline && !finished

    shareButton.visibility =
        if (
            waiting &&
            state.matchMode != "TEAM_UP"
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    shareButton.isEnabled =
        waiting &&
            state.matchMode != "TEAM_UP" &&
            state.matchId.isNotBlank()

    proofButton.visibility = View.GONE

    val rollAction = onlineRollActionDecision(state)
    // Tap the dice beside the active player's character to roll.
    rollButton.visibility = View.GONE
    rollButton.isEnabled = rollAction.enabled
    rollButton.text = rollAction.label

}

internal fun MainActivity.refreshGameplayPrompt() {
    mainHandler.removeCallbacks(gameplayPromptTick)
    val state = currentState ?: return
    if (!screenStarted || state.status != "ACTIVE") return
    val player = state.players.firstOrNull { it.playerId == playerId }
    val activePlayer = state.players.getOrNull(state.actingSeat ?: state.turnSeat)
    val awaitingMove = state.pendingRoll?.status == "RESOLVED"
    val now = SystemClock.elapsedRealtime()
    val serverNow = state.serverTimeAtSnapshot
    val deadline = (if (awaitingMove) state.moveDeadlineAt else state.turnDeadlineAt)
        .takeIf { serverNow != null }
    if (gameplayDeadlineEpoch != deadline) {
        gameplayDeadlineEpoch = deadline
        gameplayDeadlineElapsed = if (deadline != null && serverNow != null) {
            now + (deadline - serverNow).coerceAtLeast(0L)
        } else 0L
    }
    val remaining = (gameplayDeadlineElapsed - now).coerceAtLeast(0L)
    val syncNeeded = isOnline && !hasAuthoritativeActionState()
    val canSync = isOnline && playerToken != null && (syncNeeded || !realtimeConnected)
    val leftMatch = player?.forfeited == true
    val timeout = matchTimeoutMessage?.takeIf { now < matchTimeoutMessageUntil }
    val action = when {
        leftMatch -> "You were removed from this match · Tap for Home"
        !isOnline -> "Connection lost · Match timer continues"
        syncNeeded && canSync -> "Syncing match · Tap to retry"
        syncNeeded -> "Waiting for match sync"
        state.pendingRoll != null && !awaitingMove -> "Checking the dice…"
        activePlayer?.playerId != playerId -> "${activePlayer?.displayName ?: "Player"}’s turn"
        awaitingMove && state.actingSeat != null && state.actingSeat != state.turnSeat ->
            "Partner handoff · Choose a paw"
        awaitingMove -> "Your turn · Choose a paw"
        state.actingSeat != null && state.actingSeat != state.turnSeat -> "Partner handoff · Roll the dice"
        else -> "Your turn · Roll the dice"
    }
    val text = buildString {
        append(action)
        if (!leftMatch && isOnline && !syncNeeded && deadline != null) {
            append(" · ").append((remaining + 999L) / 1_000L).append("s")
        }
        if (!leftMatch && timeout != null) appendLine().append(timeout)
        else if (!leftMatch && canSync && !syncNeeded) appendLine().append("Reconnecting live updates · Tap to sync")
    }
    if (gameplayPromptText.text.toString() != text) gameplayPromptText.text = text
    gameplayPromptText.setOnClickListener(when {
        leftMatch -> View.OnClickListener { requestRemoteExitAfterWinInterstitial() }
        canSync -> View.OnClickListener { refreshState(silent = false) }
        else -> null
    })
    gameplayPromptText.isClickable = leftMatch || canSync
    gameplayPromptText.isFocusable = leftMatch || canSync
    if (!leftMatch && (remaining > 0L || timeout != null)) {
        mainHandler.postDelayed(gameplayPromptTick, 1_000L)
    }
}

internal fun MainActivity.updateRollButton() {
    val state = currentState
    if (state == null) {
        rollButton.text = "ROLL DICE"
        // An authoritative snapshot is required after restoring a session.
        rollButton.isEnabled = false
        return
    }

    val action = onlineRollActionDecision(state)
    rollButton.text = action.label
    rollButton.isEnabled = action.enabled
}

internal fun MainActivity.proofDetails(
    state: MatchSnapshot,
): String {
    val latest = state.history.lastOrNull() ?: return "No roll yet."
    return buildString {
        append("Last roll: ")
        append(latest.effectiveOutcome ?: latest.outcome ?: "?")
        latest.moveTokenIndex?.let { append(" • token ${it + 1}") }
        if (latest.captures > 0) append(" • captures ${latest.captures}")
        append("\nServer-authoritative result")
    }
}

internal fun MainActivity.toggleProofDetails() {
    ArcadeDialogs.showProofHistory(
        this,
        "LAST ROLL",
        proofDetailsText.text.toString().ifBlank {
            "No roll yet."
        },
    )
}
