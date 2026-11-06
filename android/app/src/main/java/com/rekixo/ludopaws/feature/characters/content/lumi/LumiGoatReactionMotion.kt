package com.rekixo.ludopaws.feature.characters.content.lumi

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.shared.CharacterReactionFrame
import com.rekixo.ludopaws.feature.characters.shared.CharacterReactionProfile

/** A cautious white kid. Small nods and ear turns lead its reactions. */
internal object LumiGoatReactionMotion : CharacterReactionProfile {
    override val durationScale = 1.12f

    override fun sample(cue: AnimationCue, frame: CharacterReactionFrame): LudoPaws3DReactionPose = with(frame) {
        when (cue) {
            AnimationCue.IDLE -> LudoPaws3DReactionPose(headTiltDegrees = 5f * wave, primaryAppendageDegrees = 9f * wave)
            AnimationCue.EXCITED, AnimationCue.HAPPY, AnimationCue.HOME -> LudoPaws3DReactionPose(
                liftY = .16f * bounce, neckPitchDegrees = -6f * arc, headTiltDegrees = 7f * wave,
                primaryAppendageDegrees = 21f * arc, tertiaryAppendageDegrees = 23f * wave, jawOpen = .25f * arc,
            )
            AnimationCue.VICTORY -> LudoPaws3DReactionPose(
                liftY = .21f * bounce, bodyYawDegrees = 360f * p, neckPitchDegrees = -10f * arc,
                primaryAppendageDegrees = 15f * wave, tertiaryAppendageDegrees = 27f * wave, jawOpen = .36f * arc,
            )
            AnimationCue.CAPTURE -> LudoPaws3DReactionPose(
                liftY = .09f * arc, neckPitchDegrees = 9f * arc, headTiltDegrees = -6f * arc,
                primaryAppendageDegrees = 14f * arc, tertiaryAppendageDegrees = 17f * wave,
            )
            AnimationCue.CAPTURED -> LudoPaws3DReactionPose(
                neckPitchDegrees = -12f * arc, primaryAppendageDegrees = -18f * arc,
                headTiltDegrees = 8f * wave, jawOpen = .32f * arc,
            )
            AnimationCue.SAD, AnimationCue.DEFEAT -> LudoPaws3DReactionPose(
                neckPitchDegrees = 15f * arc, headTiltDegrees = 9f * arc,
                primaryAppendageDegrees = -19f * arc, tertiaryAppendageDegrees = -8f * arc,
            )
            AnimationCue.ANGRY -> LudoPaws3DReactionPose(
                neckPitchDegrees = 10f * arc, primaryAppendageDegrees = -13f * arc,
                bodyYawDegrees = 5f * fast * (1f - p), jawOpen = .23f * arc,
            )
            AnimationCue.NERVOUS -> LudoPaws3DReactionPose(
                headTiltDegrees = 8f * wave, primaryAppendageDegrees = 17f * fast * (1f - p),
                tertiaryAppendageDegrees = 9f * wave,
            )
            AnimationCue.SAFE -> LudoPaws3DReactionPose(neckPitchDegrees = 4f * arc, primaryAppendageDegrees = 11f * wave)
        }
    }
}
