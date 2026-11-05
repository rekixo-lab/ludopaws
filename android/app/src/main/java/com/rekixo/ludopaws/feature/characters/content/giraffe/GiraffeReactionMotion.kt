package com.rekixo.ludopaws.feature.characters.content.giraffe

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.shared.CharacterReactionFrame
import com.rekixo.ludopaws.feature.characters.shared.CharacterReactionProfile

object GiraffeReactionMotion : CharacterReactionProfile {
    override val durationScale = 1.08f

    override fun sample(cue: AnimationCue, frame: CharacterReactionFrame): LudoPaws3DReactionPose = with(frame) {
        when (cue) {
            AnimationCue.IDLE -> LudoPaws3DReactionPose(headTiltDegrees = 4f * wave, primaryAppendageDegrees = 7f * wave)
            AnimationCue.EXCITED, AnimationCue.HAPPY, AnimationCue.HOME -> LudoPaws3DReactionPose(
                liftY = .16f * bounce, neckPitchDegrees = -8f * arc,
                primaryAppendageDegrees = 14f * wave, secondaryAppendageDegrees = 17f * wave, jawOpen = .25f * arc,
            )
            AnimationCue.VICTORY -> LudoPaws3DReactionPose(
                liftY = .17f * bounce, bodyYawDegrees = 360f * p, neckPitchDegrees = 9f * arc,
                headTiltDegrees = 7f * wave, secondaryAppendageDegrees = 23f * wave, jawOpen = .32f * arc,
            )
            AnimationCue.CAPTURE -> LudoPaws3DReactionPose(
                liftY = .09f * arc, neckPitchDegrees = 14f * arc,
                primaryAppendageDegrees = 9f * arc, secondaryAppendageDegrees = 21f * wave,
            )
            AnimationCue.CAPTURED -> LudoPaws3DReactionPose(
                neckPitchDegrees = -13f * arc, headTiltDegrees = 8f * fast * (1f - p),
                primaryAppendageDegrees = -12f * arc, jawOpen = .35f * arc,
            )
            AnimationCue.SAD, AnimationCue.DEFEAT -> LudoPaws3DReactionPose(
                neckPitchDegrees = 20f * arc, headTiltDegrees = 9f * arc,
                primaryAppendageDegrees = 23f * arc, secondaryAppendageDegrees = -9f * arc,
            )
            AnimationCue.ANGRY -> LudoPaws3DReactionPose(
                neckPitchDegrees = 10f * arc, bodyYawDegrees = 4f * fast * (1f - p),
                primaryAppendageDegrees = -12f * arc,
            )
            AnimationCue.NERVOUS -> LudoPaws3DReactionPose(
                headTiltDegrees = 8f * wave, primaryAppendageDegrees = 15f * fast * (1f - p),
                secondaryAppendageDegrees = 12f * wave,
            )
            AnimationCue.SAFE -> LudoPaws3DReactionPose(
                neckPitchDegrees = 5f * arc, primaryAppendageDegrees = 7f * wave,
            )
        }
    }
}
