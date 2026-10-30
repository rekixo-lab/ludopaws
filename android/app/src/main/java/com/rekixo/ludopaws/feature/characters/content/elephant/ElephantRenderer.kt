package com.rekixo.ludopaws.feature.characters.content.elephant

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.*

internal val ELEPHANT_SKIN = floatArrayOf(.56f, .63f, .68f, 1f)
internal val ELEPHANT_INNER_EAR = floatArrayOf(.70f, .57f, .59f, 1f)
internal val ELEPHANT_NAIL = floatArrayOf(.80f, .81f, .77f, 1f)
internal val ELEPHANT_IVORY = floatArrayOf(.98f, .94f, .81f, 1f)
internal val ELEPHANT_SHADOW = floatArrayOf(.25f, .31f, .36f, 1f)

internal object ElephantRenderer : CharacterRenderer {
    override fun stepTravelProgress(progress: Float) = CharacterHopDynamics.travel(progress)

    override fun sample(motion: CharacterMotion, progress: Float, hopPlayback: CharacterHopPlayback): CharacterPose {
        val phase = progress.coerceIn(0f, 1f)
        val wave = sin(phase * PI * 2.0).toFloat()
        val anatomyPhase = if (motion == CharacterMotion.HOP && hopPlayback == CharacterHopPlayback.SETTLE) 1f else phase
        val arc = sin(stepTravelProgress(anatomyPhase) * PI).toFloat().coerceAtLeast(0f)
        val pose = when (motion) {
            CharacterMotion.IDLE -> CharacterPose(
                headTiltDegrees = 2f * wave, primaryAppendageDegrees = 5f * wave,
                secondaryAppendageDegrees = 6f * wave, tertiaryAppendageDegrees = 8f * wave,
            )
            CharacterMotion.HOP -> CharacterPose(
                legSwingDegrees = 18f * wave * arc, legTuckDegrees = 20f * arc,
                primaryAppendageDegrees = 20f * arc, secondaryAppendageDegrees = 32f * arc,
                tertiaryAppendageDegrees = 16f * arc, bodyPitchDegrees = -4f * arc,
            )
            CharacterMotion.HOME -> CharacterPose(
                liftY = .12f * abs(sin(phase * PI * 3.0).toFloat()) * (1f - phase),
                bodyYawDegrees = 360f * phase, primaryAppendageDegrees = 24f * wave,
                secondaryAppendageDegrees = 62f * sin(phase * PI).toFloat(),
                tertiaryAppendageDegrees = 22f * wave,
            )
        }
        return CharacterHopDynamics.apply(pose, motion, progress, .40f, hopPlayback)
    }

    override fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose, teamColor: LudoPawsTeamColor) = with(context) {
        val previousSurface = surface
        try {
            val rig = rigTransforms[0]
            System.arraycopy(parent, 0, rig, 0, 16)
            Matrix.rotateM(rig, 0, -12f, 0f, 1f, 0f)
            legs(rig, pose)
            val torso = rigTransforms[1]
            System.arraycopy(rig, 0, torso, 0, 16)
            Matrix.translateM(torso, 0, 0f, -pose.bodyCrouch, 0f)
            Matrix.rotateM(torso, 0, pose.bodyPitchDegrees, 1f, 0f, 0f)
            Matrix.rotateM(torso, 0, pose.bodyRollDegrees, 0f, 0f, 1f)
            val breath = 1f + (life?.breath ?: 0f)
            surface = CharacterSurface.COAT
            drawGeometry(torso, ElephantGeometry.body, 0f, 0f, 0f, breath, 1f, breath, ELEPHANT_SKIN)
            tail(torso, pose.tertiaryAppendageDegrees)
            val neck = rigTransforms[2]
            System.arraycopy(torso, 0, neck, 0, 16)
            Matrix.translateM(neck, 0, 0f, .34f, .10f)
            Matrix.rotateM(neck, 0, pose.neckPitchDegrees, 1f, 0f, 0f)
            Matrix.translateM(neck, 0, 0f, -.34f, -.10f)
            drawGeometry(neck, ElephantGeometry.neck, 0f, 0f, 0f, 1f, 1f, 1f, ELEPHANT_SKIN)
            prepareHead(neck, .92f, pose.headTiltDegrees)
            face(head, pose)
        } finally {
            surface = previousSurface
        }
    }

    private fun CharacterDrawContext.legs(parent: FloatArray, pose: CharacterPose) {
        for (legIndex in 0..3) {
            val rear = legIndex < 2
            val side = if (legIndex % 2 == 0) -1f else 1f
            val leg = rigTransforms[3]
            System.arraycopy(parent, 0, leg, 0, 16)
            Matrix.translateM(leg, 0, side * .30f, -.23f, if (rear) -.43f else .28f)
            val swing = (if (rear) -side else side) * pose.legSwingDegrees
            val tuck = (pose.legTuckDegrees / 38f).coerceIn(0f, 1f)
            Matrix.rotateM(leg, 0, swing, 1f, 0f, 0f)
            Matrix.scaleM(leg, 0, 1f, 1f - .30f * tuck, 1f)
            surface = CharacterSurface.COAT
            drawGeometry(leg, ElephantGeometry.leg, 0f, 0f, 0f, 1f, 1f, 1f, ELEPHANT_SKIN)
            drawGeometry(leg, ElephantGeometry.foot, 0f, -.545f, .02f, 1f, 1f, 1f, ELEPHANT_SKIN)
            for (nailIndex in -1..1) {
                drawPart(leg, nailIndex * .078f, -.56f, .161f,
                    .034f, .043f, .018f, ELEPHANT_NAIL)
            }
        }
    }

    private fun CharacterDrawContext.face(parent: FloatArray, pose: CharacterPose) {
        for (side in -1..1 step 2) {
            val ear = rigTransforms[4]
            System.arraycopy(parent, 0, ear, 0, 16)
            Matrix.translateM(ear, 0, side * .34f, .83f, -.035f)
            Matrix.rotateM(ear, 0, side * (12f + pose.primaryAppendageDegrees), 0f, 1f, 0f)
            surface = CharacterSurface.COAT
            drawGeometry(ear, ElephantGeometry.ear, 0f, 0f, 0f, side.toFloat(), 1f, 1f, ELEPHANT_SKIN)
            drawGeometry(ear, ElephantGeometry.ear, side * .025f, .005f, .058f,
                side * .78f, .80f, .35f, ELEPHANT_INNER_EAR)
        }
        drawGeometry(parent, ElephantGeometry.head, 0f, 0f, 0f, 1f, 1f, 1f, ELEPHANT_SKIN)
        for (side in -1..1 step 2) {
            surface = CharacterSurface.EYE
            drawLivingEye(parent, side * .235f, .88f, .325f,
                .085f, .10f, ELEPHANT_SKIN, WHITE, EYE_DARK, horizontal = false, slit = false)
            surface = CharacterSurface.HORN
            drawGeometry(parent, ElephantGeometry.tusk, side * .195f, .58f, .31f,
                1f, 1f, 1f, ELEPHANT_IVORY, 90f, side * 10f)
        }
        surface = CharacterSurface.MATTE
        val opening = ((life?.mouthOpen ?: 0f) + pose.jawOpen).coerceIn(0f, 1f)
        drawPart(parent, 0f, .49f, .34f, .13f, .012f + opening * .03f, .018f, ELEPHANT_SHADOW)
        trunk(parent, pose.secondaryAppendageDegrees + 32f * pose.gestureWeight)
    }

    private fun CharacterDrawContext.trunk(parent: FloatArray, curl: Float) {
        val trunk = rigTransforms[5]
        System.arraycopy(parent, 0, trunk, 0, 16)
        Matrix.translateM(trunk, 0, 0f, .79f, .345f)
        Matrix.rotateM(trunk, 0, -curl * .35f, 1f, 0f, 0f)
        surface = CharacterSurface.COAT
        for (segment in 0..5) {
            Matrix.rotateM(trunk, 0, -5f - curl * .20f - (if (segment > 2) 13f else 0f), 1f, 0f, 0f)
            val radius = .145f - segment * .014f
            drawGeometry(trunk, ElephantGeometry.trunk, 0f, -.08f, 0f,
                radius, 1f, radius, ELEPHANT_SKIN)
            Matrix.translateM(trunk, 0, 0f, -.145f, 0f)
        }
        drawPart(trunk, 0f, -.025f, 0f, .067f, .06f, .067f, ELEPHANT_SKIN)
        surface = CharacterSurface.NOSE
        for (side in -1..1 step 2) {
            drawPart(trunk, side * .025f, -.073f, .023f, .014f, .008f, .018f, ELEPHANT_SHADOW)
        }
    }

    private fun CharacterDrawContext.tail(parent: FloatArray, sway: Float) {
        val tail = rigTransforms[6]
        System.arraycopy(parent, 0, tail, 0, 16)
        Matrix.translateM(tail, 0, .29f, .10f, -.64f)
        Matrix.rotateM(tail, 0, -25f + sway, 0f, 0f, 1f)
        surface = CharacterSurface.COAT
        drawPart(tail, 0f, -.15f, 0f, .028f, .18f, .028f, ELEPHANT_SKIN)
        drawPart(tail, 0f, -.32f, 0f, .055f, .08f, .04f, ELEPHANT_SHADOW)
    }
}
