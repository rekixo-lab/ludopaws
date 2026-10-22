package com.rekixo.ludopaws.feature.characters.content.goat

import com.rekixo.ludopaws.feature.characters.shared.CharacterHopDynamics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

enum class Goat3DMotion {
    IDLE,
    HOP,
    HOME,
}

data class Goat3DPose(
    val liftY: Float = 0f,
    val bodyYawDegrees: Float = 0f,
    val headTiltDegrees: Float = 0f,
    val earFlickDegrees: Float = 0f,
    val beardSwingDegrees: Float = 0f,
    val tailFlickDegrees: Float = 0f,
    val legSwingDegrees: Float = 0f,
    val legTuckDegrees: Float = 0f,
    val bodyPitchDegrees: Float = 0f,
    val bodyRollDegrees: Float = 0f,
    val neckPitchDegrees: Float = 0f,
    val bodyCrouch: Float = 0f,
    val frontLegTuckDegrees: Float? = null,
    val rearLegTuckDegrees: Float? = null,
    val frontLegSwingDegrees: Float? = null,
    val rearLegSwingDegrees: Float? = null,
)

/** Pure, testable motion timeline for the code-generated 3D goat pawn. */
object Goat3DMotionTimeline {
    const val IDLE_DURATION_MILLIS = 2_000L
    const val HOP_DURATION_MILLIS = 700L
    const val HOME_DURATION_MILLIS = 1_350L
    const val TAKEOFF_PROGRESS = CharacterHopDynamics.TAKEOFF
    const val FRONT_CONTACT_PROGRESS = CharacterHopDynamics.CONTACT

    fun stepTravelProgress(progress: Float): Float =
        CharacterHopDynamics.travel(progress)

    fun durationMillis(
        motion: Goat3DMotion,
    ): Long =
        when (motion) {
            Goat3DMotion.IDLE -> IDLE_DURATION_MILLIS
            Goat3DMotion.HOP -> HOP_DURATION_MILLIS
            Goat3DMotion.HOME -> HOME_DURATION_MILLIS
        }

    fun sample(
        motion: Goat3DMotion,
        progress: Float,
    ): Goat3DPose {
        val p = progress.coerceIn(0f, 1f)
        fun smooth(start: Float, end: Float): Float {
            val t = ((p - start) / (end - start)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
        fun pairLift(start: Float, peak: Float, release: Float, contact: Float): Float =
            smooth(start, peak) * (1f - smooth(release, contact))
        return when (motion) {
            Goat3DMotion.IDLE -> {
                val wave = sin(p * PI * 2.0).toFloat()
                fun pulse(center: Float, width: Float): Float {
                    val distance = (p - center) / width
                    return exp(-distance * distance)
                }
                val ear = 5f * pulse(.25f, .055f) - 4f * pulse(.7f, .075f)
                val tail = 10f * pulse(.25f, .09f) - 8f * pulse(.73f, .11f)
                val envelope = sin(p * PI).toFloat().let { it * it }
                val glance = pulse(.62f, .12f) * envelope
                val sniff = pulse(.40f, .07f) * envelope
                Goat3DPose(
                    liftY = 0f,
                    bodyYawDegrees = 2.5f * glance,
                    headTiltDegrees = 2.2f * wave + 5f * glance,
                    earFlickDegrees = ear,
                    beardSwingDegrees = 3.5f * -wave,
                    tailFlickDegrees = tail,
                    bodyRollDegrees = .65f * wave,
                    neckPitchDegrees = .8f * -wave + 6f * sniff - 2f * glance,
                    bodyPitchDegrees = 1.2f * sniff,
                )
            }

            Goat3DMotion.HOP -> {
                // Bound in fore/hind pairs: forelegs release first, hindlegs push off
                // later; forelegs reach the destination before the hindlegs settle.
                val front = pairLift(.16f, .30f, .51f, .76f)
                val rear = pairLift(.24f, .44f, .70f, .90f)
                val flight = stepTravelProgress(p)
                val arc = sin(flight * PI).toFloat().coerceAtLeast(0f)
                val wobble = sin(p * PI * 2.0).toFloat()
                val preload = pairLift(0f, .09f, .12f, .26f)
                val landing = pairLift(.74f, .83f, .86f, 1f)
                Goat3DPose(
                    liftY = if (p in TAKEOFF_PROGRESS..FRONT_CONTACT_PROGRESS) .60f * 4f * flight * (1f - flight) else 0f,
                    headTiltDegrees = -5.5f * arc,
                    earFlickDegrees = 15f * arc - 5f * landing,
                    beardSwingDegrees = 18f * arc + 4f * wobble - 8f * landing,
                    tailFlickDegrees = 20f * wobble,
                    legSwingDegrees = 27f * wobble * front,
                    legTuckDegrees = 32f * arc,
                    frontLegTuckDegrees = 34f * front,
                    rearLegTuckDegrees = 30f * rear,
                    frontLegSwingDegrees = (18f - 36f * smooth(.38f, .68f)) * front,
                    rearLegSwingDegrees = (-20f + 34f * smooth(.40f, .68f)) * rear,
                    bodyPitchDegrees = -5f * front * (1f - smooth(.35f, .58f)) + 4f * landing,
                    bodyRollDegrees = 0f,
                    neckPitchDegrees = -4f * front + 6f * landing,
                    bodyCrouch = .08f * preload + .065f * landing,
                )
            }

            Goat3DMotion.HOME -> {
                val bounce =
                    abs(sin(p * PI * 3.0).toFloat()) *
                        (1f - p * 0.30f)
                Goat3DPose(
                    liftY = 0.31f * bounce,
                    bodyYawDegrees = 360f * p,
                    headTiltDegrees = 8f * sin(p * PI * 2.0).toFloat(),
                    earFlickDegrees = 10f * sin(p * PI * 4.0).toFloat(),
                    beardSwingDegrees = 22f * sin(p * PI * 3.0).toFloat(),
                    tailFlickDegrees = 28f * sin(p * PI * 6.0).toFloat(),
                    legSwingDegrees = 11f * sin(p * PI * 6.0).toFloat(),
                    legTuckDegrees = 8f * sin(p * PI * 3.0).toFloat().coerceAtLeast(0f),
                    bodyPitchDegrees = -4f * sin(p * PI * 6.0).toFloat() * sin(p * PI).toFloat(),
                    neckPitchDegrees = 3f * sin(p * PI * 6.0).toFloat() * sin(p * PI).toFloat(),
                    // Suppress crouch at clip boundaries so neutral transitions stay continuous.
                    bodyCrouch = .035f * (1f - bounce.coerceIn(0f, 1f)) * sin(p * PI).toFloat().let { it * it },
                )
            }
        }
    }
}
