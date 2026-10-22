package com.rekixo.ludopaws.feature.characters.content.goat

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.PI
import kotlin.math.sin

object GoatReactionMotion : CharacterReactionProfile {
    override val durationScale = 1.04f
    override fun sample(cue: AnimationCue, frame: CharacterReactionFrame): LudoPaws3DReactionPose = with(frame) {
        // Wind-up in the first 30% of a cue: the goat rocks back before it lunges.
        val wind = sin((p / .3f).coerceIn(0f, 1f) * PI.toFloat())
        val reaction = when (cue) {
            AnimationCue.IDLE -> LudoPaws3DReactionPose(headTiltDegrees = 10f * wave, primaryAppendageDegrees = 7f * fast, secondaryAppendageDegrees = 6f * -wave, tertiaryAppendageDegrees = 9f * fast)
            AnimationCue.EXCITED, AnimationCue.HAPPY -> LudoPaws3DReactionPose(liftY = .22f * bounce, headTiltDegrees = -6f * arc + 4f * wave, primaryAppendageDegrees = 14f * arc, secondaryAppendageDegrees = 13f * wave, tertiaryAppendageDegrees = 18f * fast)
            AnimationCue.SAFE -> LudoPaws3DReactionPose(liftY = .08f * arc, headTiltDegrees = 7f * wave, primaryAppendageDegrees = 5f * wave, secondaryAppendageDegrees = 5f * -wave)
            AnimationCue.CAPTURE -> LudoPaws3DReactionPose(liftY = .13f * arc, bodyYawDegrees = 13f * wave, headTiltDegrees = -16f * arc, primaryAppendageDegrees = 11f * arc, secondaryAppendageDegrees = 18f * arc, tertiaryAppendageDegrees = 16f * fast, scale = 1f + .04f * arc)
            AnimationCue.CAPTURED -> LudoPaws3DReactionPose(liftY = .11f * arc, bodyYawDegrees = 13f * fast * (1f - p), headTiltDegrees = 11f * wave, primaryAppendageDegrees = -7f * arc, secondaryAppendageDegrees = 15f * wave, tertiaryAppendageDegrees = -10f * arc, scale = 1f - .045f * arc)
            AnimationCue.SAD, AnimationCue.DEFEAT -> LudoPaws3DReactionPose(liftY = -.05f * arc, headTiltDegrees = 12f * arc, primaryAppendageDegrees = -7f * arc, secondaryAppendageDegrees = 10f * arc, tertiaryAppendageDegrees = -7f * arc)
            AnimationCue.ANGRY -> LudoPaws3DReactionPose(liftY = .035f * bounce, bodyYawDegrees = 7f * fast * (1f - p), headTiltDegrees = -12f * arc, primaryAppendageDegrees = 8f * fast, secondaryAppendageDegrees = 13f * fast, tertiaryAppendageDegrees = 11f * fast)
            AnimationCue.NERVOUS -> LudoPaws3DReactionPose(bodyYawDegrees = 5f * fast * (1f - p), headTiltDegrees = 9f * wave, primaryAppendageDegrees = 8f * fast, secondaryAppendageDegrees = 7f * wave)
            AnimationCue.HOME -> LudoPaws3DReactionPose(liftY = .15f * bounce, headTiltDegrees = 8f * wave, primaryAppendageDegrees = 10f * fast, secondaryAppendageDegrees = 17f * wave, tertiaryAppendageDegrees = 22f * fast, scale = 1f + .03f * arc)
            AnimationCue.VICTORY -> LudoPaws3DReactionPose(liftY = .20f * bounce, bodyYawDegrees = 360f * p, headTiltDegrees = 8f * wave, primaryAppendageDegrees = 12f * fast, secondaryAppendageDegrees = 20f * wave, tertiaryAppendageDegrees = 26f * fast, scale = 1f + .04f * arc)
        }
        reaction.copy(
            scale = 1f + (reaction.scale - 1f) * .40f,
            neckPitchDegrees = when (cue) {
                AnimationCue.CAPTURE -> 18f * arc - 8f * wind
                AnimationCue.CAPTURED -> -9f * arc
                AnimationCue.ANGRY -> 16f * arc + 2f * fast * arc
                AnimationCue.SAD, AnimationCue.DEFEAT -> 12f * arc
                AnimationCue.NERVOUS -> -5f * arc
                AnimationCue.HAPPY, AnimationCue.SAFE -> -4f * arc
                AnimationCue.VICTORY -> -8f * arc
                else -> -2f * arc
            },
            jawOpen = when (cue) {
                AnimationCue.EXCITED, AnimationCue.HOME, AnimationCue.VICTORY -> .30f * arc * (.75f + .25f * fast)
                AnimationCue.CAPTURED -> .34f * arc
                AnimationCue.CAPTURE, AnimationCue.ANGRY -> .20f * arc
                AnimationCue.HAPPY -> .10f * arc
                AnimationCue.SAD, AnimationCue.DEFEAT -> .06f * arc * (.5f + .5f * fast)
                else -> 0f
            },
            bodyPitchDegrees = when (cue) {
                AnimationCue.CAPTURE -> 8f * arc - 6f * wind
                AnimationCue.CAPTURED -> -5f * arc
                AnimationCue.ANGRY -> 3f * arc
                AnimationCue.SAD, AnimationCue.DEFEAT -> 2f * arc
                else -> 0f
            },
        )
    }
}
