package com.rekixo.ludopaws.content

import com.rekixo.ludopaws.feature.characters.content.goat.GoatGeometry
import com.rekixo.ludopaws.feature.characters.content.goat.GoatLegRig
import com.rekixo.ludopaws.feature.characters.content.goat.GoatRenderer
import com.rekixo.ludopaws.feature.characters.content.goat.Goat3DMotionTimeline
import com.rekixo.ludopaws.feature.characters.rendering.CharacterMotion
import com.rekixo.ludopaws.feature.characters.shared.CharacterHopDynamics
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GoatGeometryRigTest {
    @Test
    fun allGoatMeshesHaveValidTrianglesAndUnitNormals() {
        for (mesh in GoatGeometry.all) {
            assertTrue(mesh.indices.size >= 36)
            assertTrue(mesh.vertices.size / 6 < 1024)
            for (i in mesh.vertices.indices step 6) {
                val length = sqrt(mesh.vertices[i+3].pow(2) + mesh.vertices[i+4].pow(2) + mesh.vertices[i+5].pow(2))
                assertEquals(1f, length, .0001f)
            }
            for (index in mesh.indices) assertTrue((index.toInt() and 65535) < mesh.vertices.size / 6)
        }
    }

    @Test
    fun kneesRemainConnectedAndHoovesStayPlantedWithoutTuck() {
        val out = FloatArray(6)
        for (rear in listOf(false, true)) for (side in listOf(-1f, 1f)) {
            for (stride in -30..30 step 3) for (tuck in 0..38 step 2) {
                GoatLegRig.solve(rear, side, stride.toFloat(), tuck.toFloat(), out)
                assertTrue(out.all { it.isFinite() })
                assertEquals(GoatLegRig.UPPER_LENGTH, hypot(out[2]-out[0], out[3]-out[1]), .0001f)
                assertEquals(GoatLegRig.LOWER_LENGTH, hypot(out[4]-out[2], out[5]-out[3]), .0001f)
                if (tuck == 0) assertEquals(GoatLegRig.GROUND_ANKLE_Y, out[4], .0001f)
                else assertTrue(out[4] > GoatLegRig.GROUND_ANKLE_Y)
            }
        }
    }
    @Test
    fun preloadLowersTheBodyWithoutPullingTheHoovesOffTheGround() {
        val standing = FloatArray(6)
        val crouched = FloatArray(6)
        GoatLegRig.solve(false, 1f, 0f, 0f, standing)
        GoatLegRig.solve(false, 1f, 0f, 0f, crouched, .08f)
        assertTrue(crouched[0] < standing[0])
        assertEquals(standing[4], crouched[4], .0001f)
        assertEquals(standing[5], crouched[5], .0001f)
    }

    @Test
    fun boundReleasesForePairFirstAndLandsForePairBeforeHindPair() {
        val frontLead = GoatRenderer.sample(CharacterMotion.HOP, .26f)
        assertTrue(frontLead.frontLegTuckDegrees!! > 10f)
        assertTrue(frontLead.rearLegTuckDegrees!! < frontLead.frontLegTuckDegrees!!)
        val planted = GoatRenderer.sample(CharacterMotion.HOP, .14f)
        assertEquals(0f, planted.liftY, .0001f)
        assertEquals(0f, planted.frontLegTuckDegrees!!, .0001f)
        assertEquals(0f, planted.rearLegTuckDegrees!!, .0001f)
        assertTrue(planted.bodyScaleY <= .85f)
        // Front hooves complete their landing at the shared contact boundary.
        val contact = GoatRenderer.sample(CharacterMotion.HOP, CharacterHopDynamics.CONTACT)
        assertEquals(0f, contact.frontLegTuckDegrees!!, .0001f)
        assertTrue(contact.rearLegTuckDegrees!! > 5f)
        assertEquals(0f, contact.liftY, .0001f)
        for (i in 0..100) {
            val p = GoatRenderer.sample(CharacterMotion.HOP, i / 100f)
            for (rear in listOf(false, true)) {
                val a = FloatArray(6); val b = FloatArray(6)
                val tuck = (if (rear) p.rearLegTuckDegrees else p.frontLegTuckDegrees)!!
                val swing = (if (rear) p.rearLegSwingDegrees else p.frontLegSwingDegrees)!!
                GoatLegRig.solve(rear, -1f, swing, tuck, a, p.bodyCrouch, pairedStride = true)
                GoatLegRig.solve(rear, 1f, swing, tuck, b, p.bodyCrouch, pairedStride = true)
                assertArrayEquals(a, b, .0001f)
                assertTrue(a.all { it.isFinite() })
                assertEquals(GoatLegRig.UPPER_LENGTH, hypot(a[2]-a[0], a[3]-a[1]), .0001f)
                assertEquals(GoatLegRig.LOWER_LENGTH, hypot(a[4]-a[2], a[5]-a[3]), .0001f)
            }
        }
    }

    @Test
    fun plantedPhasesHoldRouteAndClipsReturnToNeutralCompression() {
        assertEquals(0f, Goat3DMotionTimeline.stepTravelProgress(.16f), .0001f)
        assertEquals(1f, Goat3DMotionTimeline.stepTravelProgress(.82f), .0001f)
        var last = 0f
        for (i in 0..100) {
            val travel = Goat3DMotionTimeline.stepTravelProgress(i / 100f)
            assertTrue(travel in last..1f)
            last = travel
        }
        for (motion in listOf(CharacterMotion.HOP, CharacterMotion.HOME)) {
            for (progress in listOf(0f, 1f)) {
                val p = GoatRenderer.sample(motion, progress)
                assertEquals(0f, p.bodyCrouch, .0001f)
                assertEquals(0f, p.liftY, .0001f)
            }
        }
    }

}
