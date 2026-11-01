package com.rekixo.ludopaws.feature.online

import android.os.SystemClock
import com.rekixo.ludopaws.LudoPawsGameplayPacingPolicy
import com.rekixo.ludopaws.MainActivity
import com.rekixo.ludopaws.MatchSnapshot
import com.rekixo.ludopaws.OnlineStateSource
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore

/**
 * Returns the remaining wall-clock-independent dice animation duration using
 * the same configured minimum spin duration as the verified-result renderer.
 */
internal fun MainActivity.remainingOnlineDicePresentationMillis(): Long {
    val speed = GameSettingsStore(this).snapshot().gameSpeed
    val minimumRollDuration =
        LudoPawsGameplayPacingPolicy.diceResultHoldMillis(speed)
    return diceView.remainingOutcomePresentationMillis(minimumRollDuration)
}

private fun MainActivity.onlineAutoMoveWaitMillis(includePostRollBreath: Boolean): Long {
    val now = SystemClock.uptimeMillis()
    val actionLockRemaining =
        (onlineGameplayActionBlockedUntilMillis - now).coerceAtLeast(0L)
    val diceAnimationRemaining = remainingOnlineDicePresentationMillis()
    val animationAndLock = maxOf(actionLockRemaining, diceAnimationRemaining)
    if (!includePostRollBreath) return animationAndLock

    val speed = GameSettingsStore(this).snapshot().gameSpeed
    return animationAndLock +
        LudoPawsGameplayPacingPolicy.postRollAutoMoveDelayMillis(speed)
}

/**
 * Schedules an automatic move only from a fresh authoritative resolved roll.
 * It waits for both the verified-dice animation and gameplay lock, then
 * revalidates the exact event/destination before sending the mutation.
 */
internal fun MainActivity.scheduleOnlineAutoMove(snapshot: MatchSnapshot) {
    val decision = OnlineAutoMovePolicy.resolve(
        state = snapshot,
        localPlayerId = playerId,
        isOnline = isOnline,
        hasAuthoritativeState =
            uiStateHolder.value.currentStateSource == OnlineStateSource.AUTHORITATIVE,
    )
    if (decision == null) {
        cancelScheduledOnlineAutoMove()
        return
    }
    if (scheduledAutoMoveEventKey == decision.eventKey) return
    if (
        lastAutoMoveAttemptEventKey == decision.eventKey &&
        autoMoveAttemptCount >= MAX_AUTO_MOVE_ATTEMPTS
    ) {
        cancelScheduledOnlineAutoMove()
        return
    }

    cancelScheduledOnlineAutoMove()
    scheduledAutoMoveEventKey = decision.eventKey
    // A move has one effective destination; explain why no second paw tap is needed.
    if (autoMoveAttemptCount == 0 || lastAutoMoveAttemptEventKey != decision.eventKey) {
        showStatus("Only one move available • moving your paw automatically…")
    }
    val task = object : Runnable {
        override fun run() {
            if (scheduledAutoMoveEventKey != decision.eventKey) return
            if (!canRenderUi() || !screenStarted) {
                cancelScheduledOnlineAutoMove()
                return
            }
            val current = currentState
            val latestDecision = current?.let {
                OnlineAutoMovePolicy.resolve(
                    state = it,
                    localPlayerId = playerId,
                    isOnline = isOnline,
                    hasAuthoritativeState =
                        uiStateHolder.value.currentStateSource == OnlineStateSource.AUTHORITATIVE,
                )
            }
            if (
                latestDecision == null ||
                latestDecision.eventKey != decision.eventKey ||
                latestDecision.tokenIndex != decision.tokenIndex
            ) {
                cancelScheduledOnlineAutoMove()
                return
            }

            // Never issue the move while a dice spin/settle frame is still visible.
            val waitMillis = onlineAutoMoveWaitMillis(includePostRollBreath = false)
            if (waitMillis > 0L) {
                mainHandler.postDelayed(this, waitMillis)
                return
            }
            if (onlineRollRequestInFlight || onlineMoveRequestInFlight) {
                cancelScheduledOnlineAutoMove()
                return
            }
            if (!canSubmitOnlineGameplayAction()) {
                val retryAfterLock =
                    (onlineGameplayActionBlockedUntilMillis - SystemClock.uptimeMillis())
                        .coerceAtLeast(1L)
                mainHandler.postDelayed(this, retryAfterLock)
                return
            }

            if (lastAutoMoveAttemptEventKey != decision.eventKey) {
                lastAutoMoveAttemptEventKey = decision.eventKey
                autoMoveAttemptCount = 0
            }
            if (autoMoveAttemptCount >= MAX_AUTO_MOVE_ATTEMPTS) {
                cancelScheduledOnlineAutoMove()
                return
            }
            autoMoveAttemptCount += 1
            cancelScheduledOnlineAutoMove()
            moveToken(decision.tokenIndex)
        }
    }
    scheduledAutoMoveRunnable = task
    mainHandler.postDelayed(
        task,
        onlineAutoMoveWaitMillis(includePostRollBreath = true) +
            // Bounded backoff after a transient rejected/failed network action.
            // The server has a separate authoritative forced-move fallback.
            when (autoMoveAttemptCount) {
                0 -> 0L
                1 -> 600L
                2 -> 1_200L
                else -> 2_400L
            },
    )
}

internal fun MainActivity.cancelScheduledOnlineAutoMove() {
    scheduledAutoMoveRunnable?.let(mainHandler::removeCallbacks)
    scheduledAutoMoveRunnable = null
    scheduledAutoMoveEventKey = null
}

private const val MAX_AUTO_MOVE_ATTEMPTS = 4
