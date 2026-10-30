package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.shared.CharacterLivingBehavior
import org.junit.Assert.*
import org.junit.Test

class LudoPawsPawnReactionVariationTest {
    @Test fun groupCelebrationsAreStaggeredAndEmotionallyConsistent() {
        for (seed in listOf(Int.MIN_VALUE, -100, 0, 32, Int.MAX_VALUE)) {
            val choices = (0..3).map { LudoPawsPawnReactionVariation.choose(AnimationCue.VICTORY, it, seed, true) }
            assertEquals(4, choices.map { it.delayMillis }.toSet().size)
            assertEquals(setOf(0L, 90L, 180L, 270L), choices.map { it.delayMillis }.toSet())
            assertEquals(3, choices.map { it.cue }.toSet().size)
            val loss = (0..3).map { LudoPawsPawnReactionVariation.choose(AnimationCue.DEFEAT, it, seed, true) }
            assertTrue(loss.all { it.cue == AnimationCue.SAD || it.cue == AnimationCue.DEFEAT })
            assertEquals(choices, (0..3).map { LudoPawsPawnReactionVariation.choose(AnimationCue.VICTORY, it, seed, true) })
        }
    }

    @Test fun tokenSpecificCaptureAndHomeCuesKeepTheirContactTiming() {
        for (cue in AnimationCue.entries) for (token in 0..3) {
            val choice = LudoPawsPawnReactionVariation.choose(cue, token, 271, false)
            assertEquals(cue, choice.cue)
            assertEquals(0L, choice.delayMillis)
            assertEquals(1f, choice.durationScale, 0f)
        }
    }

    @Test fun variantsPreserveFullSpinAndNeutralEndpoints() {
        for (variant in 0..3) {
            for (p in listOf(0f, 1f)) {
                val base = LudoPaws3DReactionPose(bodyYawDegrees = 360f * p)
                val pose = LudoPawsPawnReactionVariation.pose(base, p, variant)
                assertEquals(base.bodyYawDegrees, pose.bodyYawDegrees, 0f)
                assertEquals(0f, pose.headTiltDegrees, .0001f)
                assertEquals(0f, pose.neckPitchDegrees, .0001f)
            }
        }
    }

    @Test fun allCharacterAccentsStayFiniteWithoutMovingFootContactOrScale() {
        for (id in CharacterLivingBehavior.characterIds) for (cue in AnimationCue.entries) {
            for (v in 0..3) for (p in listOf(0f, .2f, .5f, .8f, 1f)) {
                val base = LudoPaws3DReactionMotion.sample(id, cue, p)
                val varied = LudoPawsPawnReactionVariation.pose(base, p, v)
                val pose = CharacterLivingBehavior.accentReaction(varied, id, cue, p, v)
                assertEquals(varied.liftY, pose.liftY, 0f)
                assertEquals(base.bodyYawDegrees, pose.bodyYawDegrees, 0f)
                assertEquals(base.scale, pose.scale, 0f)
                assertTrue(listOf(pose.liftY, pose.headTiltDegrees, pose.neckPitchDegrees,
                    pose.primaryAppendageDegrees, pose.secondaryAppendageDegrees, pose.tertiaryAppendageDegrees)
                    .all { it.isFinite() })
            }
        }
    }
}
