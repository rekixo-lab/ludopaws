package com.rekixo.ludopaws.feature.characters.content.dog

import com.rekixo.ludopaws.feature.characters.shared.CharacterHopDynamics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class Dog3DMotion {
    IDLE,
    HOP,
    HOME,
}

data class Dog3DPose(
    val liftY: Float = 0f,
    val bodyYawDegrees: Float = 0f,
    val headTiltDegrees: Float = 0f,
    val earBounceDegrees: Float = 0f,
    val tailWagDegrees: Float = 0f,
    val legSwingDegrees: Float = 0f,
    val legTuckDegrees: Float = 0f,
    val bodyPitchDegrees: Float = 0f,
    val neckPitchDegrees: Float = 0f,
    val bodyCrouch: Float = 0f,
    val frontLegTuckDegrees: Float? = null,
    val rearLegTuckDegrees: Float? = null,
    val frontLegSwingDegrees: Float? = null,
    val rearLegSwingDegrees: Float? = null,
)

/** Pure, testable motion timeline for the code-generated 3D dog pawn. */
object Dog3DMotionTimeline {
    const val IDLE_DURATION_MILLIS = 1_800L
    const val HOP_DURATION_MILLIS = 680L
    const val HOME_DURATION_MILLIS = 1_300L

    fun stepTravelProgress(progress: Float) = CharacterHopDynamics.travel(progress)

    fun durationMillis(
        motion: Dog3DMotion,
    ): Long =
        when (motion) {
            Dog3DMotion.IDLE -> IDLE_DURATION_MILLIS
            Dog3DMotion.HOP -> HOP_DURATION_MILLIS
            Dog3DMotion.HOME -> HOME_DURATION_MILLIS
        }

    fun sample(
        motion: Dog3DMotion,
        progress: Float,
    ): Dog3DPose {
        val p = progress.coerceIn(0f, 1f)
        fun smooth(start: Float, end: Float): Float {
            val t = ((p - start) / (end - start)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
        fun pulse(a: Float, b: Float, c: Float, d: Float) = smooth(a, b) * (1f - smooth(c, d))
        return when (motion) {
            Dog3DMotion.IDLE -> {
                val wave = sin(p * PI * 2.0).toFloat()
                val tail = sin(p * PI * 4.0).toFloat()
                Dog3DPose(
                    liftY = 0f,
                    headTiltDegrees = 2.5f * wave,
                    earBounceDegrees = 2.0f * -wave,
                    neckPitchDegrees = 1.5f * -wave,
                    tailWagDegrees = 24f * tail,
                )
            }

            Dog3DMotion.HOP -> {
                val flight = stepTravelProgress(p)
                val arc = sin(flight * PI).toFloat().coerceAtLeast(0f)
                val front = pulse(.16f, .30f, .55f, .76f)
                val rear = pulse(.24f, .40f, .70f, .90f)
                val preload = pulse(0f, .08f, .12f, .20f)
                val land = pulse(.80f, .87f, .90f, 1f)
                val tail = sin(p * PI * 3.0).toFloat()
                Dog3DPose(
                    liftY = .58f * 4f * flight * (1f - flight),
                    bodyCrouch = .065f * preload + .05f * land,
                    bodyPitchDegrees = -4f * front * (1f - smooth(.35f, .55f)) + 3f * land,
                    neckPitchDegrees = 3f * arc - 4f * land,
                    frontLegTuckDegrees = 32f * front,
                    rearLegTuckDegrees = 29f * rear,
                    frontLegSwingDegrees = (16f - 30f * smooth(.40f, .71f)) * front,
                    rearLegSwingDegrees = (-16f + 28f * smooth(.43f, .70f)) * rear,
                    headTiltDegrees = -6f * arc,
                    earBounceDegrees = 18f * arc,
                    tailWagDegrees = 28f * tail,
                    legSwingDegrees = 28f * sin(p * PI * 2.0).toFloat() * arc,
                    legTuckDegrees = 34f * arc,
                )
            }

            Dog3DMotion.HOME -> {
                val bounce =
                    abs(sin(p * PI * 3.0).toFloat()) *
                        (1f - p * 0.30f)
                Dog3DPose(
                    liftY = 0.30f * bounce,
                    bodyYawDegrees = 360f * p,
                    headTiltDegrees = 7f * sin(p * PI * 2.0).toFloat(),
                    earBounceDegrees = 12f * sin(p * PI).toFloat().coerceAtLeast(0f),
                    tailWagDegrees = 48f * sin(p * PI * 6.0).toFloat(),
                    legSwingDegrees = 12f * sin(p * PI * 6.0).toFloat(),
                    legTuckDegrees = 9f * sin(p * PI * 3.0).toFloat().coerceAtLeast(0f),
                )
            }
        }
    }
}
