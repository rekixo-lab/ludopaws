package com.rekixo.ludopaws.feature.characters.content.horse

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.shared.*

object HorseReactionMotion : CharacterReactionProfile {
    override val durationScale = 1.02f

    override fun sample(cue: AnimationCue, frame: CharacterReactionFrame): LudoPaws3DReactionPose = with(frame) {
        when (cue) {
            AnimationCue.IDLE -> LudoPaws3DReactionPose(headTiltDegrees = 4f * wave, primaryAppendageDegrees = 8f * wave)
            AnimationCue.EXCITED, AnimationCue.HAPPY, AnimationCue.HOME -> LudoPaws3DReactionPose(
                liftY = .18f * bounce, neckPitchDegrees = -7f * arc,
                primaryAppendageDegrees = 12f * arc, secondaryAppendageDegrees = 25f * wave, jawOpen = .35f * arc,
            )
            AnimationCue.VICTORY -> LudoPaws3DReactionPose(
                liftY = .22f * bounce, bodyYawDegrees = 360f * p,
                neckPitchDegrees = -10f * arc, secondaryAppendageDegrees = 30f * wave, jawOpen = .50f * arc,
            )
            AnimationCue.CAPTURE -> LudoPaws3DReactionPose(
                liftY = .13f * arc, headTiltDegrees = -5f * arc,
                primaryAppendageDegrees = 15f * arc, secondaryAppendageDegrees = 20f * wave, jawOpen = .28f * arc,
            )
            AnimationCue.CAPTURED -> LudoPaws3DReactionPose(
                headTiltDegrees = 12f * wave, primaryAppendageDegrees = -18f * arc,
                neckPitchDegrees = -8f * arc, jawOpen = .40f * arc,
            )
            AnimationCue.SAD, AnimationCue.DEFEAT -> LudoPaws3DReactionPose(
                neckPitchDegrees = 13f * arc, headTiltDegrees = 8f * arc,
                primaryAppendageDegrees = -16f * arc, secondaryAppendageDegrees = -10f * arc,
            )
            AnimationCue.ANGRY -> LudoPaws3DReactionPose(
                bodyYawDegrees = 7f * fast * (1f - p), primaryAppendageDegrees = -20f * arc, jawOpen = .25f * arc,
            )
            AnimationCue.NERVOUS -> LudoPaws3DReactionPose(
                headTiltDegrees = 8f * wave, primaryAppendageDegrees = 12f * fast * (1f - p),
                secondaryAppendageDegrees = 15f * wave,
            )
            AnimationCue.SAFE -> LudoPaws3DReactionPose(neckPitchDegrees = -4f * arc, secondaryAppendageDegrees = 12f * wave)
        }
    }
}
