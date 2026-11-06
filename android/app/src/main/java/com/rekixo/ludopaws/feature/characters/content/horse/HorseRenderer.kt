package com.rekixo.ludopaws.feature.characters.content.horse

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.content.donkey.DonkeyLegRig
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.*

internal val HORSE_CHESTNUT = floatArrayOf(.64f, .26f, .105f, 1f)
internal val HORSE_MANE = floatArrayOf(.20f, .096f, .055f, 1f)
internal val HORSE_MUZZLE = floatArrayOf(.33f, .22f, .18f, 1f)
internal val HORSE_BLAZE = floatArrayOf(.98f, .94f, .83f, 1f)
private val HORSE_STOCKING = floatArrayOf(.29f, .13f, .076f, 1f)
private val HORSE_MANE_LIGHT = floatArrayOf(.30f, .15f, .079f, 1f)
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
                legSwingDegrees = 31f * wave * arc,
                frontLegTuckDegrees = 33f * arc, rearLegTuckDegrees = 23f * arc,
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
            surface = CharacterSurface.HORSE_COAT
            drawGeometry(torso, HorseGeometry.body, 0f, 0f, 0f, breath, 1f, breath, HORSE_CHESTNUT)
            tail(torso, pose.secondaryAppendageDegrees)
            val neck = rigTransforms[2]
            System.arraycopy(torso, 0, neck, 0, 16)
            Matrix.translateM(neck, 0, 0f, .36f, .16f)
            Matrix.rotateM(neck, 0, pose.neckPitchDegrees, 1f, 0f, 0f)
            Matrix.translateM(neck, 0, 0f, -.36f, -.16f)
            surface = CharacterSurface.HORSE_COAT
            drawGeometry(neck, HorseGeometry.neck, 0f, 0f, 0f, 1f, 1f, 1f, HORSE_CHESTNUT)
            surface = CharacterSurface.COAT
            drawGeometry(neck, HorseGeometry.mane, 0f, 0f, 0f, 1f, 1f, 1f, HORSE_MANE)
            drawGeometry(neck, HorseGeometry.mane, .038f, .005f, .028f, .72f, .96f, .77f, HORSE_MANE_LIGHT)
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
            val scrape = if (!rear && side < 0f) pose.gestureWeight else 0f
            val pairTuck = if (rear) pose.rearLegTuckDegrees else pose.frontLegTuckDegrees
            DonkeyLegRig.solve(
                rear, side, pose.legSwingDegrees + 9f * scrape * sin(pose.gesturePhase * PI.toFloat() * 2f),
                (pairTuck ?: pose.legTuckDegrees) + 8f * scrape, rigScratch, pose.bodyCrouch,
            )
            val hipY = rigScratch[0]
            val hipZ = rigScratch[1]
            val kneeY = rigScratch[2]
            val kneeZ = rigScratch[3]
            val ankleY = rigScratch[4]
            val ankleZ = rigScratch[5]
            surface = CharacterSurface.COAT
            bone(parent, positionX, hipY, hipZ, kneeY, kneeZ, if (rear) .105f else .09f, HORSE_CHESTNUT)
            drawPart(parent, positionX, kneeY, kneeZ, .068f, .07f, .07f, HORSE_CHESTNUT)
            bone(parent, positionX, kneeY, kneeZ, ankleY, ankleZ, .062f, HORSE_STOCKING)
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
        surface = CharacterSurface.HORSE_FACE
        drawGeometry(parent, HorseGeometry.head, 0f, 0f, 0f, 1f, 1f, 1f, HORSE_CHESTNUT)
        for (side in -1..1 step 2) {
            val ear = rigTransforms[3]
            System.arraycopy(parent, 0, ear, 0, 16)
            Matrix.translateM(ear, 0, side * .17f, 1.27f, -.04f)
            val swivel = sin((life?.timeSeconds ?: 0f) * 1.5f + side) * 7f * (life?.idleWeight ?: 0f)
            Matrix.rotateM(ear, 0, -side * (10f + pose.primaryAppendageDegrees), 0f, 0f, 1f)
            Matrix.rotateM(ear, 0, side * swivel, 0f, 1f, 0f)
            surface = CharacterSurface.COAT
            drawGeometry(ear, HorseGeometry.ear, 0f, 0f, 0f, 1f, 1f, 1f, HORSE_CHESTNUT)
            drawGeometry(ear, HorseGeometry.ear, 0f, .025f, .035f, .55f, .78f, .30f, HORSE_MUZZLE)
            surface = CharacterSurface.EYE
            drawLivingEye(parent, side * .19f, 1.10f, .29f,
                .075f, .09f, HORSE_CHESTNUT, HORSE_BLAZE, EYE_DARK, horizontal = false, slit = false)
            surface = CharacterSurface.COAT
        }
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
        surface = CharacterSurface.COAT
        drawGeometry(parent, HorseGeometry.forelock, -.017f, 1.36f, .16f,
            1f, 1f, 1f, HORSE_MANE, rotateZ = 10f + pose.primaryAppendageDegrees * .14f)
        drawGeometry(parent, HorseGeometry.forelock, .033f, 1.345f, .177f,
            .62f, .83f, .70f, HORSE_MANE_LIGHT, rotateZ = 19f + pose.primaryAppendageDegrees * .14f)
    }

    private fun CharacterDrawContext.tail(parent: FloatArray, sway: Float) {
        val tail = rigTransforms[5]
        System.arraycopy(parent, 0, tail, 0, 16)
        Matrix.translateM(tail, 0, .065f, .12f, -.72f)
        Matrix.rotateM(tail, 0, -8f + sway * .25f, 1f, 0f, 0f)
        Matrix.rotateM(tail, 0, sway * .85f, 0f, 0f, 1f)
        surface = CharacterSurface.COAT
        drawGeometry(tail, HorseGeometry.tail, 0f, 0f, 0f, 1f, 1f, 1f, HORSE_MANE)
        drawGeometry(tail, HorseGeometry.tail, .032f, -.01f, .025f,
            .67f, .95f, .82f, HORSE_MANE_LIGHT, rotateZ = sway * .12f)
    }
}
