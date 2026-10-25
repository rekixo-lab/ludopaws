package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.settings.data.local.GameSpeed
import kotlin.math.max

/**
 * Central presentation pacing for readable Ludo Paws play.
 *
 * The authoritative rules and committed match state never depend on these
 * values. They only decide how long the UI waits before exposing the next
 * action while dice, movement, capture and settle animations remain visible.
 */
object LudoPawsGameplayPacingPolicy {
    const val DICE_SETTLE_DURATION_MILLIS = 450L

    fun diceResultHoldMillis(speed: GameSpeed): Long =
        when (speed) {
            GameSpeed.SLOW -> 850L
            GameSpeed.NORMAL -> 700L
            GameSpeed.FAST -> 520L
        }

    /** Small tail after the common result hold before a forced single move. */
    fun postRollAutoMoveDelayMillis(speed: GameSpeed): Long =
        when (speed) {
            GameSpeed.SLOW -> 150L
            GameSpeed.NORMAL -> 100L
            GameSpeed.FAST -> 80L
        }

    fun captureReturnDurationMillis(speed: GameSpeed): Long =
        when (speed) {
            GameSpeed.SLOW -> 1_500L
            GameSpeed.NORMAL -> 1_300L
            GameSpeed.FAST -> 1_050L
        }

    /** Travel time to the final cell, excluding the landing-settle tail. */
    fun forwardTravelDurationMillis(
        speed: GameSpeed,
        visualSteps: Int,
    ): Long =
        max(
            speed.moveStepMs,
            visualSteps.coerceAtLeast(1).toLong() * speed.moveStepMs,
        )

    /** Full movement animation time including its final landing settle. */
    fun forwardAnimationDurationMillis(
        speed: GameSpeed,
        visualSteps: Int,
    ): Long =
        forwardTravelDurationMillis(speed, visualSteps) +
            LudoPaws3DRenderCadencePolicy.FORWARD_LANDING_SETTLE_MILLIS

    /**
     * Physical capture impact and both comic vocals are aligned to the final
     * contact frame of the same forward animation.
     */
    fun captureContactDelayMillis(
        speed: GameSpeed,
        visualSteps: Int,
    ): Long =
        LudoPaws3DRenderCadencePolicy.captureContactDelayMillis(
            forwardAnimationDurationMillis(speed, visualSteps),
        )

    /**
     * One movement audio tick is emitted for every visual board step.
     *
     * The returned interval is derived from the actual forward animation
     * duration, so audio remains locked to the same timing source even if the
     * configured speed changes or a future presentation policy adjusts pacing.
     */
    fun movementAudioStepDurationMillis(
        forwardDurationMillis: Long,
        visualSteps: Int,
    ): Long =
        if (visualSteps <= 0) {
            0L
        } else {
            (
                forwardDurationMillis -
                    LudoPaws3DRenderCadencePolicy.FORWARD_LANDING_SETTLE_MILLIS
            )
                .coerceAtLeast(0L)
                .div(visualSteps.toLong())
        }

    /**
     * Returns only the presentation time that has not already elapsed while an
     * online move preview was visible. Network confirmation must not restart the
     * same movement hold from zero.
     */
    fun remainingPresentationDelayMillis(
        totalDelayMillis: Long,
        alreadyPresentedMillis: Long,
    ): Long =
        (totalDelayMillis - alreadyPresentedMillis.coerceAtLeast(0L))
            .coerceAtLeast(0L)

    fun postMoveBreathMillis(
        speed: GameSpeed,
        extraTurn: Boolean,
    ): Long {
        val base =
            when (speed) {
                GameSpeed.SLOW -> 560L
                GameSpeed.NORMAL -> 450L
                GameSpeed.FAST -> 330L
            }
        val extraRollHold =
            when (speed) {
                GameSpeed.SLOW -> 720L
                GameSpeed.NORMAL -> 600L
                GameSpeed.FAST -> 450L
            }
        return if (extraTurn) max(base, extraRollHold) else base
    }

    fun interactionDelayAfterMoveMillis(
        previous: MatchSnapshot?,
        current: MatchSnapshot,
        speed: GameSpeed,
        includeHomeEntry: Boolean = true,
    ): Long {
        if (
            previous == null ||
            previous.matchId != current.matchId
        ) {
            return postMoveBreathMillis(
                speed = speed,
                extraTurn = false,
            )
        }

        val motions =
            LudoPawsPawnAnimationPolicy.plans(
                previous = previous,
                current = current,
            )
        val forward =
            motions.firstOrNull {
                it.kind == LudoPawsPawnMotionKind.FORWARD
            }
        val forwardDuration =
            forward
                ?.let {
                    forwardTravelDurationMillis(
                        speed = speed,
                        visualSteps = it.visualSteps,
                    )
                }
                ?: 0L
        val hasCapture =
            motions.any {
                it.kind == LudoPawsPawnMotionKind.CAPTURE_RETURN
            }
        val extraTurn =
            previous.status == "ACTIVE" &&
                current.status == "ACTIVE" &&
                previous.turnSeat == current.turnSeat
        val captureDuration =
            if (hasCapture) {
                captureReturnDurationMillis(speed)
            } else {
                0L
            }

        if (includeHomeEntry && LudoPawsHomeEntryPolicy.newlyComplete(previous, current)) {
            return forwardAnimationDurationMillis(speed, forward?.visualSteps ?: 1) +
                LudoPawsHomeEntryPolicy.DURATION_MS
        }
        return forwardDuration +
            captureDuration +
            postMoveBreathMillis(
                speed = speed,
                extraTurn = extraTurn,
            )
    }
}
