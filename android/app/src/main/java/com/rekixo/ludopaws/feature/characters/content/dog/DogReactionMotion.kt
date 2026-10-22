package com.rekixo.ludopaws.feature.characters.content.dog

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.shared.*

object DogReactionMotion : CharacterReactionProfile {
    override val durationScale = 1f
    override fun sample(cue: AnimationCue, frame: CharacterReactionFrame): LudoPaws3DReactionPose = with(frame) {
        val reaction = when (cue) {
            AnimationCue.IDLE -> LudoPaws3DReactionPose(headTiltDegrees = 7f * wave, primaryAppendageDegrees = 5f * -wave, secondaryAppendageDegrees = 30f * fast)
            AnimationCue.EXCITED -> LudoPaws3DReactionPose(liftY = .24f * bounce, headTiltDegrees = -7f * arc, primaryAppendageDegrees = 15f * arc, secondaryAppendageDegrees = 42f * fast, scale = 1f + .045f * arc)
            AnimationCue.HAPPY, AnimationCue.SAFE -> LudoPaws3DReactionPose(liftY = .13f * arc, bodyYawDegrees = 8f * wave, headTiltDegrees = 6f * wave, primaryAppendageDegrees = 7f * arc, secondaryAppendageDegrees = 34f * fast)
            AnimationCue.CAPTURE -> LudoPaws3DReactionPose(liftY = .19f * arc, bodyYawDegrees = 18f * wave, headTiltDegrees = -10f * arc, primaryAppendageDegrees = 12f * arc, secondaryAppendageDegrees = 46f * fast, scale = 1f + .055f * arc)
            AnimationCue.CAPTURED -> LudoPaws3DReactionPose(liftY = .12f * arc, bodyYawDegrees = 16f * fast * (1f - p), headTiltDegrees = 13f * wave, primaryAppendageDegrees = -9f * arc, secondaryAppendageDegrees = 8f * wave, scale = 1f - .055f * arc)
            AnimationCue.SAD, AnimationCue.DEFEAT -> LudoPaws3DReactionPose(liftY = -.055f * arc, headTiltDegrees = 13f * arc, primaryAppendageDegrees = -8f * arc, secondaryAppendageDegrees = 7f * wave, scale = 1f - .035f * arc)
            AnimationCue.ANGRY -> LudoPaws3DReactionPose(liftY = .045f * bounce, bodyYawDegrees = 11f * fast * (1f - p), headTiltDegrees = -8f * arc, primaryAppendageDegrees = 7f * fast, secondaryAppendageDegrees = 16f * fast)
            AnimationCue.NERVOUS -> LudoPaws3DReactionPose(bodyYawDegrees = 6f * fast * (1f - p), headTiltDegrees = 8f * wave, primaryAppendageDegrees = 5f * fast, secondaryAppendageDegrees = 10f * wave)
            AnimationCue.HOME -> LudoPaws3DReactionPose(liftY = .16f * bounce, headTiltDegrees = 7f * wave, primaryAppendageDegrees = 11f * arc, secondaryAppendageDegrees = 44f * fast, scale = 1f + .035f * arc)
            AnimationCue.VICTORY -> LudoPaws3DReactionPose(liftY = .22f * bounce, bodyYawDegrees = 360f * p, headTiltDegrees = 7f * wave, primaryAppendageDegrees = 13f * arc, secondaryAppendageDegrees = 52f * fast, scale = 1f + .05f * arc)
        }
        reaction.copy(
            scale = 1f + (reaction.scale - 1f) * .45f,
            neckPitchDegrees = when (cue) {
                AnimationCue.SAD, AnimationCue.DEFEAT -> 10f * arc
                AnimationCue.CAPTURE, AnimationCue.ANGRY -> 7f * arc
                AnimationCue.EXCITED, AnimationCue.HAPPY, AnimationCue.VICTORY -> -4f * arc
                else -> 0f
            },
            jawOpen = when (cue) {
                AnimationCue.EXCITED, AnimationCue.HOME, AnimationCue.VICTORY -> .35f * arc
                AnimationCue.HAPPY, AnimationCue.SAFE -> .18f * arc
                AnimationCue.CAPTURED -> .26f * arc
                AnimationCue.CAPTURE, AnimationCue.ANGRY -> .15f * arc
                else -> 0f
            },
        )
    }
}
