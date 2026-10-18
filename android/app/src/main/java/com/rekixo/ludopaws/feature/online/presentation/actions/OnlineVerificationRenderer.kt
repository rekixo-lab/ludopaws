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
) {
    val pending = state.pendingRoll
    val presentation =
        OnlineVerifiedDicePresentationPolicy.resolve(state)

    verificationText.text =
        if (presentation != null) {
            val shouldAnimate =
                uiStateHolder.value.presentedDiceEventKey !=
                    presentation.eventKey
            val minimumRollDurationMillis =
                LudoPawsGameplayPacingPolicy.diceResultHoldMillis(
                    GameSettingsStore(this).snapshot().gameSpeed,
                )
            diceView.showOutcome(
                outcome = presentation.outcome,
                animate = shouldAnimate,
                eventKey = presentation.eventKey,
                minimumRollDurationMillis = minimumRollDurationMillis,
            )
            if (shouldAnimate) {
                uiStateHolder.update {
                    it.copy(
                        presentedDiceEventKey = presentation.eventKey,
                    )
                }
                val remainingPresentationMillis =
                    diceView.remainingOutcomePresentationMillis(minimumRollDurationMillis)
                if (remainingPresentationMillis > 0L) {
                    onlineGameplayActionBlockedUntilMillis = maxOf(
                        onlineGameplayActionBlockedUntilMillis,
                        SystemClock.uptimeMillis() + remainingPresentationMillis,
                    )
                    scheduleOnlineGameplayUnlock()
                }
            }

            buildString {
                append("✓ VERIFIED • Dice ")
                append(presentation.outcome)
                if (presentation.openingRollApplied) {
                    append(" • opening bonus")
                }
                presentation.eventIndex?.let { eventIndex ->
                    append("\nEvent ")
                    append(eventIndex)
                }
                append("  •  Proof ")
                append(shortDigest(presentation.proofDigest))
            }
        } else {
            if (pending != null) {
                diceView.startRolling()
            } else {
                diceView.stopRolling()
            }

            when (pending?.status) {
                "CREATING" -> "WAITING • Settling a previous turn…"
                "COMMITTED" -> "WAITING • Settling a previous turn…"
                "RESOLVING" -> "WAITING • Settling a previous turn…"
                "RESOLVED" ->
                    "VERIFYING • Waiting for the complete proof before showing a verified result."
                else -> "No verified roll yet."
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

    // Keep the live board in a fixed center stage for remote Online / Team Up matches.
    // The parent ScrollView remains scrollable in the lobby, but the active game uses
    // the full viewport and gives the board the flexible space between player rails.
    val gameplayContent = boardFrame.parent as? LinearLayout
    val gameplayContentHost = gameplayContent?.parent as? FrameLayout
    val gameplayScroll = gameplayContentHost?.parent as? ScrollView
    val viewportHeight =
        if (active) FrameLayout.LayoutParams.MATCH_PARENT
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
            if (active) 0 else LinearLayout.LayoutParams.WRAP_CONTENT
        val targetWeight = if (active) 1f else 0f
        if (params.height != targetHeight || params.weight != targetWeight) {
            params.height = targetHeight
            params.weight = targetWeight
            boardFrame.layoutParams = params
        }
    }

    if (active && gameplayScroll != null && gameplayScroll.scrollY != 0) {
        gameplayScroll.post { gameplayScroll.scrollTo(0, 0) }
    }

    val canEnterAnotherMatch =
        finished && gameMode == GameMode.ONLINE

    lobbyPanel.visibility =
        if (canEnterAnotherMatch) View.VISIBLE else View.GONE

    if (canEnterAnotherMatch) {
        restorePublicMatchmakingUi()
    } else {
        setPublicSearchUi(searching = false)
    }

    nameInput.visibility = if (canEnterAnotherMatch) View.VISIBLE else View.GONE
    matchInput.visibility = if (canEnterAnotherMatch) View.VISIBLE else View.GONE
    createButton.visibility = if (canEnterAnotherMatch) View.VISIBLE else View.GONE
    joinButton.visibility = if (canEnterAnotherMatch) View.VISIBLE else View.GONE

    createButton.isEnabled = canEnterAnotherMatch && authoritativeActions
    joinButton.isEnabled = canEnterAnotherMatch && authoritativeActions
    nameInput.isEnabled = canEnterAnotherMatch && authoritativeActions
    matchInput.isEnabled = canEnterAnotherMatch && authoritativeActions

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
    resultPanel.visibility = if (finished) View.VISIBLE else View.GONE
    boardFrame.visibility = if (active || finished) View.VISIBLE else View.GONE
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

    scheduleSingleLegalOnlineMove(
        state = state,
        myTurn = myTurn,
    )
}

private fun MainActivity.scheduleSingleLegalOnlineMove(
    state: MatchSnapshot,
    myTurn: Boolean,
) {
    val pending =
        state.pendingRoll
            ?.takeIf { it.status == "RESOLVED" }
            ?: return
    if (
        !myTurn ||
        !hasAuthoritativeActionState()
    ) {
        return
    }

    val tokenIndex =
        LudoTurnAutomationPolicy.singleLegalTokenIndex(
            pending.legalTokenIndexes,
        ) ?: return

    val eventKey =
        "single-legal:${state.matchId}:${pending.eventIndex}:$tokenIndex"
    if (rollButton.tag == eventKey) return
    rollButton.tag = eventKey

    showStatus("Only one move is possible • showing the dice result…")
    val speed = GameSettingsStore(this).snapshot().gameSpeed
    val autoMoveDelay =
        maxOf(
            remainingOnlineDicePresentationMillis(),
            (onlineGameplayActionBlockedUntilMillis - SystemClock.uptimeMillis())
                .coerceAtLeast(0L),
        ) + LudoPawsGameplayPacingPolicy.postRollAutoMoveDelayMillis(speed)

    val autoMoveTask = object : Runnable {
        override fun run() {
            if (!canRenderUi() || !screenStarted || rollButton.tag != eventKey) {
                if (rollButton.tag == eventKey) rollButton.tag = null
                return
            }

            val latest = currentState
            val latestPending = latest?.pendingRoll
            val stillSameMove =
                latest != null &&
                    hasAuthoritativeActionState() &&
                    latest.status == "ACTIVE" &&
                    latestPending?.status == "RESOLVED" &&
                    latestPending.eventIndex == pending.eventIndex &&
                    LudoTurnAutomationPolicy.singleLegalTokenIndex(
                        latestPending.legalTokenIndexes,
                    ) == tokenIndex &&
                    (latest.actingSeat ?: latest.turnSeat) ==
                        latest.players.indexOfFirst { player ->
                            player.playerId == playerId
                        }

            if (!stillSameMove) {
                if (rollButton.tag == eventKey) rollButton.tag = null
                return
            }

            val remainingAnimation = remainingOnlineDicePresentationMillis()
            val remainingActionLock =
                (onlineGameplayActionBlockedUntilMillis - SystemClock.uptimeMillis())
                    .coerceAtLeast(0L)
            val remainingDelay = maxOf(remainingAnimation, remainingActionLock)
            if (remainingDelay > 0L) {
                rollButton.postDelayed(this, remainingDelay)
                return
            }

            if (onlineRollRequestInFlight || onlineMoveRequestInFlight) {
                // A slow network request can outlast the dice animation. Keep
                // the unique legal move pending until the action lane is free.
                rollButton.postDelayed(this, 120L)
                return
            }
            moveToken(tokenIndex)
        }
    }
    rollButton.postDelayed(autoMoveTask, autoMoveDelay)
}

internal fun MainActivity.updateRollButton() {
    val state = currentState
    if (state == null) {
        val secret = pendingSecret
        rollButton.text =
            if (
                secret != null &&
                secret.matchId == matchId
            ) {
                "RESUME VERIFIED ROLL"
            } else {
                "ROLL VERIFIED DICE"
            }
        // A restored local secret can explain what will resume, but it cannot
        // authorize a mutation. The button becomes actionable only after a
        // fresh authoritative snapshot passes OnlineRollActionPolicy.
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
    val latest = state.history.takeLast(8).reversed()

    if (latest.isEmpty()) {
        return "No proof history yet."
    }

    return buildString {
        append("Recent verified events\n")
        latest.forEach { event ->
            append("#")
            append(event.eventIndex)
            append("  dice=")
            append(event.effectiveOutcome ?: event.outcome ?: "?")
            if (event.openingRollApplied) {
                append("(opening)")
            }
            append("  proof=")
            append(shortDigest(event.proofDigest))
            append("  round=")
            append(shortDigest(event.roundId))
            if (event.moveTokenIndex != null) {
                append("  token=")
                append(event.moveTokenIndex + 1)
            }
            if (event.captures > 0) {
                append("  captures=")
                append(event.captures)
            }
            append("\n")
        }
    }.trimEnd()
}

internal fun MainActivity.toggleProofDetails() {
    ArcadeDialogs.showProofHistory(
        this,
        "VERIFIED HISTORY",
        proofDetailsText.text.toString().ifBlank {
            "No proof history yet."
        },
    )
}
