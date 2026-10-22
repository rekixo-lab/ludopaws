package com.rekixo.ludopaws.feature.characters.content.duck

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.*

internal object DuckRenderer : CharacterRenderer {
    override fun stepTravelProgress(progress: Float) = Duck3DMotionTimeline.stepTravelProgress(progress)
    override fun sample(
        motion: CharacterMotion,
        progress: Float,
        hopPlayback: CharacterHopPlayback,
    ): CharacterPose {
        val anatomyProgress = if (motion == CharacterMotion.HOP && hopPlayback == CharacterHopPlayback.SETTLE) 1f else progress
        val p = Duck3DMotionTimeline.sample(Duck3DMotion.valueOf(motion.name), anatomyProgress)
        return CharacterPose(
            liftY = p.liftY, bodyYawDegrees = p.bodyYawDegrees, headTiltDegrees = p.headTiltDegrees,
            legPaddleDegrees = p.legPaddleDegrees, primaryAppendageDegrees = p.wingFlapDegrees,
            legTuckDegrees = p.legTuckDegrees, bodyCrouch = p.bodyCrouch,
            bodyPitchDegrees = p.bodyPitchDegrees, bodyRollDegrees = p.bodyRollDegrees,
            neckPitchDegrees = p.neckPitchDegrees,
        ).let { CharacterHopDynamics.apply(it, motion, progress, .54f, hopPlayback) }
    }
    override fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose, teamColor: LudoPawsTeamColor) = with(context) {
        val previous = surface
        try {
            val rig = rigTransforms[0]
            System.arraycopy(parent,0,rig,0,16)
            Matrix.rotateM(rig,0,-12f,0f,1f,0f)
            feet(rig, pose)
            val torso = rigTransforms[1]
            System.arraycopy(rig,0,torso,0,16)
            Matrix.translateM(torso,0,0f,-pose.bodyCrouch,0f)
            Matrix.rotateM(torso,0,pose.bodyPitchDegrees,1f,0f,0f)
            Matrix.rotateM(torso,0,pose.bodyRollDegrees,0f,0f,1f)
            val breath = 1f+(life?.breath ?: 0f)
            surface = CharacterSurface.BODY_COAT
            duckMesh(torso,DuckGeometry.body,DUCK_YELLOW,sx=breath,sz=breath)
            for (side in -1..1 step 2) wing(torso,side.toFloat(),pose)
            surface = CharacterSurface.COAT
            duckMesh(torso,DuckGeometry.tail,DUCK_BELLY,y=-.02f,z=-.52f,rx=-46f)
            val neck = rigTransforms[2]
            System.arraycopy(torso,0,neck,0,16)
            Matrix.translateM(neck,0,0f,.43f,0f)
            Matrix.rotateM(neck,0,pose.neckPitchDegrees,1f,0f,0f)
            Matrix.translateM(neck,0,0f,-.43f,0f)
            duckMesh(neck,DuckGeometry.neck,DUCK_YELLOW)
            surface=CharacterSurface.MATTE
            duckMesh(neck,DuckGeometry.band,teamColor.glColor)
            surface=CharacterSurface.HORN
            drawPart(neck,0f,.367f,.285f,.060f,.073f,.024f,TAG_GOLD)
            prepareHead(neck,.97f,pose.headTiltDegrees)
            // A small downward glance reveals the bill's top in the frontal game camera.
            Matrix.translateM(head,0,0f,.84f,.05f)
            Matrix.rotateM(head,0,8f,1f,0f,0f)
            Matrix.translateM(head,0,0f,-.90f,-.05f)
            DuckFaceRenderer.draw(this,head,pose)
        } finally { surface=previous }
    }
    private fun CharacterDrawContext.wing(parent: FloatArray, side: Float, pose: CharacterPose) {
        val wing = rigTransforms[3]
        System.arraycopy(parent,0,wing,0,16)
        Matrix.translateM(wing,0,side*.47f,.23f,-.03f)
        Matrix.rotateM(wing,0,side*(15f+pose.primaryAppendageDegrees.coerceIn(-12f,80f)),0f,0f,1f)
        Matrix.rotateM(wing,0,-8f,1f,0f,0f)
        surface=CharacterSurface.COAT
        duckMesh(wing,DuckGeometry.wing,DUCK_YELLOW,sx=side)
        for (i in 0..2) duckMesh(wing,DuckGeometry.feather,DUCK_BELLY,
            x=side*(i-1)*.065f,y=-.19f,z=.065f-i*.008f,sy=.80f+i*.08f,rz=side*(i-1)*9f)
    }
    private fun CharacterDrawContext.feet(parent: FloatArray, pose: CharacterPose) {
        val tuck = (pose.legTuckDegrees/38f).coerceIn(0f,1f)
        for (i in 0..1) {
            val side = if(i==0) -1f else 1f
            val paddle = side*pose.legPaddleDegrees
            val ankleY = -.70f + tuck*.19f
            val ankleZ = .20f + sin(paddle*PI.toFloat()/180f)*.09f
            surface=CharacterSurface.NOSE
            duckBone(parent,DuckGeometry.leg,side*.245f,-.40f-pose.bodyCrouch,.08f,ankleY+.06f,ankleZ,.085f,DUCK_ORANGE)
            duckMesh(parent,DuckGeometry.foot,DUCK_ORANGE,side*.245f,ankleY+.056f,ankleZ,
                // Reveal the webbed toe outline to the board's frontal camera.
                // The center toe remains at the authored -.755 ground baseline.
                ry=side*8f,rx=16f-paddle*.30f*tuck)
        }
    }
}
