package com.rekixo.ludopaws.content

import com.rekixo.ludopaws.feature.characters.content.duck.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class DuckGeometryMotionTest {
    @Test fun meshesHaveFiniteUnitNormalsAndValidIndices() {
        for(mesh in DuckGeometry.all) {
            assertTrue(mesh.vertices.size/6<1024)
            for(i in mesh.vertices.indices step 6) {
                val n=sqrt(mesh.vertices[i+3].pow(2)+mesh.vertices[i+4].pow(2)+mesh.vertices[i+5].pow(2))
                assertEquals(1f,n,.0001f)
            }
            for(i in mesh.indices) assertTrue((i.toInt() and 65535)<mesh.vertices.size/6)
        }
    }
    @Test fun webbedFeetAreFlatAndHaveThreeForwardToeTips() {
        val xyz=DuckGeometry.foot.vertices.asList().chunked(6)
        assertEquals(-.035f,xyz.minOf{it[1]},.0001f)
        assertEquals(.035f,xyz.maxOf{it[1]},.0001f)
        assertTrue(xyz.any{abs(it[0])<.001f && it[2]>.27f})
        assertTrue(xyz.any{it[0]<-.17f && it[2]>.24f})
        assertTrue(xyz.any{it[0]>.17f && it[2]>.24f})
    }
    @Test fun plantedPreloadAndContactHoldRouteAndReturnFeetToGround() {
        assertEquals(0f,Duck3DMotionTimeline.stepTravelProgress(.10f),.0001f)
        assertEquals(1f,Duck3DMotionTimeline.stepTravelProgress(.9f),.0001f)
        for(progress in listOf(0f,.10f,.9f,1f)) {
            val p=Duck3DMotionTimeline.sample(Duck3DMotion.HOP,progress)
            assertEquals(0f,p.liftY,.0001f)
            assertEquals(0f,p.legTuckDegrees,.0001f)
            assertEquals(0f,p.legPaddleDegrees,.0001f)
        }
        for(progress in listOf(0f,1f)) assertEquals(0f,Duck3DMotionTimeline.sample(Duck3DMotion.HOP,progress).bodyCrouch,.0001f)
    }
}
