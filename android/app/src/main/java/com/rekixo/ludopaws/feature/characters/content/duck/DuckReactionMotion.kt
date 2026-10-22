package com.rekixo.ludopaws.feature.characters.content.duck

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.shared.*

object DuckReactionMotion : CharacterReactionProfile {
    override val durationScale = .94f
    override fun sample(cue: AnimationCue, frame: CharacterReactionFrame): LudoPaws3DReactionPose = with(frame) {
        val reaction = when (cue) {
            AnimationCue.IDLE -> LudoPaws3DReactionPose(liftY = .025f * wave, headTiltDegrees = 7f * wave, primaryAppendageDegrees = 8f * wave)
            AnimationCue.EXCITED, AnimationCue.HAPPY -> LudoPaws3DReactionPose(liftY = .25f * bounce, bodyYawDegrees = 7f * wave, headTiltDegrees = -7f * arc, primaryAppendageDegrees = 38f * arc + 9f * fast, scale = 1f + .04f * arc)
            AnimationCue.SAFE -> LudoPaws3DReactionPose(liftY = .07f * arc, headTiltDegrees = 5f * wave, primaryAppendageDegrees = 11f * arc)
            AnimationCue.CAPTURE -> LudoPaws3DReactionPose(liftY = .18f * arc, bodyYawDegrees = 13f * wave, headTiltDegrees = -8f * arc, primaryAppendageDegrees = 42f * arc, scale = 1f + .045f * arc)
            AnimationCue.CAPTURED -> LudoPaws3DReactionPose(liftY = .12f * arc, bodyYawDegrees = 15f * fast * (1f - p), headTiltDegrees = 12f * wave, primaryAppendageDegrees = 28f * fast * (1f - p), scale = 1f - .045f * arc)
            AnimationCue.SAD, AnimationCue.DEFEAT -> LudoPaws3DReactionPose(liftY = -.045f * arc, headTiltDegrees = 13f * arc, primaryAppendageDegrees = -5f * arc, scale = 1f - .03f * arc)
            AnimationCue.ANGRY -> LudoPaws3DReactionPose(bodyYawDegrees = 10f * fast * (1f - p), headTiltDegrees = -8f * arc, primaryAppendageDegrees = 26f * fast)
            AnimationCue.NERVOUS -> LudoPaws3DReactionPose(bodyYawDegrees = 6f * fast * (1f - p), headTiltDegrees = 10f * wave, primaryAppendageDegrees = 12f * fast)
            AnimationCue.HOME -> LudoPaws3DReactionPose(liftY = .18f * bounce, headTiltDegrees = 8f * wave, primaryAppendageDegrees = 42f * arc + 6f * fast, scale = 1f + .035f * arc)
            AnimationCue.VICTORY -> LudoPaws3DReactionPose(liftY = .23f * bounce, bodyYawDegrees = 360f * p, headTiltDegrees = 8f * wave, primaryAppendageDegrees = 50f * arc + 8f * fast, scale = 1f + .05f * arc)
        }
        reaction.copy(
            scale = 1f+(reaction.scale-1f)*.45f,
            jawOpen = when(cue) {
                AnimationCue.EXCITED, AnimationCue.HAPPY, AnimationCue.HOME, AnimationCue.VICTORY -> .35f*arc
                AnimationCue.CAPTURED -> .28f*arc
                AnimationCue.CAPTURE, AnimationCue.ANGRY -> .22f*arc
                else -> 0f
            },
            neckPitchDegrees = when(cue) {
                AnimationCue.SAD, AnimationCue.DEFEAT -> 9f*arc
                AnimationCue.EXCITED, AnimationCue.VICTORY -> -5f*arc
                AnimationCue.NERVOUS -> -3f*arc
                else -> 0f
            },
        )
    }
}
