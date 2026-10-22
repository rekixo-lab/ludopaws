package com.rekixo.ludopaws.feature.characters.content.goat

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*

/** Procedural kid-goat rig: continuous anatomy, articulated neck and four IK leg chains. */
internal object GoatRenderer : CharacterRenderer {
    override fun stepTravelProgress(progress: Float) = Goat3DMotionTimeline.stepTravelProgress(progress)
    override fun sample(
        motion: CharacterMotion,
        progress: Float,
        hopPlayback: CharacterHopPlayback,
    ): CharacterPose {
        val anatomyProgress = if (motion == CharacterMotion.HOP && hopPlayback == CharacterHopPlayback.SETTLE) 1f else progress
        val pose = Goat3DMotionTimeline.sample(Goat3DMotion.valueOf(motion.name), anatomyProgress)
        return CharacterPose(
            liftY = pose.liftY, bodyYawDegrees = pose.bodyYawDegrees,
            headTiltDegrees = pose.headTiltDegrees, legSwingDegrees = pose.legSwingDegrees,
            legTuckDegrees = pose.legTuckDegrees, primaryAppendageDegrees = pose.earFlickDegrees,
            secondaryAppendageDegrees = pose.beardSwingDegrees, tertiaryAppendageDegrees = pose.tailFlickDegrees,
            bodyPitchDegrees = pose.bodyPitchDegrees, bodyRollDegrees = pose.bodyRollDegrees,
            neckPitchDegrees = pose.neckPitchDegrees,
            bodyCrouch = pose.bodyCrouch,
            frontLegTuckDegrees = pose.frontLegTuckDegrees,
            rearLegTuckDegrees = pose.rearLegTuckDegrees,
            frontLegSwingDegrees = pose.frontLegSwingDegrees,
            rearLegSwingDegrees = pose.rearLegSwingDegrees,
        ).let { CharacterHopDynamics.apply(it, motion, progress, .60f, hopPlayback) }
    }

    override fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose,
        teamColor: LudoPawsTeamColor) = with(context) {
        val previousSurface = surface
        surface = CharacterSurface.COAT
        try {
            val rig = rigTransforms[0]
            System.arraycopy(parent, 0, rig, 0, 16)
            Matrix.rotateM(rig, 0, -16f, 0f, 1f, 0f)
            drawLegs(rig, pose)

            val torso = rigTransforms[1]
            System.arraycopy(rig, 0, torso, 0, 16)
            Matrix.translateM(torso, 0, 0f, -pose.bodyCrouch, 0f)
            Matrix.rotateM(torso, 0, pose.bodyPitchDegrees, 1f, 0f, 0f)
            Matrix.rotateM(torso, 0, pose.bodyRollDegrees, 0f, 0f, 1f)
            val breathing = 1f + (life?.breath ?: 0f)
            surface = CharacterSurface.BODY_COAT
            goatMesh(torso, GoatGeometry.body, GOAT_CREAM, sx = breathing, sz = breathing)
            surface = CharacterSurface.COAT
            // A darker dorsal mantle follows the barrel, leaving the front of the kid cream.
            drawPart(torso, 0f, .16f, -.28f, .30f, .30f, .16f, GOAT_FUR_SHADE)
            val neck = rigTransforms[2]
            System.arraycopy(torso, 0, neck, 0, 16)
            Matrix.translateM(neck, 0, 0f, .43f, .025f)
            Matrix.rotateM(neck, 0, pose.neckPitchDegrees, 1f, 0f, 0f)
            Matrix.translateM(neck, 0, 0f, -.43f, -.025f)
            goatMesh(neck, GoatGeometry.neck, GOAT_CREAM)
            surface = CharacterSurface.MATTE
            goatMesh(neck, GoatGeometry.collar, teamColor.glColor)
            surface = CharacterSurface.HORN
            drawPart(neck, 0f, .445f, .32f, .055f, .075f, .025f, TAG_GOLD)
            surface = CharacterSurface.COAT
            prepareHead(neck, 1.03f, pose.headTiltDegrees)
            GoatFaceRenderer.draw(this, head, pose)
            goatMesh(torso, GoatGeometry.tail, GOAT_IVORY,
                .32f, .18f, -.32f, rx = -15f, rz = -44f + pose.tertiaryAppendageDegrees)
        } finally {
            surface = previousSurface
        }
    }

    private fun CharacterDrawContext.drawLegs(parent: FloatArray, pose: CharacterPose) {
        for (leg in 0 until 4) {
                val rear = leg < 2
                val side = if (leg % 2 == 0) -1 else 1
                val x = side * (if (rear) .285f else .235f)
                val pairTuck = if (rear) pose.rearLegTuckDegrees else pose.frontLegTuckDegrees
                val pairSwing = if (rear) pose.rearLegSwingDegrees else pose.frontLegSwingDegrees
                GoatLegRig.solve(rear, side.toFloat(), pairSwing ?: pose.legSwingDegrees,
                    pairTuck ?: pose.legTuckDegrees, rigScratch, pose.bodyCrouch,
                    pairedStride = pairTuck != null)
                val hy = rigScratch[0]; val hz = rigScratch[1]
                val ky = rigScratch[2]; val kz = rigScratch[3]
                val ay = rigScratch[4]; val az = rigScratch[5]
                surface = CharacterSurface.COAT
                goatBone(parent, GoatGeometry.thigh, x, hy, hz, ky, kz, if (rear) .12f else .083f, GOAT_CREAM)
                drawPart(parent, x, ky, kz, .071f, .075f, .075f, GOAT_IVORY)
                goatBone(parent, GoatGeometry.shin, x, ky, kz, ay, az, .065f, GOAT_IVORY)
                drawPart(parent, x, ay + .015f, az, .067f, .07f, .07f, GOAT_CREAM)
                surface = CharacterSurface.HOOF
                // Separate left and right toes leave an actual cloven split, rather than painting a line.
                goatMesh(parent, GoatGeometry.hoof, GOAT_HOOF_DARK, x - .044f, ay - .064f, az + .045f, sx = .82f)
                goatMesh(parent, GoatGeometry.hoof, GOAT_HOOF_WARM, x + .044f, ay - .064f, az + .045f, sx = .82f)
        }
        surface = CharacterSurface.COAT
    }
}
