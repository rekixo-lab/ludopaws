package com.rekixo.ludopaws.feature.characters.content.cat

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.sin

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
            val size = CatDefinition.MODEL_SIZE_MULTIPLIER
            Matrix.scaleM(rig, 0, size, size, size)
            Matrix.rotateM(rig, 0, -15f, 0f, 1f, 0f)
            legs(rig, pose)
            val torso = rigTransforms[1]
            System.arraycopy(rig, 0, torso, 0, 16)
            Matrix.translateM(torso, 0, 0f, -pose.bodyCrouch, 0f)
            Matrix.rotateM(torso, 0, pose.bodyPitchDegrees, 1f, 0f, 0f)
            val breath = 1f + (life?.breath ?: 0f)
            surface = CharacterSurface.CAT_COAT
            catMesh(torso, CatGeometry.body, CAT_TABBY_FUR, y = .08f, z = -.12f, sx = breath, sz = breath, rx = 90f)
            val neck = rigTransforms[2]
            System.arraycopy(torso, 0, neck, 0, 16)
            Matrix.translateM(neck, 0, 0f, .38f, 0f)
            Matrix.rotateM(neck, 0, pose.neckPitchDegrees, 1f, 0f, 0f)
            Matrix.translateM(neck, 0, 0f, -.38f, 0f)
            surface = CharacterSurface.CAT_COAT
            catMesh(neck, CatGeometry.neck, CAT_TABBY_FUR)
            // Lower the entire facial rig together; eyes, ears and jaw keep their own pivots.
            Matrix.translateM(neck, 0, 0f, -.18f, 0f)
            prepareHead(neck, .94f, pose.headTiltDegrees)
            CatFaceRenderer.draw(this, head, pose)
            tail(torso, pose.secondaryAppendageDegrees)
        } finally {
            surface = previous
            limbBendY = 0f
            limbBendZ = 0f
        }
    }

    private fun CharacterDrawContext.legs(parent: FloatArray, pose: CharacterPose) {
        val legRoot = rigTransforms[6]
        System.arraycopy(parent, 0, legRoot, 0, 16)
        // Like Lumi's legs, the upper roots move into the torso as length is halved.
        // Apply the same transform to the fur, paws and toe creases so they stay joined.
        val lengthScale = CatDefinition.LEG_LENGTH_SCALE
        Matrix.translateM(legRoot, 0, 0f, CatDefinition.LEG_LENGTH_ANCHOR_Y * (1f - lengthScale), 0f)
        Matrix.scaleM(legRoot, 0, 1f, lengthScale, 1f)
        for (leg in 0 until 4) {
            val rear = leg < 2
            val side = if (leg % 2 == 0) -1f else 1f
            val x = side * (if (rear) .19f else .135f)
            val tuck = if (rear) pose.rearLegTuckDegrees else pose.frontLegTuckDegrees
            val swing = if (rear) pose.rearLegSwingDegrees else pose.frontLegSwingDegrees
            CatLegRig.solve(rear, side, swing ?: pose.legSwingDegrees,
                tuck ?: pose.legTuckDegrees, rigScratch, pose.bodyCrouch, tuck != null)
            val hy = rigScratch[0]; val hz = rigScratch[1]
            val wash = if (!rear && side < 0f) pose.gestureWeight else 0f
            val ay = rigScratch[4] + wash * 1.12f
            val az = rigScratch[5] + wash * .14f
            surface = CharacterSurface.CAT_COAT
            catBone(legRoot, CatGeometry.limb, x, hy, hz, ay, az,
                if (rear) .10f else .078f, CAT_TABBY_FUR)
            surface = CharacterSurface.SOFT_FUR
            catMesh(legRoot, CatGeometry.paw, CAT_CREAM, x, ay - .03f, az + .035f)
            // Fine toe grooves read as paws without additional round toe balls.
            surface = CharacterSurface.MATTE
            for (toe in listOf(-1f, 1f)) drawPart(legRoot, x + toe * .028f,
                ay - .058f, az + .14f, .0025f, .013f, .003f, CAT_STRIPE_DARK)
        }
    }

    private fun CharacterDrawContext.tail(parent: FloatArray, sway: Float) {
        val chain=rigTransforms[5]
        System.arraycopy(parent,0,chain,0,16)
        // Root on the rear spine, embedded in the rump rather than glued to the flank.
        Matrix.translateM(chain,0,0f,.24f,-.57f)
        Matrix.rotateM(chain,0,-43f,1f,0f,0f)
        Matrix.rotateM(chain,0,-24f,0f,0f,1f)
        val time = life?.timeSeconds ?: 0f
        val idle = life?.idleWeight ?: 0f
        Matrix.rotateM(chain,0,sway * .12f + sin(time * 1.05f) * 2f * idle,0f,1f,0f)
        surface=CharacterSurface.CAT_COAT
        var radius=.075f
        for(i in 0..5) {
            // A delayed wave travels toward the tip while the embedded base stays anchored.
            val wave = sin(time * 1.5f - i * .55f) * (1.2f + i * .4f) * idle
            // Keep the original upward arc with a gentle rightward bend toward the tip.
            val rightCurve = if (i >= 2) -6f else 0f
            Matrix.rotateM(chain,0,rightCurve+sway*(.10f+i*.025f)+wave,0f,0f,1f)
            Matrix.rotateM(chain,0,14f+i*2f,1f,0f,0f)
            catMesh(chain,if(i==5) CatGeometry.tailTip else CatGeometry.tail,
                CAT_TABBY_FUR,
                y=.085f,sx=radius,sy=.18f,sz=radius)
            Matrix.translateM(chain,0,0f,.17f,0f)
            radius*=.80f
        }
    }
}
