package com.rekixo.ludopaws.feature.characters.content.monkey

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.shared.*

object MonkeyReactionMotion : CharacterReactionProfile {
    override val durationScale = .94f

    override fun sample(cue: AnimationCue, frame: CharacterReactionFrame): LudoPaws3DReactionPose = with(frame) {
        when (cue) {
            AnimationCue.IDLE -> LudoPaws3DReactionPose(headTiltDegrees = 4f * wave, primaryAppendageDegrees = 5f * wave)
            AnimationCue.EXCITED, AnimationCue.HAPPY, AnimationCue.HOME -> LudoPaws3DReactionPose(
                liftY = .28f * bounce, primaryAppendageDegrees = 85f * arc + 18f * wave,
                secondaryAppendageDegrees = 24f * wave, tertiaryAppendageDegrees = 25f * wave,
                headTiltDegrees = 10f * wave, jawOpen = .24f * arc,
            )
            AnimationCue.VICTORY -> LudoPaws3DReactionPose(
                liftY = .28f * bounce, bodyYawDegrees = 360f * p,
                primaryAppendageDegrees = 110f * arc, secondaryAppendageDegrees = 32f * fast * arc, tertiaryAppendageDegrees = 40f * wave,
                headTiltDegrees = 10f * wave, jawOpen = .24f * arc,
            )
            AnimationCue.CAPTURE -> LudoPaws3DReactionPose(liftY = .12f * arc, neckPitchDegrees = -7f * arc)
            AnimationCue.CAPTURED -> LudoPaws3DReactionPose(headTiltDegrees = 13f * wave, primaryAppendageDegrees = 70f * arc, tertiaryAppendageDegrees = -25f * arc, jawOpen = .25f * arc)
            AnimationCue.SAD, AnimationCue.DEFEAT -> LudoPaws3DReactionPose(neckPitchDegrees = 18f * arc, headTiltDegrees = 12f * arc, primaryAppendageDegrees = -15f * arc)
            AnimationCue.ANGRY -> LudoPaws3DReactionPose(bodyYawDegrees = 6f * fast * (1f - p), primaryAppendageDegrees = 14f * arc)
            AnimationCue.NERVOUS -> LudoPaws3DReactionPose(headTiltDegrees = 7f * wave, primaryAppendageDegrees = 9f * fast * (1f - p))
            AnimationCue.SAFE -> LudoPaws3DReactionPose(headTiltDegrees = 5f * wave, primaryAppendageDegrees = 8f * arc)
        }
    }
}
