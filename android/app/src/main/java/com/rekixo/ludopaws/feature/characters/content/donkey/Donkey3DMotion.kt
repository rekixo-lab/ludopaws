package com.rekixo.ludopaws.feature.characters.content.donkey

import com.rekixo.ludopaws.feature.characters.shared.CharacterHopDynamics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class Donkey3DMotion {
    IDLE,
    HOP,
    HOME,
}

data class Donkey3DPose(
    val liftY: Float = 0f,
    val stridePhase: Float = 0f,
    val strideWeight: Float = 0f,
    val bodyRollDegrees: Float = 0f,
    val bodyYawDegrees: Float = 0f,
    val headTiltDegrees: Float = 0f,
    val earTwitchDegrees: Float = 0f,
    val tailSwayDegrees: Float = 0f,
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

/** Pure, testable motion timeline for the code-generated 3D donkey pawn. */
object Donkey3DMotionTimeline {
    const val IDLE_DURATION_MILLIS = 2_400L
    const val HOP_DURATION_MILLIS = 760L
    const val HOME_DURATION_MILLIS = 1_550L

    fun stepTravelProgress(progress: Float) = CharacterHopDynamics.travel(progress)

    fun durationMillis(
        motion: Donkey3DMotion,
    ): Long =
        when (motion) {
            Donkey3DMotion.IDLE -> IDLE_DURATION_MILLIS
            Donkey3DMotion.HOP -> HOP_DURATION_MILLIS
            Donkey3DMotion.HOME -> HOME_DURATION_MILLIS
        }

    fun sample(
        motion: Donkey3DMotion,
        progress: Float,
    ): Donkey3DPose {
        val p = progress.coerceIn(0f, 1f)
        fun smooth(start: Float, end: Float): Float {
            val t = ((p - start) / (end - start)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
        fun pulse(a: Float, b: Float, c: Float, d: Float) = smooth(a, b) * (1f - smooth(c, d))
        return when (motion) {
            Donkey3DMotion.IDLE -> {
                val wave = sin(p * PI * 2.0).toFloat()
                val tail = sin(p * PI * 2.0).toFloat()*.75f + sin(p*PI*4.0).toFloat()*.34f
                Donkey3DPose(
                    liftY = 0f,
                    headTiltDegrees = 2.5f * wave,
                    earTwitchDegrees = 3.5f * sin(p*PI*4.0).toFloat(),
                    neckPitchDegrees = 1.5f * -wave,
                    tailSwayDegrees = 24f * tail,
                )
            }

            Donkey3DMotion.HOP -> {
                val flight = stepTravelProgress(p)
                val arc = sin(flight * PI).toFloat().coerceAtLeast(0f)
                val stride = sin(flight * PI * 2.0).toFloat()
                val preload = pulse(0f,.08f,.12f,.20f)
                val land = pulse(.80f,.87f,.90f,1f)
                Donkey3DPose(
                    liftY = 0f,
                    stridePhase = flight,
                    strideWeight = sin(flight*PI).toFloat().coerceAtLeast(0f).let { kotlin.math.sqrt(it) },
                    bodyRollDegrees = 1.8f * stride,
                    bodyCrouch = .035f * preload + .035f * land,
                    bodyPitchDegrees = -3.5f * stride,
                    neckPitchDegrees = 4f * sin((flight*2f-.12f)*PI).toFloat()*arc,
                    frontLegTuckDegrees = 0f,
                    rearLegTuckDegrees = 0f,
                    frontLegSwingDegrees = 26f * stride,
                    rearLegSwingDegrees = -26f * stride,
                    headTiltDegrees = -3f * arc,
                    earTwitchDegrees = -8f * arc + 4f * stride,
                    tailSwayDegrees = 16f * sin(p * PI * 3.0).toFloat(),
                )
            }

            Donkey3DMotion.HOME -> {
                val bounce =
                    abs(sin(p * PI * 3.0).toFloat()) *
                        (1f - p * 0.30f)
                Donkey3DPose(
                    liftY = 0.30f * bounce,
                    bodyYawDegrees = 360f * p,
                    headTiltDegrees = 7f * sin(p * PI * 2.0).toFloat(),
                    earTwitchDegrees = 12f * sin(p * PI).toFloat().coerceAtLeast(0f),
                    tailSwayDegrees = 50f * sin(p * PI * 6.0).toFloat(),
                    legSwingDegrees = 12f * sin(p * PI * 6.0).toFloat(),
                    legTuckDegrees = 9f * sin(p * PI * 3.0).toFloat().coerceAtLeast(0f),
                )
            }
        }
    }
}
