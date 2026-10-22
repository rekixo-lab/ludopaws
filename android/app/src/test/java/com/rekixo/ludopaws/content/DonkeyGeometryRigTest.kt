package com.rekixo.ludopaws.content

import com.rekixo.ludopaws.feature.characters.content.donkey.DonkeyGait
import com.rekixo.ludopaws.feature.characters.content.donkey.DonkeyGeometry
import com.rekixo.ludopaws.feature.characters.content.donkey.DonkeyLegRig
import com.rekixo.ludopaws.feature.characters.content.donkey.DonkeyRenderer
import com.rekixo.ludopaws.feature.characters.content.donkey.Donkey3DMotionTimeline
import com.rekixo.ludopaws.feature.characters.rendering.CharacterMotion
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class DonkeyGeometryRigTest {
    @Test
    fun allDonkeyMeshesHaveValidTrianglesAndUnitNormals() {
        for (mesh in DonkeyGeometry.all) {
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
                DonkeyLegRig.solve(rear, side, stride.toFloat(), tuck.toFloat(), out)
                assertTrue(out.all { it.isFinite() })
                assertEquals(DonkeyLegRig.UPPER_LENGTH, hypot(out[2]-out[0], out[3]-out[1]), .0001f)
                assertEquals(DonkeyLegRig.LOWER_LENGTH, hypot(out[4]-out[2], out[5]-out[3]), .0001f)
                if (tuck == 0) assertEquals(DonkeyLegRig.GROUND_ANKLE_Y, out[4], .0001f)
                else assertTrue(out[4] > DonkeyLegRig.GROUND_ANKLE_Y)
            }
        }
    }
    @Test
    fun preloadLowersTheBodyWithoutPullingTheHoovesOffTheGround() {
        val standing = FloatArray(6)
        val crouched = FloatArray(6)
        DonkeyLegRig.solve(false, 1f, 0f, 0f, standing)
        DonkeyLegRig.solve(false, 1f, 0f, 0f, crouched, .08f)
        assertTrue(crouched[0] < standing[0])
        assertEquals(standing[4], crouched[4], .0001f)
        assertEquals(standing[5], crouched[5], .0001f)
    }

    @Test
    fun trotAlternatesDiagonalFeetAndReturnsToRest() {
        for (progress in listOf(.35f,.65f)) {
            val p = DonkeyRenderer.sample(CharacterMotion.HOP, progress)
            val a=FloatArray(6); val b=FloatArray(6)
            for (side in listOf(-1f,1f)) {
                DonkeyGait.foot(false,side,p.stridePhase,p.strideWeight,a)
                DonkeyGait.foot(true,-side,p.stridePhase,p.strideWeight,b)
                assertEquals(a[0],b[0],.0001f)
                assertTrue(a.all { it.isFinite() } && b.all { it.isFinite() })
            }
        }
    }

    @Test
    fun plantedPhasesHoldRouteAndClipsReturnToNeutralCompression() {
        assertEquals(0f, Donkey3DMotionTimeline.stepTravelProgress(.16f), .0001f)
        assertEquals(1f, Donkey3DMotionTimeline.stepTravelProgress(.86f), .0001f)
        var last = 0f
        for (i in 0..100) {
            val travel = Donkey3DMotionTimeline.stepTravelProgress(i / 100f)
            assertTrue(travel in last..1f)
            last = travel
        }
        for (motion in listOf(CharacterMotion.HOP, CharacterMotion.HOME)) {
            for (progress in listOf(0f, 1f)) {
                val p = DonkeyRenderer.sample(motion, progress)
                assertEquals(0f, p.bodyCrouch, .0001f)
                assertEquals(0f, p.liftY, .0001f)
            }
        }
    }

    @Test
    fun continuousFootPathsPlantStanceAndLiftOnlyTheSwingPair() {
        val front=FloatArray(6);val rear=FloatArray(6);val other=FloatArray(6)
        for(i in 0..100) {
            val phase=i/100f
            DonkeyGait.foot(false,1f,phase,1f,front)
            DonkeyGait.foot(true,-1f,phase,1f,rear)
            DonkeyGait.foot(false,-1f,phase,1f,other)
            assertEquals(front[0],rear[0],.0001f)
            assertEquals(DonkeyLegRig.GROUND_ANKLE_Y,minOf(front[0],other[0]),.0001f)
            assertTrue(front.all { it.isFinite() })
            DonkeyLegRig.solveFoot(false,-.24f,.30f,front[0],front[1],rear)
            assertEquals(front[0],rear[4],.0001f)
            assertEquals(front[1],rear[5],.0001f)
            assertEquals(DonkeyLegRig.UPPER_LENGTH,hypot(rear[2]-rear[0],rear[3]-rear[1]),.0001f)
        }
    }

}
