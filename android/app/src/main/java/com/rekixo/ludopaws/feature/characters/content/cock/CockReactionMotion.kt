package com.rekixo.ludopaws.feature.characters.content.cock

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.shared.*

object CockReactionMotion : CharacterReactionProfile {
    override val durationScale = 1.02f

    override fun sample(cue: AnimationCue, frame: CharacterReactionFrame): LudoPaws3DReactionPose = with(frame) {
        when (cue) {
            AnimationCue.IDLE -> LudoPaws3DReactionPose(headTiltDegrees = 6f * wave, neckPitchDegrees = -3f * arc)
            AnimationCue.EXCITED, AnimationCue.HAPPY, AnimationCue.HOME -> LudoPaws3DReactionPose(
                liftY = .18f * bounce, primaryAppendageDegrees = 36f * arc + 7f * fast,
                neckPitchDegrees = -10f * arc, jawOpen = .45f * arc,
            )
            AnimationCue.VICTORY -> LudoPaws3DReactionPose(
                liftY = .22f * bounce, bodyYawDegrees = 360f * p,
                primaryAppendageDegrees = 48f * arc, neckPitchDegrees = -14f * arc, jawOpen = .65f * arc,
            )
            AnimationCue.CAPTURE -> LudoPaws3DReactionPose(
                liftY = .12f * arc, bodyYawDegrees = 10f * wave,
                primaryAppendageDegrees = 30f * arc, neckPitchDegrees = -8f * arc, jawOpen = .32f * arc,
            )
            AnimationCue.CAPTURED -> LudoPaws3DReactionPose(
                liftY = .10f * arc, headTiltDegrees = 14f * wave,
                primaryAppendageDegrees = 25f * fast * (1f - p), jawOpen = .35f * arc,
            )
            AnimationCue.SAD, AnimationCue.DEFEAT -> LudoPaws3DReactionPose(
                headTiltDegrees = 12f * arc, neckPitchDegrees = 14f * arc, primaryAppendageDegrees = -6f * arc,
            )
            AnimationCue.ANGRY -> LudoPaws3DReactionPose(
                bodyYawDegrees = 9f * fast * (1f - p), neckPitchDegrees = 7f * arc,
                primaryAppendageDegrees = 22f * arc, jawOpen = .25f * arc,
            )
            AnimationCue.NERVOUS -> LudoPaws3DReactionPose(
                headTiltDegrees = 10f * wave, primaryAppendageDegrees = 10f * fast * (1f - p),
            )
            AnimationCue.SAFE -> LudoPaws3DReactionPose(headTiltDegrees = 5f * wave, neckPitchDegrees = -6f * arc)
        }
    }
}
