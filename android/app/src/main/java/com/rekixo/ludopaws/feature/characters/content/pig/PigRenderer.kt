package com.rekixo.ludopaws.feature.characters.content.pig

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.*

internal val PIG_PINK = floatArrayOf(1f, .64f, .69f, 1f)
internal val PIG_BLUSH = floatArrayOf(.91f, .35f, .45f, 1f)
internal val PIG_SNOUT = floatArrayOf(1f, .49f, .58f, 1f)
internal val PIG_HOOF = floatArrayOf(.30f, .20f, .24f, 1f)

internal object PigRenderer : CharacterRenderer {
    override fun stepTravelProgress(progress: Float) = CharacterHopDynamics.travel(progress)

    override fun sample(motion: CharacterMotion, progress: Float, hopPlayback: CharacterHopPlayback): CharacterPose {
        val phase = progress.coerceIn(0f, 1f)
        val wave = sin(phase * PI * 2.0).toFloat()
        val anatomyPhase = if (motion == CharacterMotion.HOP && hopPlayback == CharacterHopPlayback.SETTLE) 1f else phase
        val travel = stepTravelProgress(anatomyPhase)
        val arc = sin(travel * PI).toFloat().coerceAtLeast(0f)
        val pose = when (motion) {
            CharacterMotion.IDLE -> CharacterPose(
                headTiltDegrees = 3f * wave, primaryAppendageDegrees = 5f * wave,
                secondaryAppendageDegrees = 9f * wave, neckPitchDegrees = 2f * wave,
            )
            CharacterMotion.HOP -> CharacterPose(
                legSwingDegrees = 22f * wave * arc, legTuckDegrees = 26f * arc,
                primaryAppendageDegrees = -18f * arc, secondaryAppendageDegrees = 15f * arc,
                bodyPitchDegrees = -5f * arc, neckPitchDegrees = 4f * arc,
            )
            CharacterMotion.HOME -> CharacterPose(
                liftY = .18f * abs(sin(phase * PI * 3.0).toFloat()) * (1f - phase),
                bodyYawDegrees = 360f * phase, headTiltDegrees = 7f * wave,
                primaryAppendageDegrees = 16f * wave, secondaryAppendageDegrees = 24f * wave,
            )
        }
        return CharacterHopDynamics.apply(pose, motion, progress, .48f, hopPlayback)
    }

    override fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose, teamColor: LudoPawsTeamColor) = with(context) {
        val previousSurface = surface
        try {
            val rig = rigTransforms[0]
            System.arraycopy(parent, 0, rig, 0, 16)
            Matrix.rotateM(rig, 0, -18f, 0f, 1f, 0f)
            legs(rig, pose)
            val torso = rigTransforms[1]
            System.arraycopy(rig, 0, torso, 0, 16)
            Matrix.translateM(torso, 0, 0f, -pose.bodyCrouch, 0f)
            Matrix.rotateM(torso, 0, pose.bodyPitchDegrees, 1f, 0f, 0f)
            Matrix.rotateM(torso, 0, pose.bodyRollDegrees, 0f, 0f, 1f)
            val breath = 1f + (life?.breath ?: 0f)
            surface = CharacterSurface.COAT
            drawGeometry(torso, PigGeometry.body, 0f, 0f, 0f, breath, 1f, breath, PIG_PINK)
            tail(torso, pose.secondaryAppendageDegrees)
            val neck = rigTransforms[2]
            System.arraycopy(torso, 0, neck, 0, 16)
            Matrix.translateM(neck, 0, 0f, .30f, .10f)
            Matrix.rotateM(neck, 0, pose.neckPitchDegrees, 1f, 0f, 0f)
            Matrix.translateM(neck, 0, 0f, -.30f, -.10f)
            drawGeometry(neck, PigGeometry.neck, 0f, 0f, 0f, 1f, 1f, 1f, PIG_PINK)
            prepareHead(neck, .85f, pose.headTiltDegrees)
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
            Matrix.translateM(leg, 0, side * .29f, -.20f, if (rear) -.38f else .27f)
            val swing = (if (rear) -side else side) * pose.legSwingDegrees
            val tuck = (pose.legTuckDegrees / 38f).coerceIn(0f, 1f)
            Matrix.rotateM(leg, 0, swing, 1f, 0f, 0f)
            Matrix.scaleM(leg, 0, 1f, 1f - .35f * tuck - .30f * pose.bodyCrouch, 1f)
            surface = CharacterSurface.COAT
            drawGeometry(leg, PigGeometry.leg, 0f, 0f, 0f, 1f, 1f, 1f, PIG_PINK)
            surface = CharacterSurface.HORN
            for (toeSide in -1..1 step 2) {
                drawGeometry(leg, PigGeometry.hoof, toeSide * .055f, -.50f, .025f,
                    1f, 1f, 1f, PIG_HOOF)
            }
        }
    }

    private fun CharacterDrawContext.face(parent: FloatArray, pose: CharacterPose) {
        surface = CharacterSurface.COAT
        drawGeometry(parent, PigGeometry.head, 0f, 0f, 0f, 1f, 1f, 1f, PIG_PINK)
        for (side in -1..1 step 2) {
            val ear = rigTransforms[4]
            System.arraycopy(parent, 0, ear, 0, 16)
            Matrix.translateM(ear, 0, side * .27f, 1.01f, .015f)
            Matrix.rotateM(ear, 0, -side * (22f + pose.primaryAppendageDegrees + side * 12f * pose.gestureWeight), 0f, 0f, 1f)
            drawGeometry(ear, PigGeometry.ear, 0f, 0f, 0f, side.toFloat(), 1f, 1f, PIG_PINK)
            drawGeometry(ear, PigGeometry.ear, 0f, .035f, .047f, side * .62f, .75f, .32f, PIG_BLUSH)
            surface = CharacterSurface.EYE
            drawLivingEye(parent, side * .21f, .86f, .34f,
                .082f, .095f, PIG_PINK, WHITE, EYE_DARK, horizontal = false, slit = false)
            surface = CharacterSurface.COAT
            drawPart(parent, side * .30f, .68f, .31f, .067f, .037f, .026f, PIG_BLUSH)
        }
        surface = CharacterSurface.NOSE
        drawGeometry(parent, PigGeometry.snout, .012f * pose.gestureWeight * sin(pose.gesturePhase * PI.toFloat() * 6f), .70f, .30f, 1f, 1f, 1f, PIG_SNOUT, 90f)
        for (side in -1..1 step 2) {
            drawPart(parent, side * .085f, .70f, .558f, .034f, .046f, .010f, PIG_HOOF, rotateZ = side * 12f)
        }
        val opening = ((life?.mouthOpen ?: 0f) + pose.jawOpen).coerceIn(0f, 1f)
        surface = CharacterSurface.MATTE
        drawPart(parent, 0f, .535f, .37f, .105f, .009f + opening * .035f, .018f, PIG_HOOF)
        surface = CharacterSurface.COAT
        drawPart(parent, 0f, .509f - opening * .035f, .36f, .12f, .027f, .045f, PIG_PINK)
    }

    private fun CharacterDrawContext.tail(parent: FloatArray, sway: Float) {
        val tail = rigTransforms[5]
        System.arraycopy(parent, 0, tail, 0, 16)
        Matrix.translateM(tail, 0, .28f, .13f, -.55f)
        Matrix.rotateM(tail, 0, sway, 0f, 1f, 0f)
        surface = CharacterSurface.COAT
        val radius = .125f
        for (segment in 0..17) {
            val startAngle = segment * PI.toFloat() * 3f / 18f
            val endAngle = (segment + 1) * PI.toFloat() * 3f / 18f
            val startX = radius * sin(startAngle)
            val endX = radius * sin(endAngle)
            val startY = radius * (1f - cos(startAngle))
            val endY = radius * (1f - cos(endAngle))
            val deltaX = endX - startX
            val deltaY = endY - startY
            val length = sqrt(deltaX * deltaX + deltaY * deltaY)
            drawPart(tail, (startX + endX) * .5f, (startY + endY) * .5f, -segment * .005f,
                .022f, length * .65f, .022f, PIG_SNOUT,
                rotateZ = -atan2(deltaX, deltaY) * 180f / PI.toFloat())
        }
    }
}
