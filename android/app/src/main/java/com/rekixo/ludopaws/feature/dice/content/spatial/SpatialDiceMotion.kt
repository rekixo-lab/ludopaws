package com.rekixo.ludopaws.feature.dice.content.spatial

import com.rekixo.ludopaws.DiceRollAnimationPolicy
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class DiceRotation(val x: Float, val y: Float, val z: Float, val w: Float) {
    operator fun times(other: DiceRotation) = DiceRotation(
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w,
        w * other.w - x * other.x - y * other.y - z * other.z,
    )

    fun inverse() = DiceRotation(-x, -y, -z, w)

    fun writeMatrix(out: FloatArray) {
        out[0] = 1f - 2f * (y * y + z * z)
        out[1] = 2f * (x * y - z * w)
        out[2] = 2f * (x * z + y * w)
        out[3] = 2f * (x * y + z * w)
        out[4] = 1f - 2f * (x * x + z * z)
        out[5] = 2f * (y * z - x * w)
        out[6] = 2f * (x * z - y * w)
        out[7] = 2f * (y * z + x * w)
        out[8] = 1f - 2f * (x * x + y * y)
    }

    fun toward(other: DiceRotation, t: Float): DiceRotation {
        val dot = x * other.x + y * other.y + z * other.z + w * other.w
        val sign = if (dot < 0f) -1f else 1f
        val theta = acos((dot * sign).coerceIn(0f, 1f))
        val divisor = sin(theta)
        val a = if (divisor > .001f) sin((1f - t) * theta) / divisor else 1f - t
        val b = sign * if (divisor > .001f) sin(t * theta) / divisor else t
        val nx = x * a + other.x * b
        val ny = y * a + other.y * b
        val nz = z * a + other.z * b
        val nw = w * a + other.w * b
        val length = sqrt(nx * nx + ny * ny + nz * nz + nw * nw)
        return DiceRotation(nx / length, ny / length, nz / length, nw / length)
    }

    companion object {
        fun around(x: Float, y: Float, z: Float, radians: Float): DiceRotation {
            val half = radians * .5f
            val scale = sin(half) / sqrt(x * x + y * y + z * z)
            return DiceRotation(x * scale, y * scale, z * scale, cos(half))
        }
    }
}

/** Pure orientation math. The final face always comes from the game or the Shop preview. */
internal object SpatialDiceMotion {
    private const val RAMP_SECONDS = .34f
    private const val INITIAL_SPEED = 1.2f
    private const val FULL_SPEED = 15.5f
    private val halfTurn = PI.toFloat()
    private val quarterTurn = halfTurn / 2f
    private val tilt = DiceRotation.around(1f, 0f, 0f, .37f) * DiceRotation.around(0f, 1f, 0f, -.42f)
    private val rests = listOf(
        tilt,
        tilt * DiceRotation.around(1f, 0f, 0f, quarterTurn),
        tilt * DiceRotation.around(0f, 1f, 0f, -quarterTurn),
        tilt * DiceRotation.around(0f, 1f, 0f, quarterTurn),
        tilt * DiceRotation.around(1f, 0f, 0f, -quarterTurn),
        tilt * DiceRotation.around(0f, 1f, 0f, halfTurn),
    )

    fun resting(face: Int): DiceRotation = rests[face.coerceIn(1, 6) - 1]

    fun rolling(startFace: Int, rollMillis: Long): DiceRotation =
        spin(angleAt(rollMillis)) * resting(startFace)

    fun settling(startFace: Int, targetFace: Int, rollMillis: Long, progress: Float): DiceRotation {
        val start = rolling(startFace, rollMillis)
        val target = resting(targetFace)
        val t = progress.coerceIn(0f, 1f)
        val duration = DiceRollAnimationPolicy.SETTLE_DURATION_MILLIS / 1000f
        val speed = INITIAL_SPEED + (FULL_SPEED - INITIAL_SPEED) * (rollMillis / 1000f / RAMP_SECONDS).coerceIn(0f, 1f)
        // Continue the same rotation at the join, then brake while blending in
        // the correction needed to land on the authoritative face.
        val extraAngle = speed * duration * (t - t * t / 2f)
        val correctedTarget = spin(speed * duration / 2f).inverse() * target
        val blend = t * t * (3f - 2f * t)
        return spin(extraAngle) * start.toward(correctedTarget, blend)
    }

    private fun spin(angle: Float): DiceRotation = DiceRotation.around(.74f, .64f, .21f, angle)

    private fun angleAt(rollMillis: Long): Float {
        val elapsed = rollMillis.coerceAtLeast(0L) / 1000.0
        val ramp = minOf(elapsed, RAMP_SECONDS.toDouble())
        val angle = INITIAL_SPEED * ramp +
            (FULL_SPEED - INITIAL_SPEED) * ramp * ramp / (2.0 * RAMP_SECONDS) +
            FULL_SPEED * (elapsed - ramp)
        return (angle % (2.0 * PI)).toFloat()
    }
}
