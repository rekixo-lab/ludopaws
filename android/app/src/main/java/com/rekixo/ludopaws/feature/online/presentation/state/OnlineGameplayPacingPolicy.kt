package com.rekixo.ludopaws

import android.os.SystemClock
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.feature.settings.data.local.GameSpeed

internal data class OnlineGameplayPacingTransition(
    val key: String,
    val delayMillis: Long,
)

/**
 * Reuses the proven offline pacing values for remote match presentation.
 * This policy delays presentation/input only; server-authoritative game rules
 * and the 15-second turn timeout remain unchanged.
 */
internal object OnlineGameplayPacingPolicy {
    fun resolve(
        previous: MatchSnapshot?,
        current: MatchSnapshot,
        speed: GameSpeed,
    ): OnlineGameplayPacingTransition? {
        if (previous == null || previous.matchId != current.matchId) return null
        if (previous.status != "ACTIVE" || current.status !in setOf("ACTIVE", "FINISHED")) return null

        val pending = current.pendingRoll
        if (
            current.status == "ACTIVE" &&
            pending?.status == "RESOLVED" &&
            (
                previous.pendingRoll?.status != "RESOLVED" ||
                    previous.pendingRoll?.eventIndex != pending.eventIndex
            )
        ) {
            return OnlineGameplayPacingTransition(
                key = "${current.matchId}:roll:${pending.eventIndex}",
                delayMillis = LudoPawsGameplayPacingPolicy.diceResultHoldMillis(speed),
            )
        }

        val previousLatestEvent = previous.history.lastOrNull()?.eventIndex
        val latestEvent = current.history.lastOrNull()
        if (
            current.status == "ACTIVE" &&
            pending == null &&
            current.randomEventIndex > previous.randomEventIndex &&
            latestEvent != null &&
            latestEvent.eventIndex == previous.randomEventIndex &&
            latestEvent.eventIndex != previousLatestEvent &&
            latestEvent.moveTokenIndex == null
        ) {
            // A roll with no legal move may pass the turn in the same server response,
            // without exposing an intermediate RESOLVED snapshot.
            return OnlineGameplayPacingTransition(
                key = "${current.matchId}:roll:${latestEvent.eventIndex}",
                delayMillis = LudoPawsGameplayPacingPolicy.diceResultHoldMillis(speed),
            )
        }

        if (current.players.none { next ->
                val before = previous.players.firstOrNull { it.playerId == next.playerId }
                before != null && before.tokens != next.tokens
            }
        ) {
            return null
        }

        val motions = LudoPawsPawnAnimationPolicy.plans(
            previous = previous,
            current = current,
        )
        if (motions.isEmpty()) return null

        val forward = motions.firstOrNull { it.kind == LudoPawsPawnMotionKind.FORWARD }
        val forwardDuration = forward?.let {
            LudoPawsGameplayPacingPolicy.forwardTravelDurationMillis(
                speed = speed,
                visualSteps = it.visualSteps,
            )
        } ?: 0L
        val hasCapture = motions.any { it.kind == LudoPawsPawnMotionKind.CAPTURE_RETURN }
        val previousActor = previous.actingSeat ?: previous.turnSeat
        val currentActor = current.actingSeat ?: current.turnSeat
        val extraTurn = previousActor == currentActor
        val captureDuration =
            if (hasCapture) LudoPawsGameplayPacingPolicy.captureReturnDurationMillis(speed) else 0L
        // Do not consume the next remote player's server turn with a local finish show.
        // Only a completed match holds the result overlay for the full entry sequence.
        val delay = if (current.status == "FINISHED" &&
            LudoPawsHomeEntryPolicy.newlyComplete(previous, current)) {
            LudoPawsGameplayPacingPolicy.forwardAnimationDurationMillis(speed, forward?.visualSteps ?: 1) +
                LudoPawsHomeEntryPolicy.DURATION_MS
        } else forwardDuration + captureDuration +
            LudoPawsGameplayPacingPolicy.postMoveBreathMillis(speed, extraTurn)

        return OnlineGameplayPacingTransition(
            key = "${current.matchId}:move:${current.randomEventIndex}:${currentActor}",
            delayMillis = delay.coerceAtLeast(0L),
        )
    }
}


internal fun MainActivity.applyOnlineGameplayPacing(
    previous: MatchSnapshot?,
    current: MatchSnapshot,
) {
    if (previous == null || previous.matchId != current.matchId) {
        onlineGameplayActionBlockedUntilMillis = 0L
        lastOnlineGameplayPacingKey = null
        return
    }
    if (current.status !in setOf("ACTIVE", "FINISHED")) {
        onlineGameplayActionBlockedUntilMillis = 0L
        lastOnlineGameplayPacingKey = null
        return
    }

    val transition = OnlineGameplayPacingPolicy.resolve(
        previous = previous,
        current = current,
        speed = GameSettingsStore(this).snapshot().gameSpeed,
    ) ?: return
    if (transition.key == lastOnlineGameplayPacingKey) return

    lastOnlineGameplayPacingKey = transition.key
    // The dice renderer owns the remaining spin + settle lock, avoiding a
    // second full hold after the result has already waited on the network.
    if (transition.key.startsWith("${current.matchId}:roll:")) return

    val now = SystemClock.uptimeMillis()
    val previewStartedAt = onlineMovePreviewStartedAtMillis
    val alreadyPresentedMillis =
        if (
            transition.key.startsWith("${current.matchId}:move:") &&
            previewStartedAt != null
        ) {
            onlineMovePreviewStartedAtMillis = null
            (now - previewStartedAt).coerceAtLeast(0L)
        } else {
            0L
        }
    val remainingDelay =
        LudoPawsGameplayPacingPolicy.remainingPresentationDelayMillis(
            totalDelayMillis = transition.delayMillis,
            alreadyPresentedMillis = alreadyPresentedMillis,
        ).let { remaining ->
            // A late server confirmation must not skip the authoritative finish show.
            if (current.status == "FINISHED" && LudoPawsHomeEntryPolicy.newlyComplete(previous, current))
                maxOf(remaining, LudoPawsHomeEntryPolicy.DURATION_MS) else remaining
        }
    onlineGameplayActionBlockedUntilMillis = maxOf(
        onlineGameplayActionBlockedUntilMillis,
        now + remainingDelay,
    )
    scheduleOnlineGameplayUnlock()
}
