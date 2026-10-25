package com.rekixo.ludopaws.content

import com.rekixo.ludopaws.feature.characters.rendering.CharacterHopPlayback
import com.rekixo.ludopaws.feature.characters.rendering.CharacterMotion
import com.rekixo.ludopaws.feature.characters.rendering.CharacterPose
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.characters.shared.CharacterHopDynamics
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class CharacterHopDynamicsTest {
    @Test fun everyCharacterHasVisiblePlantedAnticipationAndContactCompression() {
        assertEquals(setOf("dog", "goat", "duck", "cat", "donkey", "goat_lumi"), CharacterRegistry.all.map { it.id }.toSet())
        for (character in CharacterRegistry.all) {
            val renderer = character.renderer
            val down = renderer.sample(CharacterMotion.HOP, .14f)
            assertEquals(character.id, 0f, down.liftY, .0001f)
            assertEquals(character.id, 0f, renderer.stepTravelProgress(.14f), .0001f)
            assertTrue(character.id, down.bodyScaleY <= .85f && down.bodyCrouch >= .08f)
            val air = renderer.sample(CharacterMotion.HOP, .48f)
            assertTrue(character.id, air.liftY >= .45f && air.bodyScaleY >= .99f)
            val contact = renderer.sample(CharacterMotion.HOP, .80f)
            assertEquals(character.id, 0f, contact.liftY, .0001f)
            assertEquals(character.id, 1f, renderer.stepTravelProgress(.80f), .0001f)
            assertTrue(character.id, contact.bodyScaleY <= .84f && contact.bodyCrouch >= .08f)
        }
    }

    @Test fun standalonePreviewKeepsNeutralEndpointsAndMonotonicRoute() {
        for (character in CharacterRegistry.all) {
            val renderer = character.renderer
            for (p in listOf(0f, 1f)) {
                val pose = renderer.sample(CharacterMotion.HOP, p)
                assertEquals(character.id, 1f, pose.bodyScaleY, .0001f)
                assertEquals(character.id, 0f, pose.bodyCrouch, .0001f)
                assertEquals(character.id, 0f, pose.liftY, .0001f)
            }
            assertEquals(1f, renderer.sample(CharacterMotion.IDLE, .5f).bodyScaleY, .0001f)
            var previous = 0f
            for (i in 0..1000) {
                val travel = renderer.stepTravelProgress(i / 1000f)
                assertTrue(character.id, travel >= previous && travel in 0f..1f)
                previous = travel
            }
        }
    }

    @Test fun touchdownAndTakeoffHaveNoVelocitySnap() {
        val h = .0001f
        for (character in CharacterRegistry.all) {
            val renderer = character.renderer
            for (p in listOf(CharacterHopDynamics.TAKEOFF, CharacterHopDynamics.CONTACT)) {
                val before = renderer.sample(CharacterMotion.HOP, p - h).liftY
                val center = renderer.sample(CharacterMotion.HOP, p).liftY
                val after = renderer.sample(CharacterMotion.HOP, p + h).liftY
                assertTrue(character.id, abs((center - before) / h) < .02f)
                assertTrue(character.id, abs((after - center) / h) < .02f)
                assertTrue(character.id, abs((renderer.stepTravelProgress(p + h) - renderer.stepTravelProgress(p - h)) / (2f * h)) < .02f)
            }
        }
    }

    @Test fun connectedStepsCarryContactPoseAndZeroVelocityAcrossEveryBoundary() {
        val h = .0001f
        for (character in CharacterRegistry.all) {
            val renderer = character.renderer
            for (outgoing in listOf(CharacterHopPlayback.START, CharacterHopPlayback.CHAIN)) {
                val end = channels(renderer.sample(CharacterMotion.HOP, 1f, outgoing))
                val before = channels(renderer.sample(CharacterMotion.HOP, 1f - h, outgoing))
                for (incoming in listOf(CharacterHopPlayback.CHAIN, CharacterHopPlayback.SETTLE)) {
                    val start = channels(renderer.sample(CharacterMotion.HOP, 0f, incoming))
                    val after = channels(renderer.sample(CharacterMotion.HOP, h, incoming))
                    for (i in end.indices) {
                        val label = "${character.id}: $outgoing -> $incoming channel $i"
                        assertEquals(label, end[i], start[i], .00001f)
                        assertTrue(label, abs((end[i] - before[i]) / h) < .02f)
                        assertTrue(label, abs((after[i] - start[i]) / h) < .02f)
                    }
                }
            }
        }
    }

    @Test fun landingDoesNotStandUpAndCrouchAgainBeforeTheNextHop() {
        for (character in CharacterRegistry.all) {
            val renderer = character.renderer
            for (playback in listOf(CharacterHopPlayback.START, CharacterHopPlayback.CHAIN)) {
                var previous = renderer.sample(CharacterMotion.HOP, .68f, playback)
                for (i in 681..1000) {
                    val next = renderer.sample(CharacterMotion.HOP, i / 1000f, playback)
                    assertTrue(character.id, next.bodyScaleY <= previous.bodyScaleY + .000001f)
                    assertTrue(character.id, next.bodyCrouch >= previous.bodyCrouch - .000001f)
                    previous = next
                }
                assertEquals(.83f, previous.bodyScaleY, .0001f)
            }
            var previous = renderer.sample(CharacterMotion.HOP, 0f, CharacterHopPlayback.CHAIN)
            for (i in 1..340) {
                val next = renderer.sample(CharacterMotion.HOP, i / 1000f, CharacterHopPlayback.CHAIN)
                assertTrue(character.id, next.bodyScaleY >= previous.bodyScaleY - .000001f)
                assertTrue(character.id, next.bodyCrouch <= previous.bodyCrouch + .000001f)
                previous = next
            }
        }
    }

    @Test fun finalSettleRecoversOnceWithoutBouncingOrLeavingTheGround() {
        for (character in CharacterRegistry.all) {
            val renderer = character.renderer
            var previous = renderer.sample(CharacterMotion.HOP, 0f, CharacterHopPlayback.SETTLE)
            for (i in 1..1000) {
                val next = renderer.sample(CharacterMotion.HOP, i / 1000f, CharacterHopPlayback.SETTLE)
                assertTrue(character.id, next.bodyScaleY >= previous.bodyScaleY - .000001f && next.bodyScaleY <= 1f)
                assertTrue(character.id, next.bodyCrouch <= previous.bodyCrouch + .000001f)
                assertEquals(character.id, 0f, next.liftY, .00001f)
                previous = next
            }
            assertEquals(1f, previous.bodyScaleY, .00001f)
            assertEquals(0f, previous.bodyCrouch, .00001f)
        }
    }

    // stridePhase is deliberately excluded: it has no displacement at zero strideWeight.
    private fun channels(pose: CharacterPose) = floatArrayOf(
        pose.liftY, pose.bodyScaleY, pose.bodyCrouch, pose.bodyYawDegrees,
        pose.bodyPitchDegrees, pose.bodyRollDegrees, pose.neckPitchDegrees, pose.headTiltDegrees,
        pose.primaryAppendageDegrees, pose.secondaryAppendageDegrees, pose.tertiaryAppendageDegrees,
        pose.legSwingDegrees, pose.legTuckDegrees, pose.legPaddleDegrees, pose.strideWeight,
        pose.frontLegTuckDegrees ?: 0f, pose.rearLegTuckDegrees ?: 0f,
        pose.frontLegSwingDegrees ?: 0f, pose.rearLegSwingDegrees ?: 0f,
    )
}
