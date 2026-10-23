package com.rekixo.ludopaws

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Presentation-only facing policy for the 3D animal pawns.
 *
 * A resting pawn faces the local player. During a forward move the pawn turns
 * toward the visible board-space direction of the current road segment. Turns
 * are eased so corners read like an animal changing direction instead of a
 * sprite snapping by 90 degrees.
 */
internal object LudoPawsPawnFacingPolicy {
    const val FRONT_YAW_DEGREES = 0f
    const val RETURN_TO_PLAYER_MILLIS = 180L

    private const val TURN_PORTION_OF_HOP = 0.28f

    fun yawForVisibleDelta(
        dx: Float,
        dy: Float,
    ): Float {
        if (abs(dx) <= 0.0001f && abs(dy) <= 0.0001f) {
            return FRONT_YAW_DEGREES
        }

        // The generated animals face the viewer at yaw 0. Screen Y grows down,
        // so atan2(dx, dy) maps down=0, right=+90, up=180 and left=-90 while
        // still allowing a natural diagonal heading for yard-entry jumps.
        return Math.toDegrees(
            atan2(dx.toDouble(), dy.toDouble()),
        ).toFloat()
    }

    fun movingYaw(
        previousYawDegrees: Float,
        targetYawDegrees: Float,
        stepProgress: Float,
    ): Float {
        val turnProgress =
            (stepProgress.coerceIn(0f, 1f) / TURN_PORTION_OF_HOP)
                .coerceIn(0f, 1f)
        return interpolateYaw(
            fromYawDegrees = previousYawDegrees,
            toYawDegrees = targetYawDegrees,
            progress = smoothStep(turnProgress),
        )
    }

    fun settlingYaw(
        lastTravelYawDegrees: Float,
        elapsedAfterMoveMillis: Long,
    ): Float {
        val progress =
            elapsedAfterMoveMillis
                .coerceAtLeast(0L)
                .toFloat() /
                RETURN_TO_PLAYER_MILLIS.toFloat()
        return interpolateYaw(
            fromYawDegrees = lastTravelYawDegrees,
            toYawDegrees = FRONT_YAW_DEGREES,
            progress = smoothStep(progress.coerceIn(0f, 1f)),
        )
    }

    internal fun interpolateYaw(
        fromYawDegrees: Float,
        toYawDegrees: Float,
        progress: Float,
    ): Float {
        val delta = shortestDelta(fromYawDegrees, toYawDegrees)
        return normalizeYaw(
            fromYawDegrees + delta * progress.coerceIn(0f, 1f),
        )
    }

    private fun shortestDelta(
        fromYawDegrees: Float,
        toYawDegrees: Float,
    ): Float =
        normalizeYaw(toYawDegrees - fromYawDegrees)

    private fun normalizeYaw(value: Float): Float {
        var normalized = value % 360f
        if (normalized > 180f) normalized -= 360f
        if (normalized <= -180f) normalized += 360f
        return normalized
    }

    private fun smoothStep(value: Float): Float =
        value * value * (3f - 2f * value)
}
