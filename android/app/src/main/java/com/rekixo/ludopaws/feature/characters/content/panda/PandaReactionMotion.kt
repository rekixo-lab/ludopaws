package com.rekixo.ludopaws.feature.characters.content.panda

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.shared.*

object PandaReactionMotion : CharacterReactionProfile {
    override val durationScale = 1.12f

    override fun sample(cue: AnimationCue, frame: CharacterReactionFrame): LudoPaws3DReactionPose = with(frame) {
        when (cue) {
            AnimationCue.IDLE -> LudoPaws3DReactionPose(headTiltDegrees = 4f * wave, primaryAppendageDegrees = 5f * wave)
            AnimationCue.EXCITED, AnimationCue.HAPPY, AnimationCue.HOME -> LudoPaws3DReactionPose(
                liftY = .16f * bounce, primaryAppendageDegrees = 30f * arc,
                headTiltDegrees = 6f * wave, jawOpen = .18f * arc,
            )
            AnimationCue.VICTORY -> LudoPaws3DReactionPose(
                liftY = .20f * bounce, bodyYawDegrees = 360f * p,
                primaryAppendageDegrees = 40f * arc, headTiltDegrees = 7f * wave,
            )
            AnimationCue.CAPTURE -> LudoPaws3DReactionPose(liftY = .12f * arc, neckPitchDegrees = -7f * arc)
            AnimationCue.CAPTURED -> LudoPaws3DReactionPose(headTiltDegrees = 13f * wave, primaryAppendageDegrees = 22f * arc, jawOpen = .25f * arc)
            AnimationCue.SAD, AnimationCue.DEFEAT -> LudoPaws3DReactionPose(neckPitchDegrees = 12f * arc, headTiltDegrees = 8f * arc)
            AnimationCue.ANGRY -> LudoPaws3DReactionPose(bodyYawDegrees = 6f * fast * (1f - p), primaryAppendageDegrees = 14f * arc)
            AnimationCue.NERVOUS -> LudoPaws3DReactionPose(headTiltDegrees = 7f * wave, primaryAppendageDegrees = 9f * fast * (1f - p))
            AnimationCue.SAFE -> LudoPaws3DReactionPose(headTiltDegrees = 5f * wave, primaryAppendageDegrees = 8f * arc)
        }
    }
}
