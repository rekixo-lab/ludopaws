package com.rekixo.ludopaws.ui.offline.gameplay

import android.graphics.Color
import android.graphics.Typeface
import android.os.SystemClock
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.feature.offline.*
import com.rekixo.ludopaws.feature.offline.presentation.feedback.OfflineFeedbackAction
import com.rekixo.ludopaws.feature.offline.presentation.feedback.OfflineLudoPawsFeedbackDispatcher
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.core.ui.icons.AppIconName
import com.rekixo.ludopaws.core.ui.icons.AppIconView
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.feature.settings.data.local.GameSoundFeedback
import com.rekixo.ludopaws.ui.offline.common.*
import com.rekixo.ludopaws.ui.offline.setup.*
import com.rekixo.ludopaws.ui.offline.state.resolveOfflineCharacterIds

internal fun OfflineGameActivity.gameplayActionPanel(): LinearLayout =
    LinearLayout(this).apply {
        // Quick Chat now lives directly under each player's profile card.
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(4), 0, dp(4), 0)
    }

private const val OFFLINE_RESULT_ACTIONS_TAG =
    "ludo_paws_offline_result_actions"

private fun OfflineGameActivity.ensureOfflineResultActions(
    state: MatchSnapshot,
) {
    val parent =
        resultPanel.parent as? android.widget.FrameLayout
            ?: return
    val existing =
        parent.findViewWithTag<View>(
            OFFLINE_RESULT_ACTIONS_TAG,
        )

    if (state.status != "FINISHED" || SystemClock.uptimeMillis() < gameplayActionBlockedUntilMillis) {
        existing?.visibility = View.GONE
        return
    }

    if (existing != null) {
        existing.visibility = View.VISIBLE
        return
    }

    val actions =
        LinearLayout(this).apply {
            tag = OFFLINE_RESULT_ACTIONS_TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
        }

    actions.addView(
        offlineResultActionButton(
            label = "REMATCH",
            description = "Start a rematch",
            iconName = AppIconName.REFRESH,
            tone = OfflineResultActionTone.POSITIVE,
        ) {
            startOfflineRematch()
        },
        LinearLayout.LayoutParams(
            0,
            dp(if (isCompactSetup()) 48 else 52),
            1f,
        ).apply {
            setMargins(dp(4), 0, dp(4), 0)
        },
    )

    actions.addView(
        offlineResultActionButton(
            label = "EXIT",
            description = "Exit the finished match",
            iconName = AppIconName.CLOSE,
            tone = OfflineResultActionTone.DANGER,
        ) {
            requestOfflineExit()
        },
        LinearLayout.LayoutParams(
            0,
            dp(if (isCompactSetup()) 48 else 52),
            1f,
        ).apply {
            setMargins(dp(4), 0, dp(4), 0)
        },
    )

    val maxActionWidth =
        dp(if (isCompactSetup()) 340 else 460)
    val availableWidth =
        (parent.width - dp(24))
            .coerceAtLeast(dp(220))
    val actionWidth =
        minOf(
            maxActionWidth,
            availableWidth,
        )

    parent.addView(
        actions,
        android.widget.FrameLayout.LayoutParams(
            actionWidth,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply {
            bottomMargin =
                dp(if (isCompactSetup()) 72 else 84)
        },
    )
}

private enum class OfflineResultActionTone {
    POSITIVE,
    DANGER,
}

private fun OfflineGameActivity.offlineResultActionButton(
    label: String,
    description: String,
    iconName: AppIconName,
    tone: OfflineResultActionTone,
    action: () -> Unit,
): FrameLayout =
    FrameLayout(this).apply {
        isClickable = true
        isFocusable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = description
        minimumHeight = dp(48)
        stateListAnimator = null
        elevation = dp(3).toFloat()
        background = offlineResultActionBackground(tone)
        setOnClickListener {
            GameSoundFeedback.click(this@offlineResultActionButton)
            action()
        }

        val row = LinearLayout(this@offlineResultActionButton).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = false
            isFocusable = false
            setPadding(
                dp(if (isCompactSetup()) 8 else 12),
                0,
                dp(if (isCompactSetup()) 8 else 12),
                0,
            )
            addView(
                AppIconView(
                    context = this@offlineResultActionButton,
                    iconName = iconName,
                    iconTint = Color.WHITE,
                    strokeWidthDp = 2.5f,
                ),
                LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                    marginEnd = dp(7)
                },
            )
            addView(
                TextView(this@offlineResultActionButton).apply {
                    text = label
                    textSize = if (isCompactSetup()) 11.5f else 13f
                    setTextColor(Color.WHITE)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    includeFontPadding = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
            )
        }
        addView(
            row,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )
        AppFocusRing.apply(this, cornerRadiusDp = 14f, strokeWidthDp = 2.5f)
    }

private fun OfflineGameActivity.offlineResultActionBackground(
    tone: OfflineResultActionTone,
): StateListDrawable {
    fun surface(fill: Int, border: Int, strokeWidthDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(strokeWidthDp), border)
        }

    val normalFill: Int
    val normalBorder: Int
    val pressedFill: Int
    when (tone) {
        OfflineResultActionTone.POSITIVE -> {
            normalFill = 0xE31B3D35.toInt()
            normalBorder = 0xFF67D7A6.toInt()
            pressedFill = 0xFF294E43.toInt()
        }
        OfflineResultActionTone.DANGER -> {
            normalFill = 0xE33B211F.toInt()
            normalBorder = 0xFFFF806D.toInt()
            pressedFill = 0xFF5B2B25.toInt()
        }
    }
    return StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_pressed),
            surface(pressedFill, 0xFFFFD45E.toInt(), 2),
        )
        addState(
            intArrayOf(android.R.attr.state_focused),
            surface(normalFill, 0xFFFFD45E.toInt(), 3),
        )
        addState(
            intArrayOf(),
            surface(normalFill, normalBorder, 1),
        )
    }
}

private fun OfflineGameActivity.startOfflineRematch() {
    val finished =
        session.snapshot()
            ?: return
    val spec =
        OfflineRematchPolicy
            .fromFinishedState(finished)
            ?: run {
                showStatus("Rematch is available after the match ends.")
                return
            }
    val characterIds =
        resolveOfflineCharacterIds(finished)

    selectedPlayers = spec.playerCount
    selectedColor = spec.preferredColor
    selectedCharacterSlot =
        if (isComputerMode) {
            0
        } else {
            selectedCharacterSlot.coerceIn(
                0,
                spec.playerCount - 1,
            )
        }
    selectedCharacterIds = characterIds
    selectedColorsBySeat = finished.players.map { it.color }
    if (!isComputerMode) selectedColor = selectedColorsBySeat[selectedCharacterSlot]
    persistActiveCharacterSetup()

    showGame(
        session.start(
            playerCount = spec.playerCount,
            preferredColor = spec.preferredColor,
            colorsBySeat = if (isComputerMode) null else selectedColorsBySeat,
        ),
    )
}

internal fun OfflineGameActivity.gameplaySectionParams(
    topMarginDp: Int,
) =
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply {
        topMargin = dp(topMarginDp)
    }

internal fun OfflineGameActivity.renderGame(
    state: MatchSnapshot,
    presentationDelayMillis: Long = 0L,
) {
    if (presentationDelayMillis > 0L) {
        gameplayActionBlockedUntilMillis =
            maxOf(
                gameplayActionBlockedUntilMillis,
                SystemClock.uptimeMillis() + presentationDelayMillis,
            )
    }
    val blockedRemainingMillis =
        (gameplayActionBlockedUntilMillis - SystemClock.uptimeMillis())
            .coerceAtLeast(0L)

    val active = state.players.getOrNull(state.turnSeat)
    if (state.status == "FINISHED") {
        resultPanel.visibility = if (blockedRemainingMillis > 0L) View.GONE else View.VISIBLE
        // Phase 11 compatibility marker retained for the integration gate:
        // You brought all 4 paws home.
        val humanRank = engine.humanPlayerId()?.let { LudoPawsFinishPresentation.ranks(state)[it] }
        val title = if (isComputerMode) LudoPawsFinishPresentation.headline(humanRank) else "MATCH COMPLETE"
        val standings = LudoPawsFinishPresentation.standings(state)
        val detail = if (isComputerMode && humanRank == 4)
            "WELL PLAYED!\n$standings" else standings
        resultTitleText.text = android.text.SpannableStringBuilder(title).apply {
            if (detail.isNotBlank()) {
                append("\n\n")
                val start = length
                append(detail)
                setSpan(android.text.style.RelativeSizeSpan(.55f), start, length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(android.text.style.ForegroundColorSpan(0xFFFFF1D8.toInt()), start, length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        resultTitleText.contentDescription = "$title. $detail"
    } else {
        resultPanel.visibility = View.GONE
    }
    ensureOfflineResultActions(state)

    // State-driven visibility changes can alter the surrounding vertical flow.
    // Keep the board itself centered after those changes, without resizing it.
    boardView?.post {
        centerBoardInViewport()
    }

    val activePlayerId = active?.playerId
    val interactionPlayerId =
        if (
            blockedRemainingMillis > 0L ||
            isComputerMode &&
            engine.isComputerPlayer(activePlayerId)
        ) {
            null
        } else {
            activePlayerId
        }
    val characterIdsBySeat = resolveOfflineCharacterIds(state)

    boardView?.bind(
        state = state,
        playerId = interactionPlayerId,
        perspectiveColor = state.players.firstOrNull()?.color,
        characterIdsBySeat = characterIdsBySeat,
    )

    renderPlayerRails(state)

    val pending = state.pendingRoll
    val latest = state.history.lastOrNull()
    val latestForActive =
        latest?.takeIf {
            it.playerId == activePlayerId
        }
    val presentedEventIndex =
        pending?.eventIndex
            ?: latestForActive?.eventIndex
    val outcome =
        pending?.outcome
            ?: latestForActive?.effectiveOutcome
            ?: latestForActive?.outcome

    if (
        outcome != null &&
        presentedEventIndex != null
    ) {
        val eventKey =
            "${state.matchId}:$presentedEventIndex"
        val shouldAnimate =
            presentedDiceEventKey != eventKey
        diceView?.showOutcome(
            outcome = outcome,
            animate = shouldAnimate,
        )
        presentedDiceEventKey = eventKey
    } else {
        presentedDiceEventKey = null
    }

    val computerTurn =
        isComputerMode &&
            engine.isComputerPlayer(activePlayerId)
    val canRoll =
        state.status == "ACTIVE" &&
            pending == null &&
            !computerTurn &&
            blockedRemainingMillis == 0L
    val activeHomeCount =
        active?.tokens
            ?.count {
                it == LudoPathEncoding.HOME_POSITION
            }
            ?: 0

    turnText?.text =
        when {
            state.status == "FINISHED" ->
                "MATCH COMPLETE"

            computerTurn ->
                (active?.displayName ?: "CPU") + " • THINKING"

            isComputerMode && activePlayerId == engine.humanPlayerId() ->
                "YOUR TURN"

            else ->
                (active?.displayName ?: "Player") + " • TURN"
        }

    infoText?.text =
        when {
            state.status == "FINISHED" ->
                "4/4 PAWS HOME"

            pending != null -> {
                val legalCount = pending.legalTokenIndexes.size
                val choiceLabel =
                    if (legalCount == 1) "CHOICE" else "CHOICES"
                "DICE ${pending.outcome} • $legalCount $choiceLabel • $activeHomeCount/4 HOME"
            }

            else ->
                "$activeHomeCount/4 PAWS HOME"
        }

    diceHost?.isEnabled = canRoll
    diceView?.setAttentionEnabled(canRoll)
    // Disabled means non-interactive only. Never fade the dice itself: after a
    // resolved roll the player still needs to read that face while moving.
    diceHost?.alpha = 1f

    when {
        blockedRemainingMillis > 0L ->
            showStatus("Finishing the current paw action…")

        state.status == "FINISHED" ->
            showStatus("Rematch or change setup.")

        computerTurn && pending != null ->
            showStatus("Computer is choosing a paw…")

        computerTurn ->
            showStatus("Computer is rolling…")

        pending != null ->
            showStatus("Choose a glowing paw.")

        else ->
            showStatus("Tap the dice beside the active character.")
    }

    if (blockedRemainingMillis > 0L) {
        schedulePresentationUnlock(
            state = state,
            delayMillis = blockedRemainingMillis,
        )
        return
    }

    scheduleSingleLegalHumanMove(
        state = state,
        activePlayerId = activePlayerId,
    )
    scheduleComputerTurnIfNeeded(state)
}

internal fun OfflineGameActivity.renderCommittedMove(
    previous: MatchSnapshot?,
    current: MatchSnapshot,
) {
    val speed =
        GameSettingsStore(this)
            .snapshot()
            .gameSpeed
    val motions =
        LudoPawsPawnAnimationPolicy.plans(
            previous = previous,
            current = current,
        )
    val captured =
        motions.any {
            it.kind == LudoPawsPawnMotionKind.CAPTURE_RETURN
        }
    val delay =
        LudoPawsGameplayPacingPolicy
            .interactionDelayAfterMoveMillis(
                previous = previous,
                current = current,
                speed = speed,
            )

    renderGame(
        state = current,
        presentationDelayMillis = delay,
    )
    showStatus(
        if (captured) {
            "Capture • contact, push and return…"
        } else {
            "Paw is moving…"
        },
    )
}

private fun OfflineGameActivity.schedulePresentationUnlock(
    state: MatchSnapshot,
    delayMillis: Long,
) {
    val matchId = state.matchId
    val turnSeat = state.turnSeat
    val pendingEventIndex = state.pendingRoll?.eventIndex
    val latestEventIndex = state.history.lastOrNull()?.eventIndex

    handler.postDelayed(
        {
            val latest = session.snapshot() ?: return@postDelayed
            val stillSamePresentation =
                latest.matchId == matchId &&
                    latest.turnSeat == turnSeat &&
                    latest.pendingRoll?.eventIndex == pendingEventIndex &&
                    latest.history.lastOrNull()?.eventIndex == latestEventIndex
            if (!stillSamePresentation) return@postDelayed

            if (
                SystemClock.uptimeMillis() <
                gameplayActionBlockedUntilMillis
            ) {
                renderGame(latest)
                return@postDelayed
            }

            gameplayActionBlockedUntilMillis = 0L
            renderGame(latest)
        },
        delayMillis.coerceAtLeast(1L),
    )
}

private fun OfflineGameActivity.scheduleSingleLegalHumanMove(
    state: MatchSnapshot,
    activePlayerId: String?,
) {
    if (state.status != "ACTIVE") return
    if (engine.isComputerPlayer(activePlayerId)) return

    val pending = state.pendingRoll ?: return
    val activePlayer =
        state.players
            .firstOrNull { it.playerId == activePlayerId }
            ?: return
    val outcome = pending.outcome ?: return
    val tokenIndex =
        LudoTurnAutomationPolicy.singleAutomaticTokenIndex(
            tokens = activePlayer.tokens,
            roll = outcome,
            legalTokenIndexes = pending.legalTokenIndexes,
        ) ?: return

    val eventKey =
        "single-legal:${state.matchId}:${pending.eventIndex}:$tokenIndex"
    if (singleLegalMoveKey == eventKey) return
    singleLegalMoveKey = eventKey

    showStatus("Only one effective move • holding the dice result…")

    val speed =
        GameSettingsStore(this)
            .snapshot()
            .gameSpeed
    handler.postDelayed(
        {
            if (singleLegalMoveKey != eventKey) return@postDelayed
            val latest = session.snapshot()
            val latestPending = latest?.pendingRoll
            val latestOutcome = latestPending?.outcome
            val latestActive =
                latest?.players?.getOrNull(latest.turnSeat)
            val stillSameMove =
                latest != null &&
                    latest.matchId == state.matchId &&
                    latest.status == "ACTIVE" &&
                    latestActive != null &&
                    latestActive.playerId == activePlayerId &&
                    !engine.isComputerPlayer(latestActive.playerId) &&
                    latestPending?.eventIndex == pending.eventIndex &&
                    latestOutcome != null &&
                    LudoTurnAutomationPolicy.singleAutomaticTokenIndex(
                        tokens = latestActive.tokens,
                        roll = latestOutcome,
                        legalTokenIndexes = latestPending.legalTokenIndexes,
                    ) == tokenIndex

            if (!stillSameMove) {
                singleLegalMoveKey = null
                return@postDelayed
            }

            singleLegalMoveKey = null
            runCatching {
                session.move(tokenIndex)
            }.onSuccess { next ->
                OfflineLudoPawsFeedbackDispatcher.committed(
                    context = this,
                    previous = latest,
                    current = next,
                    action = OfflineFeedbackAction.MOVE,
                )
                renderCommittedMove(
                    previous = latest,
                    current = next,
                )
            }.onFailure { error ->
                showStatus(
                    error.message ?: "Automatic move failed",
                )
            }
        },
        LudoPawsGameplayPacingPolicy
            .postRollAutoMoveDelayMillis(speed),
    )
}
