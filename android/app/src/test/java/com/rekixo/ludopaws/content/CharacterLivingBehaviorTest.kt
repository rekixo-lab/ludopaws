package com.rekixo.ludopaws.content

import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.characters.rendering.CharacterPose
import com.rekixo.ludopaws.feature.characters.shared.CharacterLivingBehavior
import org.junit.Assert.*
import org.junit.Test

class CharacterLivingBehaviorTest {
    @Test fun everyShippedCharacterHasAnAnatomicalBehaviorProfile() {
        assertEquals(CharacterDefinitions.all.map { it.character.id }.toSet(), CharacterLivingBehavior.characterIds)
    }

    @Test fun idleGesturesNeverAlterFootContactRouteOrMovementChannels() {
        val base = CharacterPose(liftY = .1f, bodyScaleY = .93f, legTuckDegrees = 14f,
            legSwingDegrees = 9f, frontLegSwingDegrees = 3f, bodyCrouch = .05f)
        for (id in CharacterLivingBehavior.characterIds) {
            for (i in 0..500) {
                val pose = CharacterLivingBehavior.blendIdle(base, id, i * .1f, 103, 1f)
                assertEquals(base, pose.copy(headTiltDegrees = base.headTiltDegrees,
                    neckPitchDegrees = base.neckPitchDegrees, primaryAppendageDegrees = base.primaryAppendageDegrees,
                    secondaryAppendageDegrees = base.secondaryAppendageDegrees, tertiaryAppendageDegrees = base.tertiaryAppendageDegrees,
                    bodyPitchDegrees = base.bodyPitchDegrees, gestureWeight = base.gestureWeight, gesturePhase = base.gesturePhase))
                assertTrue(listOf(pose.headTiltDegrees, pose.neckPitchDegrees, pose.primaryAppendageDegrees,
                    pose.secondaryAppendageDegrees, pose.tertiaryAppendageDegrees).all { it.isFinite() && kotlin.math.abs(it) <= 32f })
                assertEquals(base, CharacterLivingBehavior.blendIdle(base, id, i * .1f, 103, 0f))
            }
        }
    }

    @Test fun fourPawnsHaveIndependentRhythmsAndGestureSequences() {
        val durations = (0..3).map { CharacterLivingBehavior.idleDurationMillis(2400L, it) }
        assertEquals(4, durations.toSet().size)
        for (id in CharacterLivingBehavior.characterIds) {
            val sequences = (0..3).map { token ->
                (0..200).map { CharacterLivingBehavior.blendIdle(CharacterPose(), id, it * .1f, token * 103, 1f) }
            }
            assertEquals(id, 4, sequences.toSet().size)
        }
    }
}
