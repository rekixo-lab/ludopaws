package com.rekixo.ludopaws.feature.characters.content.rabbit

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.*

internal val RABBIT_CREAM = floatArrayOf(.91f, .86f, .76f, 1f)
internal val RABBIT_WHITE = floatArrayOf(1f, .97f, .91f, 1f)
internal val RABBIT_PINK = floatArrayOf(.86f, .54f, .57f, 1f)
internal val RABBIT_NOSE = floatArrayOf(.74f, .36f, .42f, 1f)
internal val RABBIT_WHISKER = floatArrayOf(.46f, .41f, .37f, 1f)

internal object RabbitRenderer : CharacterRenderer {
    override fun stepTravelProgress(progress: Float) = CharacterHopDynamics.travel(progress)

    override fun sample(motion: CharacterMotion, progress: Float, hopPlayback: CharacterHopPlayback): CharacterPose {
        val phase = progress.coerceIn(0f, 1f)
        val wave = sin(phase * PI * 2.0).toFloat()
        val anatomyPhase = if (motion == CharacterMotion.HOP && hopPlayback == CharacterHopPlayback.SETTLE) 1f else phase
        val arc = sin(stepTravelProgress(anatomyPhase) * PI).toFloat().coerceAtLeast(0f)
        val pose = when (motion) {
            CharacterMotion.IDLE -> CharacterPose(
                headTiltDegrees = 3f * wave, primaryAppendageDegrees = 5f * wave,
                secondaryAppendageDegrees = 3f * wave, neckPitchDegrees = 2f * wave,
            )
            CharacterMotion.HOP -> CharacterPose(
                frontLegTuckDegrees = 30f * arc, rearLegTuckDegrees = 36f * arc,
                frontLegSwingDegrees = -18f * arc, rearLegSwingDegrees = 24f * arc,
                primaryAppendageDegrees = -24f * arc, secondaryAppendageDegrees = 10f * arc,
                bodyPitchDegrees = -7f * arc, neckPitchDegrees = 5f * arc,
            )
            CharacterMotion.HOME -> CharacterPose(
                liftY = .22f * abs(sin(phase * PI * 3.0).toFloat()) * (1f - phase),
                bodyYawDegrees = 360f * phase, primaryAppendageDegrees = 16f * wave,
                headTiltDegrees = 7f * wave, secondaryAppendageDegrees = 12f * wave,
            )
        }
        return CharacterHopDynamics.apply(pose, motion, progress, .63f, hopPlayback)
    }

    override fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose, teamColor: LudoPawsTeamColor) = with(context) {
        val previousSurface = surface
        try {
            val rig = rigTransforms[0]
            System.arraycopy(parent, 0, rig, 0, 16)
            Matrix.rotateM(rig, 0, -16f, 0f, 1f, 0f)
            legs(rig, pose)
            val torso = rigTransforms[1]
            System.arraycopy(rig, 0, torso, 0, 16)
            Matrix.translateM(torso, 0, 0f, -pose.bodyCrouch, 0f)
            Matrix.rotateM(torso, 0, pose.bodyPitchDegrees, 1f, 0f, 0f)
            Matrix.rotateM(torso, 0, pose.bodyRollDegrees, 0f, 0f, 1f)
            val breath = 1f + (life?.breath ?: 0f)
            surface = CharacterSurface.SOFT_FUR
            drawGeometry(torso, RabbitGeometry.body, 0f, 0f, 0f, breath, 1f, breath, RABBIT_CREAM)
            drawPart(torso, 0f, -.01f, .28f, .25f, .30f, .11f, RABBIT_WHITE)
            drawPart(torso, .25f, -.10f + pose.secondaryAppendageDegrees * .0015f, -.48f,
                .17f, .16f, .17f, RABBIT_WHITE, rotateZ = pose.secondaryAppendageDegrees)
            val neck = rigTransforms[2]
            System.arraycopy(torso, 0, neck, 0, 16)
            Matrix.translateM(neck, 0, 0f, .34f, 0f)
            Matrix.rotateM(neck, 0, pose.neckPitchDegrees, 1f, 0f, 0f)
            Matrix.translateM(neck, 0, 0f, -.34f, 0f)
            drawGeometry(neck, RabbitGeometry.neck, 0f, 0f, 0f, 1f, 1f, 1f, RABBIT_CREAM)
            prepareHead(neck, .86f, pose.headTiltDegrees)
            face(head, pose)
        } finally {
            surface = previousSurface
        }
    }

    private fun CharacterDrawContext.legs(parent: FloatArray, pose: CharacterPose) {
        surface = CharacterSurface.SOFT_FUR
        for (side in -1..1 step 2) {
            val hind = rigTransforms[3]
            System.arraycopy(parent, 0, hind, 0, 16)
            val rearTuck = ((pose.rearLegTuckDegrees ?: pose.legTuckDegrees) / 38f).coerceIn(0f, 1f)
            val rearSwing = pose.rearLegSwingDegrees ?: pose.legSwingDegrees
            Matrix.translateM(hind, 0, side * .28f, -.28f, -.23f)
            Matrix.rotateM(hind, 0, rearSwing, 1f, 0f, 0f)
            drawPart(hind, 0f, -.03f - pose.bodyCrouch * .35f, 0f, .19f, .25f, .24f, RABBIT_CREAM)
            drawGeometry(hind, RabbitGeometry.hindPaw, 0f, -.385f + rearTuck * .21f, .15f,
                1f, 1f, 1f, RABBIT_WHITE)
            val front = rigTransforms[4]
            System.arraycopy(parent, 0, front, 0, 16)
            val frontTuck = ((pose.frontLegTuckDegrees ?: pose.legTuckDegrees) / 38f).coerceIn(0f, 1f)
            Matrix.translateM(front, 0, side * .17f, -.09f, .30f)
            Matrix.rotateM(front, 0, pose.frontLegSwingDegrees ?: pose.legSwingDegrees, 1f, 0f, 0f)
            Matrix.scaleM(front, 0, 1f, 1f - .35f * frontTuck, 1f)
            drawGeometry(front, RabbitGeometry.arm, 0f, 0f, 0f, 1f, 1f, 1f, RABBIT_CREAM)
            drawGeometry(front, RabbitGeometry.forePaw, 0f, -.585f, .025f, 1f, 1f, 1f, RABBIT_WHITE)
        }
    }

    private fun CharacterDrawContext.face(parent: FloatArray, pose: CharacterPose) {
        surface = CharacterSurface.SOFT_FUR
        drawGeometry(parent, RabbitGeometry.head, 0f, 0f, 0f, 1f, 1f, 1f, RABBIT_CREAM)
        for (side in -1..1 step 2) {
            val ear = rigTransforms[3]
            System.arraycopy(parent, 0, ear, 0, 16)
            Matrix.translateM(ear, 0, side * .17f, 1.06f, -.015f)
            Matrix.rotateM(ear, 0, -side * (9f + pose.primaryAppendageDegrees * .45f), 0f, 0f, 1f)
            Matrix.rotateM(ear, 0, 7f + pose.primaryAppendageDegrees, 1f, 0f, 0f)
            drawGeometry(ear, RabbitGeometry.ear, 0f, 0f, 0f, side.toFloat(), 1f, 1f, RABBIT_CREAM)
            drawGeometry(ear, RabbitGeometry.ear, side * .006f, .07f, .052f,
                side * .57f, .77f, .33f, RABBIT_PINK)
            surface = CharacterSurface.EYE
            drawLivingEye(parent, side * .19f, .85f, .292f,
                .095f, .12f, RABBIT_CREAM, RABBIT_WHITE, EYE_DARK, horizontal = false, slit = false)
            surface = CharacterSurface.SOFT_FUR
            drawPart(parent, side * .09f, .61f, .32f, .125f, .09f, .07f, RABBIT_WHITE)
            for (whiskerIndex in -1..1) {
                drawPart(parent, side * .24f, .625f + whiskerIndex * .025f, .337f,
                    .12f, .003f, .004f, RABBIT_WHISKER, rotateZ = side * whiskerIndex * 10f)
            }
        }
        val sniff = sin((life?.timeSeconds ?: 0f) * 9f) * .006f * (life?.idleWeight ?: 0f)
        surface = CharacterSurface.NOSE
        drawPart(parent, 0f, .675f + sniff, .389f, .044f, .030f, .023f, RABBIT_NOSE)
        val opening = ((life?.mouthOpen ?: 0f) + pose.jawOpen).coerceIn(0f, 1f)
        surface = CharacterSurface.MATTE
        drawPart(parent, 0f, .545f, .347f, .060f, .007f + opening * .025f, .012f, RABBIT_WHISKER)
        for (side in -1..1 step 2) {
            drawPart(parent, side * .017f, .532f - opening * .012f, .363f,
                .014f, .027f, .010f, WHITE)
        }
    }
}
