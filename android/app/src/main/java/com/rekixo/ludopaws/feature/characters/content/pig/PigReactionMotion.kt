package com.rekixo.ludopaws.feature.characters.content.pig

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.shared.*

object PigReactionMotion : CharacterReactionProfile {
    override val durationScale = 1.08f

    override fun sample(cue: AnimationCue, frame: CharacterReactionFrame): LudoPaws3DReactionPose = with(frame) {
        when (cue) {
            AnimationCue.IDLE -> LudoPaws3DReactionPose(headTiltDegrees = 5f * wave, secondaryAppendageDegrees = 10f * wave)
            AnimationCue.EXCITED, AnimationCue.HAPPY, AnimationCue.HOME -> LudoPaws3DReactionPose(
                liftY = .17f * bounce, headTiltDegrees = 7f * wave,
                primaryAppendageDegrees = 16f * fast * arc, secondaryAppendageDegrees = 25f * wave, jawOpen = .32f * arc,
            )
            AnimationCue.VICTORY -> LudoPaws3DReactionPose(
                liftY = .20f * bounce, bodyYawDegrees = 360f * p,
                primaryAppendageDegrees = 18f * wave, secondaryAppendageDegrees = 30f * fast * arc, jawOpen = .45f * arc,
            )
            AnimationCue.CAPTURE -> LudoPaws3DReactionPose(
                liftY = .12f * arc, neckPitchDegrees = -7f * arc,
                secondaryAppendageDegrees = 20f * wave, jawOpen = .30f * arc,
            )
            AnimationCue.CAPTURED -> LudoPaws3DReactionPose(
                liftY = .09f * arc, headTiltDegrees = 12f * wave,
                primaryAppendageDegrees = -15f * arc, jawOpen = .45f * arc,
            )
            AnimationCue.SAD, AnimationCue.DEFEAT -> LudoPaws3DReactionPose(
                headTiltDegrees = 10f * arc, primaryAppendageDegrees = 18f * arc,
                neckPitchDegrees = 12f * arc, secondaryAppendageDegrees = -12f * arc,
            )
            AnimationCue.ANGRY -> LudoPaws3DReactionPose(
                bodyYawDegrees = 8f * fast * (1f - p), neckPitchDegrees = 6f * arc, jawOpen = .28f * arc,
            )
            AnimationCue.NERVOUS -> LudoPaws3DReactionPose(
                headTiltDegrees = 9f * wave, primaryAppendageDegrees = 10f * fast * (1f - p),
            )
            AnimationCue.SAFE -> LudoPaws3DReactionPose(headTiltDegrees = 5f * wave, secondaryAppendageDegrees = 12f * wave)
        }
    }
}
