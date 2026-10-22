package com.rekixo.ludopaws.feature.characters.shared

import com.rekixo.ludopaws.feature.characters.rendering.CharacterHopPlayback
import com.rekixo.ludopaws.feature.characters.rendering.CharacterMotion
import com.rekixo.ludopaws.feature.characters.rendering.CharacterPose
import kotlin.math.PI
import kotlin.math.sin

/** Shared cell-hop weight, consumed by gameplay and real GPU previews alike. */
internal object CharacterHopDynamics {
    const val TAKEOFF = .18f
    const val CONTACT = .78f

    private const val CONTACT_SQUASH = .17f
    private const val CONTACT_CROUCH = .085f

    private fun phase(progress: Float) = ((progress - TAKEOFF) / (CONTACT - TAKEOFF)).coerceIn(0f, 1f)

    // Zero velocity AND acceleration at the planted endpoints.
    fun travel(progress: Float): Float = smoother(phase(progress))

    private fun smoother(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        val u = minOf(t, 1f - t)
        val shaped = u * u * u * (10f + u * (-15f + 6f * u))
        return if (t <= .5f) shaped else 1f - shaped
    }

    private fun ease(progress: Float, start: Float, end: Float): Float =
        smoother((progress - start) / (end - start))

    fun apply(
        base: CharacterPose,
        motion: CharacterMotion,
        progress: Float,
        height: Float,
        playback: CharacterHopPlayback = CharacterHopPlayback.ISOLATED,
    ): CharacterPose {
        if (motion != CharacterMotion.HOP) return base
        val p = progress.coerceIn(0f, 1f)
        if (playback == CharacterHopPlayback.SETTLE) {
            // The renderer supplies neutral HOP anatomy at 1. This is the one final
            // recovery, in the route's existing settle tail, rather than another hop.
            val contact = 1f - smoother(p)
            return base.copy(
                liftY = 0f,
                bodyScaleY = 1f - CONTACT_SQUASH * contact,
                bodyCrouch = CONTACT_CROUCH * contact,
                bodyPitchDegrees = 3f * contact,
                neckPitchDegrees = -3f * contact,
                headTiltDegrees = 0f,
                bodyRollDegrees = 0f,
                primaryAppendageDegrees = 0f,
                secondaryAppendageDegrees = 0f,
                tertiaryAppendageDegrees = 0f,
                legSwingDegrees = 0f,
                legTuckDegrees = 0f,
                legPaddleDegrees = 0f,
                frontLegTuckDegrees = base.frontLegTuckDegrees?.let { 0f },
                rearLegTuckDegrees = base.rearLegTuckDegrees?.let { 0f },
                frontLegSwingDegrees = base.frontLegSwingDegrees?.let { 0f },
                rearLegSwingDegrees = base.rearLegSwingDegrees?.let { 0f },
                strideWeight = 0f,
            )
        }

        val chaining = playback == CharacterHopPlayback.CHAIN
        val preload = if (chaining) 0f else ease(p, 0f, .14f) * (1f - ease(p, .14f, .36f))
        // A connected step begins in the preceding landing crouch. It releases once;
        // the old stand-up / crouch-again pair caused a second bob at every cell.
        val carriedContact = if (chaining) 1f - ease(p, 0f, .34f) else 0f
        val impact = if (playback == CharacterHopPlayback.ISOLATED) {
            ease(p, .62f, .80f) * (1f - ease(p, .80f, 1f))
        } else {
            ease(p, .62f, 1f)
        }
        val contact = carriedContact + impact
        val extension = ease(p, .18f, .38f) * (1f - ease(p, .48f, .68f))
        val flight = phase(p)
        val arc = sin(flight * PI).toFloat().coerceAtLeast(0f)
        val frontRelease = ease(p, .12f, .28f) * (1f - ease(p, .58f, CONTACT))
        val rearRelease = ease(p, .20f, .38f) * (1f - ease(p, .68f, .88f))
        // Species landing pulses must not add a second impact to the shared one.
        val anatomy = ease(p, .10f, .30f) * (1f - ease(p, .54f, CONTACT))
        val appendages = ease(p, 0f, .14f) * (1f - ease(p, .80f, 1f))
        return base.copy(
            liftY = height * arc * arc,
            bodyScaleY = 1f - .16f * preload - CONTACT_SQUASH * contact + .035f * extension,
            bodyCrouch = .085f * preload + CONTACT_CROUCH * contact,
            bodyPitchDegrees = base.bodyPitchDegrees * anatomy + 4f * preload + 3f * contact - 2f * extension,
            neckPitchDegrees = base.neckPitchDegrees * anatomy + 3f * preload - 3f * contact,
            headTiltDegrees = base.headTiltDegrees * anatomy,
            bodyRollDegrees = base.bodyRollDegrees * anatomy,
            primaryAppendageDegrees = base.primaryAppendageDegrees * appendages,
            secondaryAppendageDegrees = base.secondaryAppendageDegrees * appendages,
            tertiaryAppendageDegrees = base.tertiaryAppendageDegrees * appendages,
            legSwingDegrees = base.legSwingDegrees * anatomy,
            legPaddleDegrees = base.legPaddleDegrees * anatomy,
            legTuckDegrees = base.legTuckDegrees * frontRelease,
            frontLegTuckDegrees = base.frontLegTuckDegrees?.times(frontRelease),
            rearLegTuckDegrees = base.rearLegTuckDegrees?.times(rearRelease),
            frontLegSwingDegrees = base.frontLegSwingDegrees?.times(frontRelease),
            rearLegSwingDegrees = base.rearLegSwingDegrees?.times(rearRelease),
            strideWeight = base.strideWeight * ease(p, .16f, .32f) * (1f - ease(p, .62f, .88f)),
        )
    }
}
