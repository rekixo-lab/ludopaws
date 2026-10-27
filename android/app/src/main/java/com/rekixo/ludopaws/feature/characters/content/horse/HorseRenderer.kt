package com.rekixo.ludopaws.feature.characters.content.horse

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.content.donkey.DonkeyLegRig
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.*

internal val HORSE_CHESTNUT = floatArrayOf(.65f, .29f, .13f, 1f)
internal val HORSE_MANE = floatArrayOf(.16f, .09f, .07f, 1f)
internal val HORSE_MUZZLE = floatArrayOf(.33f, .22f, .18f, 1f)
internal val HORSE_BLAZE = floatArrayOf(.98f, .94f, .83f, 1f)
internal val HORSE_HOOF = floatArrayOf(.16f, .15f, .15f, 1f)

internal object HorseRenderer : CharacterRenderer {
    override fun stepTravelProgress(progress: Float) = CharacterHopDynamics.travel(progress)

    override fun sample(motion: CharacterMotion, progress: Float, hopPlayback: CharacterHopPlayback): CharacterPose {
        val phase = progress.coerceIn(0f, 1f)
        val wave = sin(phase * PI * 2.0).toFloat()
        val anatomyPhase = if (motion == CharacterMotion.HOP && hopPlayback == CharacterHopPlayback.SETTLE) 1f else phase
        val arc = sin(stepTravelProgress(anatomyPhase) * PI).toFloat().coerceAtLeast(0f)
        val pose = when (motion) {
            CharacterMotion.IDLE -> CharacterPose(
                headTiltDegrees = 2f * wave, primaryAppendageDegrees = 6f * wave,
                secondaryAppendageDegrees = 9f * wave, neckPitchDegrees = 2f * wave,
            )
            CharacterMotion.HOP -> CharacterPose(
                legSwingDegrees = 32f * wave * arc, legTuckDegrees = 28f * arc,
                primaryAppendageDegrees = 12f * arc, secondaryAppendageDegrees = 22f * arc,
                bodyPitchDegrees = -6f * arc, neckPitchDegrees = 5f * arc,
            )
            CharacterMotion.HOME -> CharacterPose(
                liftY = .18f * abs(sin(phase * PI * 3.0).toFloat()) * (1f - phase),
                bodyYawDegrees = 360f * phase, headTiltDegrees = 6f * wave,
                primaryAppendageDegrees = 12f * wave, secondaryAppendageDegrees = 28f * wave,
            )
        }
        return CharacterHopDynamics.apply(pose, motion, progress, .55f, hopPlayback)
    }

    override fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose, teamColor: LudoPawsTeamColor) = with(context) {
        val previousSurface = surface
        try {
            val rig = rigTransforms[0]
            System.arraycopy(parent, 0, rig, 0, 16)
            Matrix.rotateM(rig, 0, -24f, 0f, 1f, 0f)
            legs(rig, pose)
            val torso = rigTransforms[1]
            System.arraycopy(rig, 0, torso, 0, 16)
            Matrix.translateM(torso, 0, 0f, -pose.bodyCrouch, 0f)
            Matrix.rotateM(torso, 0, pose.bodyPitchDegrees, 1f, 0f, 0f)
            Matrix.rotateM(torso, 0, pose.bodyRollDegrees, 0f, 0f, 1f)
            val breath = 1f + (life?.breath ?: 0f)
            surface = CharacterSurface.COAT
            drawGeometry(torso, HorseGeometry.body, 0f, 0f, 0f, breath, 1f, breath, HORSE_CHESTNUT)
            tail(torso, pose.secondaryAppendageDegrees)
            val neck = rigTransforms[2]
            System.arraycopy(torso, 0, neck, 0, 16)
            Matrix.translateM(neck, 0, 0f, .36f, .16f)
            Matrix.rotateM(neck, 0, pose.neckPitchDegrees, 1f, 0f, 0f)
            Matrix.translateM(neck, 0, 0f, -.36f, -.16f)
            drawGeometry(neck, HorseGeometry.neck, 0f, 0f, 0f, 1f, 1f, 1f, HORSE_CHESTNUT)
            surface = CharacterSurface.SOFT_FUR
            drawGeometry(neck, HorseGeometry.mane, 0f, 0f, 0f, 1f, 1f, 1f, HORSE_MANE)
            prepareHead(neck, 1.08f, pose.headTiltDegrees)
            face(head, pose)
        } finally {
            surface = previousSurface
        }
    }

    private fun CharacterDrawContext.legs(parent: FloatArray, pose: CharacterPose) {
        for (legIndex in 0..3) {
            val rear = legIndex < 2
            val side = if (legIndex % 2 == 0) -1f else 1f
            val positionX = side * (if (rear) .27f else .235f)
            DonkeyLegRig.solve(rear, side, pose.legSwingDegrees, pose.legTuckDegrees, rigScratch, pose.bodyCrouch)
            val hipY = rigScratch[0]
            val hipZ = rigScratch[1]
            val kneeY = rigScratch[2]
            val kneeZ = rigScratch[3]
            val ankleY = rigScratch[4]
            val ankleZ = rigScratch[5]
            surface = CharacterSurface.COAT
            bone(parent, positionX, hipY, hipZ, kneeY, kneeZ, if (rear) .105f else .09f, HORSE_CHESTNUT)
            drawPart(parent, positionX, kneeY, kneeZ, .068f, .07f, .07f, HORSE_CHESTNUT)
            bone(parent, positionX, kneeY, kneeZ, ankleY, ankleZ, .062f, HORSE_MANE)
            surface = CharacterSurface.HOOF
            drawGeometry(parent, HorseGeometry.hoof, positionX, ankleY - .03f, ankleZ + .025f,
                1f, 1f, 1f, HORSE_HOOF)
        }
    }

    private fun CharacterDrawContext.bone(parent: FloatArray, positionX: Float,
        startY: Float, startZ: Float, endY: Float, endZ: Float, radius: Float, color: FloatArray) {
        val deltaY = endY - startY
        val deltaZ = endZ - startZ
        val length = sqrt(deltaY * deltaY + deltaZ * deltaZ).coerceAtLeast(.001f)
        drawGeometry(parent, HorseGeometry.limb, positionX, (startY + endY) * .5f, (startZ + endZ) * .5f,
            radius, length, radius, color, atan2(deltaZ, deltaY) * 180f / PI.toFloat())
    }

    private fun CharacterDrawContext.face(parent: FloatArray, pose: CharacterPose) {
        surface = CharacterSurface.COAT
        drawGeometry(parent, HorseGeometry.head, 0f, 0f, 0f, 1f, 1f, 1f, HORSE_CHESTNUT)
        for (side in -1..1 step 2) {
            val ear = rigTransforms[3]
            System.arraycopy(parent, 0, ear, 0, 16)
            Matrix.translateM(ear, 0, side * .17f, 1.27f, -.04f)
            Matrix.rotateM(ear, 0, -side * (10f + pose.primaryAppendageDegrees), 0f, 0f, 1f)
            drawGeometry(ear, HorseGeometry.ear, 0f, 0f, 0f, 1f, 1f, 1f, HORSE_CHESTNUT)
            drawGeometry(ear, HorseGeometry.ear, 0f, .025f, .035f, .55f, .78f, .30f, HORSE_MUZZLE)
            surface = CharacterSurface.EYE
            drawLivingEye(parent, side * .19f, 1.10f, .29f,
                .075f, .09f, HORSE_CHESTNUT, HORSE_BLAZE, EYE_DARK, horizontal = false, slit = false)
            surface = CharacterSurface.COAT
        }
        drawPart(parent, 0f, 1.16f, .288f, .052f, .17f, .025f, HORSE_BLAZE)
        drawPart(parent, 0f, .97f, .366f, .038f, .09f, .023f, HORSE_BLAZE, rotateX = 22f)
        surface = CharacterSurface.SOFT_FUR
        drawGeometry(parent, HorseGeometry.muzzle, 0f, 0f, 0f, 1f, 1f, 1f, HORSE_MUZZLE)
        drawPart(parent, 0f, .86f, .673f, .075f, .07f, .02f, HORSE_BLAZE)
        surface = CharacterSurface.NOSE
        for (side in -1..1 step 2) {
            drawPart(parent, side * .12f, .82f, .677f, .026f, .020f, .009f, EYE_DARK, rotateZ = side * 18f)
        }
        val opening = ((life?.mouthOpen ?: 0f) + pose.jawOpen).coerceIn(0f, 1f)
        surface = CharacterSurface.MATTE
        drawPart(parent, 0f, .665f, .64f, .135f, .007f + opening * .035f, .02f, EYE_DARK)
        val jaw = rigTransforms[4]
        System.arraycopy(parent, 0, jaw, 0, 16)
        Matrix.translateM(jaw, 0, 0f, .73f, .30f)
        Matrix.rotateM(jaw, 0, -opening * 22f, 1f, 0f, 0f)
        Matrix.translateM(jaw, 0, 0f, -.73f, -.30f)
        surface = CharacterSurface.SOFT_FUR
        drawGeometry(jaw, HorseGeometry.jaw, 0f, 0f, 0f, 1f, 1f, 1f, HORSE_MUZZLE)
        drawPart(parent, .035f, 1.30f, .15f, .10f, .16f, .045f, HORSE_MANE, rotateZ = -18f)
    }

    private fun CharacterDrawContext.tail(parent: FloatArray, sway: Float) {
        val tail = rigTransforms[5]
        System.arraycopy(parent, 0, tail, 0, 16)
        Matrix.translateM(tail, 0, .10f, .16f, -.76f)
        Matrix.rotateM(tail, 0, 155f, 1f, 0f, 0f)
        Matrix.rotateM(tail, 0, sway * .55f, 0f, 0f, 1f)
        surface = CharacterSurface.SOFT_FUR
        for (segment in 0..4) {
            Matrix.rotateM(tail, 0, -6f, 1f, 0f, 0f)
            Matrix.rotateM(tail, 0, sway * (.08f + segment * .025f), 0f, 0f, 1f)
            val radius = .105f - segment * .012f
            drawGeometry(tail, if (segment == 4) HorseGeometry.tailTip else HorseGeometry.tail,
                0f, .09f, 0f, radius, .20f, radius, HORSE_MANE)
            Matrix.translateM(tail, 0, 0f, .18f, 0f)
        }
    }
}
