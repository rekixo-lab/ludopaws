package com.rekixo.ludopaws.feature.characters.content.cat

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*

/** One continuous torso/skull, four connected IK chains and articulated face/tail. */
internal object CatRenderer : CharacterRenderer {
    override fun stepTravelProgress(progress: Float) = Cat3DMotionTimeline.stepTravelProgress(progress)
    override fun sample(
        motion: CharacterMotion,
        progress: Float,
        hopPlayback: CharacterHopPlayback,
    ): CharacterPose {
        val anatomyProgress = if (motion == CharacterMotion.HOP && hopPlayback == CharacterHopPlayback.SETTLE) 1f else progress
        val p = Cat3DMotionTimeline.sample(Cat3DMotion.valueOf(motion.name), anatomyProgress)
        return CharacterPose(
            liftY = p.liftY, bodyYawDegrees = p.bodyYawDegrees, headTiltDegrees = p.headTiltDegrees,
            legSwingDegrees = p.legSwingDegrees, legTuckDegrees = p.legTuckDegrees,
            primaryAppendageDegrees = p.earTwitchDegrees, secondaryAppendageDegrees = p.tailSwayDegrees,
            bodyPitchDegrees = p.bodyPitchDegrees, neckPitchDegrees = p.neckPitchDegrees,
            bodyCrouch = p.bodyCrouch,
            frontLegTuckDegrees = p.frontLegTuckDegrees, rearLegTuckDegrees = p.rearLegTuckDegrees,
            frontLegSwingDegrees = p.frontLegSwingDegrees, rearLegSwingDegrees = p.rearLegSwingDegrees,
        ).let { CharacterHopDynamics.apply(it, motion, progress, .58f, hopPlayback) }
    }

    override fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose, teamColor: LudoPawsTeamColor) = with(context) {
        val previous = surface
        try {
            val rig = rigTransforms[0]
            System.arraycopy(parent, 0, rig, 0, 16)
            Matrix.rotateM(rig, 0, -15f, 0f, 1f, 0f)
            legs(rig, pose)
            val torso = rigTransforms[1]
            System.arraycopy(rig, 0, torso, 0, 16)
            Matrix.translateM(torso, 0, 0f, -pose.bodyCrouch, 0f)
            Matrix.rotateM(torso, 0, pose.bodyPitchDegrees, 1f, 0f, 0f)
            val breath = 1f + (life?.breath ?: 0f)
            surface = CharacterSurface.CAT_COAT
            catMesh(torso, CatGeometry.body, CAT_WHITE_FUR, sx = breath, sz = breath)
            val neck = rigTransforms[2]
            System.arraycopy(torso, 0, neck, 0, 16)
            Matrix.translateM(neck, 0, 0f, .38f, 0f)
            Matrix.rotateM(neck, 0, pose.neckPitchDegrees, 1f, 0f, 0f)
            Matrix.translateM(neck, 0, 0f, -.38f, 0f)
            surface = CharacterSurface.CAT_COAT
            catMesh(neck, CatGeometry.neck, CAT_WHITE_FUR)
            surface = CharacterSurface.MATTE
            catMesh(neck, CatGeometry.collar, teamColor.glColor)
            surface = CharacterSurface.HORN
            drawPart(neck, 0f, .40f, .276f, .060f, .076f, .026f, TAG_GOLD)
            // Lower the entire facial rig together; eyes, ears and jaw keep their own pivots.
            Matrix.translateM(neck, 0, 0f, -.18f, 0f)
            prepareHead(neck, .94f, pose.headTiltDegrees)
            CatFaceRenderer.draw(this, head, pose)
            tail(torso, pose.secondaryAppendageDegrees)
        } finally { surface = previous }
    }

    private fun CharacterDrawContext.legs(parent: FloatArray, pose: CharacterPose) {
        for (leg in 0 until 4) {
            val rear = leg < 2
            val side = if (leg % 2 == 0) -1f else 1f
            val x = side * (if (rear) .27f else .24f)
            val tuck = if (rear) pose.rearLegTuckDegrees else pose.frontLegTuckDegrees
            val swing = if (rear) pose.rearLegSwingDegrees else pose.frontLegSwingDegrees
            CatLegRig.solve(rear, side, swing ?: pose.legSwingDegrees,
                tuck ?: pose.legTuckDegrees, rigScratch, pose.bodyCrouch, tuck != null)
            val hy = rigScratch[0]; val hz = rigScratch[1]
            val ky = rigScratch[2]; val kz = rigScratch[3]
            val ay = rigScratch[4]; val az = rigScratch[5]
            surface = CharacterSurface.CAT_COAT
            catBone(parent, CatGeometry.limb, x, hy, hz, ky, kz, if (rear) .115f else .085f, CAT_WHITE_FUR)
            surface = CharacterSurface.SOFT_FUR
            drawPart(parent, x, ky, kz, .075f, .078f, .078f, CAT_WHITE_FUR)
            surface = CharacterSurface.CAT_COAT
            catBone(parent, CatGeometry.limb, x, ky, kz, ay, az, .065f, CAT_WHITE_FUR)
            surface = CharacterSurface.SOFT_FUR
            catMesh(parent, CatGeometry.paw, CAT_CREAM, x, ay - .03f, az + .035f)
            // Fine toe grooves read as paws without additional round toe balls.
            surface = CharacterSurface.MATTE
            for (toe in listOf(-1f, 1f)) drawPart(parent, x + toe * .043f,
                ay - .058f, az + .173f, .004f, .027f, .006f, CAT_STRIPE_DARK)
        }
    }

    private fun CharacterDrawContext.tail(parent: FloatArray, sway: Float) {
        val chain=rigTransforms[5]
        System.arraycopy(parent,0,chain,0,16)
        Matrix.translateM(chain,0,.27f,.05f,-.39f)
        Matrix.rotateM(chain,0,-43f,1f,0f,0f)
        Matrix.rotateM(chain,0,-31f,0f,0f,1f)
        surface=CharacterSurface.CAT_COAT
        var radius=.075f
        for(i in 0..5) {
            Matrix.rotateM(chain,0,sway*(.16f+i*.035f),0f,0f,1f)
            Matrix.rotateM(chain,0,14f+i*2f,1f,0f,0f)
            catMesh(chain,if(i==5) CatGeometry.tailTip else CatGeometry.tail,
                CAT_WHITE_FUR,
                y=.085f,sx=radius,sy=.18f,sz=radius)
            Matrix.translateM(chain,0,0f,.17f,0f)
            radius*=.80f
        }
    }
}
