package com.rekixo.ludopaws.feature.characters.content.dog

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*

/** One continuous torso/skull, four connected IK chains and articulated face/tail. */
internal object DogRenderer : CharacterRenderer {
    override fun stepTravelProgress(progress: Float) = Dog3DMotionTimeline.stepTravelProgress(progress)
    override fun sample(
        motion: CharacterMotion,
        progress: Float,
        hopPlayback: CharacterHopPlayback,
    ): CharacterPose {
        val anatomyProgress = if (motion == CharacterMotion.HOP && hopPlayback == CharacterHopPlayback.SETTLE) 1f else progress
        val p = Dog3DMotionTimeline.sample(Dog3DMotion.valueOf(motion.name), anatomyProgress)
        return CharacterPose(
            liftY = p.liftY, bodyYawDegrees = p.bodyYawDegrees, headTiltDegrees = p.headTiltDegrees,
            legSwingDegrees = p.legSwingDegrees, legTuckDegrees = p.legTuckDegrees,
            primaryAppendageDegrees = p.earBounceDegrees, secondaryAppendageDegrees = p.tailWagDegrees,
            bodyPitchDegrees = p.bodyPitchDegrees, neckPitchDegrees = p.neckPitchDegrees,
            bodyCrouch = p.bodyCrouch,
            frontLegTuckDegrees = p.frontLegTuckDegrees, rearLegTuckDegrees = p.rearLegTuckDegrees,
            frontLegSwingDegrees = p.frontLegSwingDegrees, rearLegSwingDegrees = p.rearLegSwingDegrees,
        ).let { CharacterHopDynamics.apply(it, motion, progress, .56f, hopPlayback) }
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
            surface = CharacterSurface.BODY_COAT
            dogMesh(torso, DogGeometry.body, DOG_TAN, sx = breath, sz = breath)
            val neck = rigTransforms[2]
            System.arraycopy(torso, 0, neck, 0, 16)
            Matrix.translateM(neck, 0, 0f, .38f, 0f)
            Matrix.rotateM(neck, 0, pose.neckPitchDegrees, 1f, 0f, 0f)
            Matrix.translateM(neck, 0, 0f, -.38f, 0f)
            surface = CharacterSurface.COAT
            dogMesh(neck, DogGeometry.neck, DOG_TAN)
            prepareHead(neck, .94f, pose.headTiltDegrees)
            DogFaceRenderer.draw(this, head, pose)
            tail(torso, pose.secondaryAppendageDegrees)
        } finally { surface = previous }
    }

    private fun CharacterDrawContext.legs(parent: FloatArray, pose: CharacterPose) {
        for (leg in 0 until 4) {
            val rear = leg < 2
            val side = if (leg % 2 == 0) -1f else 1f
            val x = side * (if (rear) .30f else .275f)
            val tuck = if (rear) pose.rearLegTuckDegrees else pose.frontLegTuckDegrees
            val swing = if (rear) pose.rearLegSwingDegrees else pose.frontLegSwingDegrees
            DogLegRig.solve(rear, side, swing ?: pose.legSwingDegrees,
                tuck ?: pose.legTuckDegrees, rigScratch, pose.bodyCrouch, tuck != null)
            val hy = rigScratch[0]; val hz = rigScratch[1]
            val ky = rigScratch[2]; val kz = rigScratch[3]
            val ay = rigScratch[4]; val az = rigScratch[5]
            surface = CharacterSurface.COAT
            dogBone(parent, DogGeometry.limb, x, hy, hz, ky, kz, if (rear) .135f else .10f, DOG_TAN)
            drawPart(parent, x, ky, kz, .075f, .078f, .078f, DOG_TAN)
            dogBone(parent, DogGeometry.limb, x, ky, kz, ay, az, .075f, DOG_TAN)
            dogMesh(parent, DogGeometry.paw, DOG_CREAM, x, ay - .04f, az + .035f)
            // Fine toe grooves read as paws without additional round toe balls.
            surface = CharacterSurface.MATTE
            for (toe in listOf(-1f, 1f)) drawPart(parent, x + toe * .043f,
                ay - .058f, az + .207f, .004f, .027f, .006f, DOG_PAW_SEAM)
        }
    }

    private fun CharacterDrawContext.tail(parent: FloatArray, wag: Float) {
        val chain = rigTransforms[5]
        System.arraycopy(parent, 0, chain, 0, 16)
        Matrix.translateM(chain, 0, .26f, .12f, -.43f)
        Matrix.rotateM(chain, 0, -54f, 1f, 0f, 0f)
        Matrix.rotateM(chain, 0, -22f, 0f, 0f, 1f)
        surface = CharacterSurface.COAT
        for (i in 0..3) {
            Matrix.rotateM(chain, 0, wag * (.22f + i * .055f), 0f, 0f, 1f)
            Matrix.rotateM(chain, 0, 9f, 1f, 0f, 0f)
            val radius = .085f - i * .017f
            dogMesh(chain, DogGeometry.tail, if (i == 3) DOG_CREAM else DOG_TAN,
                y = .09f, sx = radius, sy = .19f, sz = radius)
            Matrix.translateM(chain, 0, 0f, .18f, 0f)
        }
    }
}
