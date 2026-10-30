package com.rekixo.ludopaws.feature.offline

import com.rekixo.ludopaws.LudoPawsGameplayPacingPolicy
import com.rekixo.ludopaws.MatchSnapshot
import com.rekixo.ludopaws.OfflineGameActivity
import com.rekixo.ludopaws.feature.offline.domain.ai.LudoPawsComputerMovePolicy
import com.rekixo.ludopaws.feature.offline.presentation.feedback.OfflineFeedbackAction
import com.rekixo.ludopaws.feature.offline.presentation.feedback.OfflineLudoPawsFeedbackDispatcher
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.feature.settings.data.local.GameSoundFeedback
import com.rekixo.ludopaws.ui.offline.gameplay.OfflineTurnTransitionFeedbackPolicy
import com.rekixo.ludopaws.ui.offline.gameplay.renderCommittedMove
import com.rekixo.ludopaws.ui.offline.gameplay.renderGame

internal fun OfflineGameActivity.rollOffline() {
    val control = diceHost ?: return
    if (!control.isEnabled || !offlineActionsAllowed || offlineRollInFlight) return

    val activeId = engine.activePlayerId()
    if (engine.isComputerPlayer(activeId)) return

    control.isEnabled = false
    control.alpha = .58f
    GameSoundFeedback.roll(this)
    diceView?.startRolling()
    gameplayPawsPromotion?.quietFor(1_500L)
    showStatus("Rolling locally…")

    val delay =
        GameSettingsStore(this)
            .snapshot()
            .gameSpeed
            .rollDelayMs

    handler.postDelayed(
        {
            if (!offlineActionsAllowed) return@postDelayed
            val previous = session.snapshot()
            prepareOfflineRoll().onSuccess { state ->
                OfflineLudoPawsFeedbackDispatcher.committed(
                    context = this,
                    previous = previous,
                    current = state,
                    action = OfflineFeedbackAction.ROLL,
                )
                renderRolledState(
                    previous = previous,
                    current = state,
                )
            }.onFailure { error ->
                diceView?.stopRolling()
                control.isEnabled = true
                control.alpha = 1f
                showStatus(error.message ?: "Roll failed")
            }
        },
        delay,
    )
}

internal fun OfflineGameActivity.scheduleComputerTurnIfNeeded(
    state: MatchSnapshot,
) {
    if (!offlineActionsAllowed || offlineRollInFlight || !isComputerMode || state.status != "ACTIVE") {
        computerActionRevision = null
        return
    }

    val active = state.players.getOrNull(state.turnSeat) ?: return
    if (!engine.isComputerPlayer(active.playerId)) {
        computerActionRevision = null
        return
    }

    val actionKey =
        state.randomEventIndex * 100 +
            state.turnSeat * 10 +
            if (state.pendingRoll != null) 1 else 0

    if (computerActionRevision == actionKey) return
    computerActionRevision = actionKey

    val speed =
        GameSettingsStore(this)
            .snapshot()
            .gameSpeed

    if (state.pendingRoll == null) {
        GameSoundFeedback.roll(this)
        diceView?.startRolling()
        gameplayPawsPromotion?.quietFor(1_500L)
        showStatus(active.displayName + " is rolling…")
        handler.postDelayed(
            {
                if (!offlineActionsAllowed) return@postDelayed
            val latest = session.snapshot()
                val latestActive =
                    latest?.players?.getOrNull(latest.turnSeat)
                if (
                    latest == null ||
                    latest.status != "ACTIVE" ||
                    latest.pendingRoll != null ||
                    latestActive?.playerId != active.playerId
                ) {
                    return@postDelayed
                }

                prepareOfflineRoll().onSuccess { next ->
                    OfflineLudoPawsFeedbackDispatcher.committed(
                        context = this,
                        previous = latest,
                        current = next,
                        action = OfflineFeedbackAction.ROLL,
                    )
                    renderRolledState(
                        previous = latest,
                        current = next,
                    )
                }.onFailure { error ->
                    diceView?.stopRolling()
                    computerActionRevision = null
                    showStatus(error.message ?: "Computer roll failed")
                }
            },
            speed.cpuThinkMs,
        )
        return
    }

    val pending = state.pendingRoll ?: return
    val outcome = pending.outcome ?: return
    val token =
        LudoPawsComputerMovePolicy.chooseToken(
            state = state,
            player = active,
            legalTokenIndexes = pending.legalTokenIndexes,
            outcome = outcome,
        ) ?: run {
            computerActionRevision = null
            return
        }

    showStatus(active.displayName + " is choosing a move…")
    handler.postDelayed(
        {
            if (!offlineActionsAllowed) return@postDelayed
            val latest = session.snapshot()
            val latestActive =
                latest?.players?.getOrNull(latest.turnSeat)
            if (
                latest == null ||
                latest.status != "ACTIVE" ||
                latestActive?.playerId != active.playerId ||
                latest.pendingRoll?.legalTokenIndexes?.contains(token) != true
            ) {
                return@postDelayed
            }

            runCatching {
                session.move(token)
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
                computerActionRevision = null
                showStatus(error.message ?: "Computer move failed")
            }
        },
        maxOf(
            (speed.cpuThinkMs / 2).coerceAtLeast(180L),
            LudoPawsGameplayPacingPolicy
                .postRollAutoMoveDelayMillis(speed),
        ),
    )
}

private fun OfflineGameActivity.renderRolledState(
    previous: MatchSnapshot?,
    current: MatchSnapshot,
) {
    val speed =
        GameSettingsStore(this)
            .snapshot()
            .gameSpeed
    renderGame(
        state = current,
        presentationDelayMillis =
            LudoPawsGameplayPacingPolicy
                .diceResultHoldMillis(speed),
    )
    OfflineTurnTransitionFeedbackPolicy
        .message(
            previous = previous,
            current = current,
        )
        ?.let(::showStatus)
}

/** Compatibility seam kept for existing tests and diagnostics. */
internal fun computerMoveDestination(
    position: Int,
    outcome: Int,
): Int? =
    LudoPawsComputerMovePolicy.destinationForRoll(
        position = position,
        outcome = outcome,
    )

internal fun OfflineGameActivity.offlineHistory(
    state: MatchSnapshot?,
): String {
    if (state == null || state.history.isEmpty()) {
        return "No offline rolls yet."
    }

    return buildString {
        append("Recent offline rolls\n\n")
        state.history
            .takeLast(16)
            .reversed()
            .forEach { event ->
                append("#")
                append(event.eventIndex)
                append("  dice=")
                append(event.effectiveOutcome ?: event.outcome ?: "?")
                if (event.openingRollApplied) {
                    append("  opening-bonus")
                    val raw = event.randomOutcome
                    val effective = event.effectiveOutcome ?: event.outcome
                    if (raw != null && raw != effective) {
                        append("  verifiedRaw=")
                        append(raw)
                    }
                }
                if (event.proofDigest != null) {
                    append("  localV4=")
                    append(event.proofDigest.take(8))
                    append("…")
                }
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

internal fun OfflineGameActivity.showStatus(
    value: String,
) {
    statusText?.text = value
}

/** Derive and verify on one worker; commit only to the still-current foreground generation. */
internal class OfflineRollCompletion {
    var success: (MatchSnapshot) -> Unit = {}
    var failure: (Throwable) -> Unit = {}
    fun onSuccess(action: (MatchSnapshot) -> Unit) = apply { success = action }
    fun onFailure(action: (Throwable) -> Unit) = apply { failure = action }
}

internal fun OfflineGameActivity.prepareOfflineRoll(): OfflineRollCompletion {
    val completion = OfflineRollCompletion()
    val generation = offlineGeneration
    val work = runCatching { engine.prepareRoll() }
    offlineRollInFlight = true
    offlineRollExecutor.execute {
        val prepared = work.mapCatching { it.invoke() }
        handler.post {
            if (generation != offlineGeneration || !offlineActionsAllowed) return@post
            offlineRollInFlight = false
            prepared.mapCatching { engine.applyPreparedRoll(it) }
                .onSuccess(completion.success).onFailure(completion.failure)
        }
    }
    return completion
}
