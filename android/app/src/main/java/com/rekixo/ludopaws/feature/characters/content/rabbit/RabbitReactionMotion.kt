package com.rekixo.ludopaws.feature.characters.content.rabbit

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.shared.*

object RabbitReactionMotion : CharacterReactionProfile {
    override val durationScale = .93f

    override fun sample(cue: AnimationCue, frame: CharacterReactionFrame): LudoPaws3DReactionPose = with(frame) {
        when (cue) {
            AnimationCue.IDLE -> LudoPaws3DReactionPose(headTiltDegrees = 6f * wave, primaryAppendageDegrees = 7f * wave)
            AnimationCue.EXCITED, AnimationCue.HAPPY, AnimationCue.HOME -> LudoPaws3DReactionPose(
                liftY = .23f * bounce, primaryAppendageDegrees = 18f * wave,
                secondaryAppendageDegrees = 12f * fast * arc, headTiltDegrees = 7f * wave, jawOpen = .20f * arc,
            )
            AnimationCue.VICTORY -> LudoPaws3DReactionPose(
                liftY = .27f * bounce, bodyYawDegrees = 360f * p,
                primaryAppendageDegrees = 22f * wave, secondaryAppendageDegrees = 15f * wave, jawOpen = .25f * arc,
            )
            AnimationCue.CAPTURE -> LudoPaws3DReactionPose(
                liftY = .17f * arc, neckPitchDegrees = -6f * arc, primaryAppendageDegrees = -12f * arc,
            )
            AnimationCue.CAPTURED -> LudoPaws3DReactionPose(
                liftY = .10f * arc, headTiltDegrees = 12f * wave,
                primaryAppendageDegrees = 30f * arc, jawOpen = .30f * arc,
            )
            AnimationCue.SAD, AnimationCue.DEFEAT -> LudoPaws3DReactionPose(
                headTiltDegrees = 9f * arc, neckPitchDegrees = 11f * arc, primaryAppendageDegrees = 38f * arc,
            )
            AnimationCue.ANGRY -> LudoPaws3DReactionPose(
                bodyYawDegrees = 8f * fast * (1f - p), primaryAppendageDegrees = 25f * arc, jawOpen = .15f * arc,
            )
            AnimationCue.NERVOUS -> LudoPaws3DReactionPose(
                headTiltDegrees = 9f * wave, primaryAppendageDegrees = 15f * fast * (1f - p),
            )
            AnimationCue.SAFE -> LudoPaws3DReactionPose(headTiltDegrees = 5f * wave, primaryAppendageDegrees = -7f * arc)
        }
    }
}
