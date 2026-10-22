package com.rekixo.ludopaws.feature.characters.content.cat

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.shared.*

object CatReactionMotion : CharacterReactionProfile {
    override val durationScale = .98f
    override fun sample(cue: AnimationCue, frame: CharacterReactionFrame): LudoPaws3DReactionPose = with(frame) {
        val reaction = when (cue) {
            AnimationCue.IDLE -> LudoPaws3DReactionPose(headTiltDegrees = 5f * wave, primaryAppendageDegrees = 4f * fast, secondaryAppendageDegrees = 28f * wave)
            AnimationCue.EXCITED -> LudoPaws3DReactionPose(liftY = .16f * arc, headTiltDegrees = -5f * arc, primaryAppendageDegrees = 9f * arc, secondaryAppendageDegrees = 26f * fast)
            AnimationCue.HAPPY, AnimationCue.SAFE -> LudoPaws3DReactionPose(liftY = .07f * arc, bodyYawDegrees = 5f * wave, headTiltDegrees = 6f * wave, primaryAppendageDegrees = 5f * fast, secondaryAppendageDegrees = 32f * wave)
            AnimationCue.CAPTURE -> LudoPaws3DReactionPose(liftY = .12f * arc, bodyYawDegrees = 14f * wave, headTiltDegrees = -7f * arc, primaryAppendageDegrees = 8f * arc, secondaryAppendageDegrees = 39f * fast, scale = 1f + .035f * arc)
            AnimationCue.CAPTURED -> LudoPaws3DReactionPose(liftY = .10f * arc, bodyYawDegrees = 12f * fast * (1f - p), headTiltDegrees = 11f * wave, primaryAppendageDegrees = -9f * arc, secondaryAppendageDegrees = 14f * fast, scale = 1f - .04f * arc)
            AnimationCue.SAD, AnimationCue.DEFEAT -> LudoPaws3DReactionPose(liftY = -.04f * arc, headTiltDegrees = 9f * arc, primaryAppendageDegrees = -7f * arc, secondaryAppendageDegrees = 13f * wave, scale = 1f - .025f * arc)
            AnimationCue.ANGRY -> LudoPaws3DReactionPose(bodyYawDegrees = 5f * fast * (1f - p), headTiltDegrees = -6f * arc, primaryAppendageDegrees = -8f * arc, secondaryAppendageDegrees = 26f * fast)
            AnimationCue.NERVOUS -> LudoPaws3DReactionPose(bodyYawDegrees = 4f * fast * (1f - p), headTiltDegrees = 7f * wave, primaryAppendageDegrees = 7f * fast, secondaryAppendageDegrees = 18f * fast)
            AnimationCue.HOME -> LudoPaws3DReactionPose(liftY = .12f * bounce, headTiltDegrees = 7f * wave, primaryAppendageDegrees = 8f * fast, secondaryAppendageDegrees = 38f * fast, scale = 1f + .025f * arc)
            // Complete a full turn so the final frame matches the starting heading.
            AnimationCue.VICTORY -> LudoPaws3DReactionPose(liftY = .17f * bounce, bodyYawDegrees = 360f * p, headTiltDegrees = 7f * wave, primaryAppendageDegrees = 10f * fast, secondaryAppendageDegrees = 48f * fast, scale = 1f + .035f * arc)
        }
        reaction.copy(
            scale=1f+(reaction.scale-1f)*.45f,
            neckPitchDegrees=when(cue) {
                AnimationCue.SAD,AnimationCue.DEFEAT -> 8f*arc
                AnimationCue.CAPTURE,AnimationCue.ANGRY -> 6f*arc
                AnimationCue.NERVOUS -> -4f*arc
                else -> 0f
            },
            jawOpen=when(cue) {
                AnimationCue.CAPTURED -> .30f*arc
                AnimationCue.ANGRY,AnimationCue.CAPTURE -> .22f*arc
                AnimationCue.EXCITED,AnimationCue.HOME,AnimationCue.VICTORY -> .25f*arc
                AnimationCue.HAPPY -> .12f*arc
                else -> 0f
            },
        )
    }
}
