package com.rekixo.ludopaws.feature.characters.content.elephant

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.shared.*

object ElephantReactionMotion : CharacterReactionProfile {
    override val durationScale = 1.16f

    override fun sample(cue: AnimationCue, frame: CharacterReactionFrame): LudoPaws3DReactionPose = with(frame) {
        when (cue) {
            AnimationCue.IDLE -> LudoPaws3DReactionPose(headTiltDegrees = 3f * wave, secondaryAppendageDegrees = 9f * wave)
            AnimationCue.EXCITED, AnimationCue.HAPPY, AnimationCue.HOME -> LudoPaws3DReactionPose(
                liftY = .12f * bounce, primaryAppendageDegrees = 20f * arc,
                secondaryAppendageDegrees = 55f * arc, tertiaryAppendageDegrees = 18f * wave, jawOpen = .25f * arc,
            )
            AnimationCue.VICTORY -> LudoPaws3DReactionPose(
                liftY = .16f * bounce, bodyYawDegrees = 360f * p,
                primaryAppendageDegrees = 28f * arc, secondaryAppendageDegrees = 75f * arc,
                tertiaryAppendageDegrees = 25f * wave, jawOpen = .40f * arc,
            )
            AnimationCue.CAPTURE -> LudoPaws3DReactionPose(
                headTiltDegrees = -5f * arc, primaryAppendageDegrees = 16f * arc,
                secondaryAppendageDegrees = 42f * arc, jawOpen = .25f * arc,
            )
            AnimationCue.CAPTURED -> LudoPaws3DReactionPose(
                headTiltDegrees = 10f * wave, primaryAppendageDegrees = 25f * arc,
                secondaryAppendageDegrees = -16f * arc, jawOpen = .35f * arc,
            )
            AnimationCue.SAD, AnimationCue.DEFEAT -> LudoPaws3DReactionPose(
                headTiltDegrees = 8f * arc, neckPitchDegrees = 10f * arc,
                primaryAppendageDegrees = -12f * arc, secondaryAppendageDegrees = -15f * arc,
            )
            AnimationCue.ANGRY -> LudoPaws3DReactionPose(
                bodyYawDegrees = 6f * fast * (1f - p), primaryAppendageDegrees = 22f * arc,
                secondaryAppendageDegrees = 28f * arc, jawOpen = .25f * arc,
            )
            AnimationCue.NERVOUS -> LudoPaws3DReactionPose(
                headTiltDegrees = 7f * wave, primaryAppendageDegrees = 8f * fast * (1f - p),
                secondaryAppendageDegrees = 18f * arc,
            )
            AnimationCue.SAFE -> LudoPaws3DReactionPose(headTiltDegrees = 4f * wave, secondaryAppendageDegrees = 16f * arc)
        }
    }
}
