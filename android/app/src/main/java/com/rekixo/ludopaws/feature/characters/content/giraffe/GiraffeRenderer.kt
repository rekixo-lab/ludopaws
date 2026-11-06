package com.rekixo.ludopaws.feature.characters.content.giraffe

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.content.donkey.DonkeyLegRig
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.CharacterHopDynamics
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

internal object GiraffeRenderer : CharacterRenderer {
    private val honey = floatArrayOf(.98f, .67f, .29f, 1f)
    private val cinnamon = floatArrayOf(.49f, .205f, .072f, 1f)
    private val cream = floatArrayOf(.98f, .83f, .57f, 1f)
    private val earPink = floatArrayOf(.90f, .49f, .43f, 1f)
    private val hoofBrown = floatArrayOf(.21f, .13f, .10f, 1f)
    private val dark = floatArrayOf(.10f, .052f, .038f, 1f)

    override fun stepTravelProgress(progress: Float): Float = CharacterHopDynamics.travel(progress)

    override fun sample(motion: CharacterMotion, progress: Float, hopPlayback: CharacterHopPlayback): CharacterPose {
        val phase = progress.coerceIn(0f, 1f)
        val wave = sin(phase * 2f * PI.toFloat())
        val anatomy = if (hopPlayback == CharacterHopPlayback.SETTLE) 1f else phase
        val arc = sin(stepTravelProgress(anatomy) * PI.toFloat()).coerceAtLeast(0f)
        val pose = when (motion) {
            CharacterMotion.IDLE -> CharacterPose(
                headTiltDegrees = 4f * wave,
                neckPitchDegrees = 3f * wave,
                primaryAppendageDegrees = 10f * wave,
                secondaryAppendageDegrees = 17f * wave,
            )
            CharacterMotion.HOP -> CharacterPose(
                legSwingDegrees = 27f * wave * arc,
                frontLegTuckDegrees = 31f * arc,
                rearLegTuckDegrees = 21f * arc,
                bodyPitchDegrees = -5f * arc,
                neckPitchDegrees = 8f * arc,
                primaryAppendageDegrees = 12f * arc,
                secondaryAppendageDegrees = 19f * wave,
            )
            CharacterMotion.HOME -> {
                val bow = sin(phase * PI.toFloat())
                CharacterPose(
                    liftY = .16f * bow * bow,
                    bodyYawDegrees = 360f * phase,
                    neckPitchDegrees = 11f * bow,
                    headTiltDegrees = 6f * wave,
                    secondaryAppendageDegrees = 25f * wave,
                )
            }
        }
        return CharacterHopDynamics.apply(pose, motion, progress, .47f, hopPlayback)
    }

    override fun draw(
        context: CharacterDrawContext,
        parent: FloatArray,
        pose: CharacterPose,
        teamColor: LudoPawsTeamColor,
    ) = with(context) {
        val previousSurface = surface
        try {
            val model = rigTransforms[0]
            System.arraycopy(parent, 0, model, 0, 16)
            val scale = GiraffeDefinition.MODEL_SCALE
            Matrix.scaleM(model, 0, scale, scale, scale)
            // A slight three-quarter stance reveals the length of the torso and all four hooves.
            Matrix.rotateM(model, 0, -25f, 0f, 1f, 0f)
            legs(model, pose)

            val torso = rigTransforms[1]
            System.arraycopy(model, 0, torso, 0, 16)
            Matrix.translateM(torso, 0, 0f, -pose.bodyCrouch, 0f)
            Matrix.rotateM(torso, 0, pose.bodyPitchDegrees, 1f, 0f, 0f)
            Matrix.rotateM(torso, 0, pose.bodyRollDegrees, 0f, 0f, 1f)
            val breath = 1f + (life?.breath ?: 0f)
            coat(torso, GiraffeGeometry.body, breath)
            tail(torso, pose.secondaryAppendageDegrees)

            val neck = rigTransforms[2]
            System.arraycopy(torso, 0, neck, 0, 16)
            Matrix.translateM(neck, 0, 0f, .24f, .25f)
            Matrix.rotateM(neck, 0, pose.neckPitchDegrees, 1f, 0f, 0f)
            Matrix.translateM(neck, 0, 0f, -.24f, -.25f)
            coat(neck, GiraffeGeometry.neck)
            surface = CharacterSurface.SOFT_FUR
            drawGeometry(neck, GiraffeGeometry.mane, 0f, 0f, 0f, 1f, 1f, 1f, cinnamon)
            prepareHead(neck, 1.86f, pose.headTiltDegrees)
            face(head, pose)
        } finally {
            surface = previousSurface
        }
    }

    private fun CharacterDrawContext.coat(parent: FloatArray, coat: GiraffeGeometry.Coat, width: Float = 1f) {
        surface = CharacterSurface.COAT
        drawGeometry(parent, coat.gold, 0f, 0f, 0f, width, 1f, width, honey)
        drawGeometry(parent, coat.patches, 0f, 0f, 0f, width, 1f, width, cinnamon)
    }

    private fun CharacterDrawContext.legs(parent: FloatArray, pose: CharacterPose) {
        for (leg in 0..3) {
            val rear = leg < 2
            val side = if (leg % 2 == 0) -1f else 1f
            val x = side * (if (rear) .20f else .185f)
            val tuck = (if (rear) pose.rearLegTuckDegrees else pose.frontLegTuckDegrees) ?: pose.legTuckDegrees
            DonkeyLegRig.solve(rear, side, pose.legSwingDegrees, tuck, rigScratch, pose.bodyCrouch)

            val hipY = legY(rigScratch[0])
            val kneeY = legY(rigScratch[2])
            val ankleY = legY(rigScratch[4])
            val hipZ = rigScratch[1]
            val kneeZ = rigScratch[3]
            val ankleZ = rigScratch[5]
            surface = CharacterSurface.COAT
            bone(parent, x, hipY, hipZ, kneeY, kneeZ, if (rear) .092f else .079f, honey)
            bone(parent, x, kneeY, kneeZ, ankleY, ankleZ, .052f, cream)
            drawPart(parent, x, kneeY, kneeZ, .057f, .069f, .055f, honey)
            surface = CharacterSurface.HOOF
            drawGeometry(parent, GiraffeGeometry.hoof, x, ankleY + GiraffeDefinition.HOOF_OFFSET, ankleZ + .015f,
                1f, 1f, 1f, hoofBrown)
        }
    }

    private fun legY(y: Float): Float =
        GiraffeDefinition.LEG_Y_ANCHOR + (y - GiraffeDefinition.LEG_Y_ANCHOR) * GiraffeDefinition.LEG_Y_SCALE

    private fun CharacterDrawContext.bone(
        parent: FloatArray, x: Float, startY: Float, startZ: Float, endY: Float, endZ: Float,
        radius: Float, color: FloatArray,
    ) {
        val y = endY - startY
        val z = endZ - startZ
        drawPart(parent, x, (startY + endY) * .5f, (startZ + endZ) * .5f,
            radius, hypot(y, z) * .5f + .035f, radius, color,
            rotateX = atan2(z, y) * 180f / PI.toFloat())
    }

    private fun CharacterDrawContext.face(parent: FloatArray, pose: CharacterPose) {
        surface = CharacterSurface.SOFT_FUR
        drawGeometry(parent, GiraffeGeometry.head, 0f, 0f, 0f, 1f, 1f, 1f, honey)
        for (side in -1..1 step 2) {
            val x = side.toFloat()
            val ear = rigTransforms[3]
            System.arraycopy(parent, 0, ear, 0, 16)
            Matrix.translateM(ear, 0, x * .174f, 1.74f, .285f)
            Matrix.rotateM(ear, 0, -x * (57f + pose.primaryAppendageDegrees), 0f, 0f, 1f)
            surface = CharacterSurface.SOFT_FUR
            drawGeometry(ear, GiraffeGeometry.ear, 0f, 0f, 0f, 1f, 1f, 1f, honey)
            drawGeometry(ear, GiraffeGeometry.ear, 0f, .025f, .031f, .62f, .77f, .45f, earPink)

            surface = CharacterSurface.HORN
            drawPart(parent, x * .10f, 1.89f, .27f, .040f, .12f, .043f, honey, rotateZ = -x * 8f)
            surface = CharacterSurface.SOFT_FUR
            drawPart(parent, x * .115f, 2.005f, .27f, .058f, .063f, .056f, cinnamon)

            surface = CharacterSurface.EYE
            drawLivingEye(parent, x * .142f, 1.648f, .520f, .066f, .083f,
                honey, cream, dark, horizontal = false, slit = false)
            surface = CharacterSurface.SOFT_FUR
            drawPart(parent, x * .103f, 1.78f, .415f, .033f, .048f, .02f, cinnamon, rotateZ = x * 15f)
        }

        val open = ((life?.mouthOpen ?: 0f) + pose.jawOpen).coerceIn(0f, 1f)
        surface = CharacterSurface.SOFT_FUR
        drawPart(parent, 0f, 1.467f, .618f, .174f, .118f, .143f, cream, rotateX = -10f)
        drawPart(parent, 0f, 1.395f - .055f * open, .600f, .126f, .046f, .103f, cream)
        surface = CharacterSurface.NOSE
        drawPart(parent, -.067f, 1.490f, .751f, .024f, .016f, .010f, dark, rotateZ = -17f)
        drawPart(parent, .067f, 1.490f, .751f, .024f, .016f, .010f, dark, rotateZ = 17f)
        surface = CharacterSurface.MATTE
        drawPart(parent, 0f, 1.415f - .019f * open, .742f, .087f, .008f + .020f * open, .012f, hoofBrown)
    }

    private fun CharacterDrawContext.tail(parent: FloatArray, sway: Float) {
        val tail = rigTransforms[4]
        System.arraycopy(parent, 0, tail, 0, 16)
        Matrix.translateM(tail, 0, 0f, .045f, -.685f)
        Matrix.rotateM(tail, 0, -126f, 1f, 0f, 0f)
        Matrix.rotateM(tail, 0, sway, 0f, 0f, 1f)
        surface = CharacterSurface.COAT
        drawPart(tail, 0f, .135f, 0f, .027f, .155f, .029f, honey)
        Matrix.translateM(tail, 0, 0f, .27f, 0f)
        Matrix.rotateM(tail, 0, -24f, 1f, 0f, 0f)
        drawPart(tail, 0f, .065f, 0f, .023f, .090f, .023f, honey)
        surface = CharacterSurface.SOFT_FUR
        drawPart(tail, 0f, .175f, 0f, .051f, .091f, .039f, cinnamon)
    }
}
