package com.rekixo.ludopaws.feature.characters.content.duck

import com.rekixo.ludopaws.feature.characters.shared.CharacterHopDynamics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class Duck3DMotion {
    IDLE,
    HOP,
    HOME,
}

data class Duck3DPose(
    val liftY: Float = 0f,
    val bodyYawDegrees: Float = 0f,
    val wingFlapDegrees: Float = 0f,
    val headTiltDegrees: Float = 0f,
    val legPaddleDegrees: Float = 0f,
    val legTuckDegrees: Float = 0f,
    val bodyPitchDegrees: Float = 0f,
    val bodyRollDegrees: Float = 0f,
    val neckPitchDegrees: Float = 0f,
    val bodyCrouch: Float = 0f,
)

/**
 * Pure animation timeline for the procedural 3D duck.
 *
 * Keeping the motion curve free of Android/OpenGL dependencies makes it easy to
 * unit-test and later map the same cues to the in-game pawn renderer.
 */
object Duck3DMotionTimeline {
    const val IDLE_DURATION_MILLIS = 2_200L
    const val HOP_DURATION_MILLIS = 720L
    const val HOME_DURATION_MILLIS = 1_350L

    fun stepTravelProgress(progress: Float) = CharacterHopDynamics.travel(progress)

    fun durationMillis(
        motion: Duck3DMotion,
    ): Long =
        when (motion) {
            Duck3DMotion.IDLE -> IDLE_DURATION_MILLIS
            Duck3DMotion.HOP -> HOP_DURATION_MILLIS
            Duck3DMotion.HOME -> HOME_DURATION_MILLIS
        }

    fun sample(
        motion: Duck3DMotion,
        progress: Float,
    ): Duck3DPose {
        val p = progress.coerceIn(0f, 1f)
        fun smooth(a: Float,b: Float): Float {
            val t=((p-a)/(b-a)).coerceIn(0f,1f)
            return t*t*(3f-2f*t)
        }
        fun pulse(a: Float,b: Float,c: Float,d: Float)=smooth(a,b)*(1f-smooth(c,d))
        return when (motion) {
            Duck3DMotion.IDLE -> {
                val wave = sin((p * PI * 2.0)).toFloat()
                Duck3DPose(
                    liftY = 0f,
                    wingFlapDegrees = 3.5f * wave,
                    bodyRollDegrees = 1.2f * wave,
                    neckPitchDegrees = -1.0f * wave,
                    headTiltDegrees = 2.5f * sin((p * PI * 2.0 + PI / 3.0)).toFloat(),
                )
            }

            Duck3DMotion.HOP -> {
                val flight = stepTravelProgress(p)
                val arc = sin(flight*PI).toFloat().coerceAtLeast(0f)
                val flap = sin(p*PI*4.0).toFloat()*arc
                val preload=pulse(0f,.07f,.10f,.16f)
                val land=pulse(.82f,.88f,.91f,1f)
                Duck3DPose(
                    liftY = .62f*4f*flight*(1f-flight),
                    legTuckDegrees = 30f*arc,
                    bodyCrouch = .06f*preload+.055f*land,
                    bodyPitchDegrees = -4f*arc+4f*land,
                    bodyRollDegrees = 2.5f*sin(p*PI*2.0).toFloat()*arc,
                    neckPitchDegrees = 3f*arc-4f*land,
                    wingFlapDegrees = 34f * arc + 7f * flap,
                    headTiltDegrees = -7f * arc,
                    legPaddleDegrees = 42f * sin(p * PI * 2.0).toFloat() * arc,
                )
            }

            Duck3DMotion.HOME -> {
                val celebrationBounce =
                    abs(sin((p * PI * 3.0)).toFloat()) *
                        (1f - p * 0.35f)
                Duck3DPose(
                    liftY = 0.34f * celebrationBounce,
                    legTuckDegrees = 12f*celebrationBounce,
                    bodyRollDegrees = 3f*sin(p*PI*6.0).toFloat()*sin(p*PI).toFloat(),
                    bodyYawDegrees = 360f * p,
                    wingFlapDegrees = 52f * sin((p * PI)).toFloat().coerceAtLeast(0f),
                    headTiltDegrees = 8f * sin((p * PI * 2.0)).toFloat(),
                    legPaddleDegrees = 20f * sin(p * PI * 6.0).toFloat(),
                )
            }
        }
    }
}
