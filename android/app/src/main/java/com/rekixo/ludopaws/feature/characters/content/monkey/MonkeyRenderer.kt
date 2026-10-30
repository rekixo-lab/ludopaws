package com.rekixo.ludopaws.feature.characters.content.monkey

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.*

private val MONKEY_FUR = floatArrayOf(.38f, .19f, .085f, 1f)
private val MONKEY_FACE = floatArrayOf(.88f, .66f, .40f, 1f)
private val MONKEY_DARK = floatArrayOf(.14f, .065f, .035f, 1f)
private val MONKEY_EAR = floatArrayOf(.67f, .39f, .25f, 1f)

/** Articulated long arms, expressive face and a continuous curling tail. */
internal object MonkeyRenderer : CharacterRenderer {
    override fun stepTravelProgress(progress: Float) = CharacterHopDynamics.travel(progress)

    override fun sample(motion: CharacterMotion, progress: Float, hopPlayback: CharacterHopPlayback): CharacterPose {
        val p = progress.coerceIn(0f, 1f)
        val wave = sin(p * PI * 2).toFloat()
        val anatomy = if (hopPlayback == CharacterHopPlayback.SETTLE) 1f else p
        val arc = sin(stepTravelProgress(anatomy) * PI).toFloat().coerceAtLeast(0f)
        val pose = when (motion) {
            CharacterMotion.IDLE -> CharacterPose(
                headTiltDegrees = 9f * wave, primaryAppendageDegrees = 22f * wave,
                secondaryAppendageDegrees = 12f * wave, tertiaryAppendageDegrees = 18f * wave,
                bodyRollDegrees = 3f * wave,
            )
            CharacterMotion.HOP -> CharacterPose(
                legTuckDegrees = 32f * arc, legSwingDegrees = -22f * arc,
                primaryAppendageDegrees = 42f * arc, secondaryAppendageDegrees = -24f * arc,
                tertiaryAppendageDegrees = -35f * arc, bodyPitchDegrees = -9f * arc,
                neckPitchDegrees = 7f * arc,
            )
            CharacterMotion.HOME -> CharacterPose(
                liftY = .24f * abs(sin(p * PI * 3).toFloat()) * (1f - p),
                bodyYawDegrees = 360f * p, primaryAppendageDegrees = 65f * wave,
                secondaryAppendageDegrees = 28f * wave, tertiaryAppendageDegrees = 32f * wave,
                headTiltDegrees = 10f * wave,
            )
        }
        return CharacterHopDynamics.apply(pose, motion, progress, .64f, hopPlayback)
    }

    override fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose, teamColor: LudoPawsTeamColor) = with(context) {
        val oldSurface = surface
        try {
            surface = CharacterSurface.SOFT_FUR
            val root = rigTransforms[0]
            System.arraycopy(parent, 0, root, 0, 16)
            Matrix.translateM(root, 0, 0f, -pose.bodyCrouch, 0f)
            Matrix.rotateM(root, 0, pose.bodyPitchDegrees, 1f, 0f, 0f)
            Matrix.rotateM(root, 0, pose.bodyRollDegrees, 0f, 0f, 1f)
            val breath = 1f + (life?.breath ?: 0f)
            drawPart(root, 0f, -.035f, -.015f, .27f * breath, .38f, .23f * breath, MONKEY_FUR)
            drawPart(root, 0f, -.035f, .19f, .18f, .27f, .065f, MONKEY_FACE)
            tail(root, pose.tertiaryAppendageDegrees)
            // A slow head-scratch gesture is independent of the short idle loop.
            val scratch = life?.let {
                val phase = ((it.timeSeconds % 9f) / 9f) * PI.toFloat()
                sin(phase).pow(16) * it.idleWeight
            } ?: 0f
            for (side in -1..1 step 2) {
                val arm = rigTransforms[1]
                System.arraycopy(root, 0, arm, 0, 16)
                Matrix.translateM(arm, 0, side * .255f, .22f, 0f)
                val greeting = pose.primaryAppendageDegrees * if (side == 1) 1f else .45f
                val scratchWeight = if (side == 1) scratch else 0f
                val armAngle = (12f + greeting) * (1f - scratchWeight) + 155f * scratchWeight
                Matrix.rotateM(arm, 0, side * armAngle, 0f, 0f, 1f)
                Matrix.rotateM(arm, 0, pose.secondaryAppendageDegrees, 1f, 0f, 0f)
                drawPart(arm, side * .025f, -.19f, 0f, .09f, .24f, .09f, MONKEY_FUR)
                val elbow = rigTransforms[2]
                System.arraycopy(arm, 0, elbow, 0, 16)
                Matrix.translateM(elbow, 0, side * .025f, -.36f, 0f)
                Matrix.rotateM(elbow, 0, -18f - (if (side == 1) 55f * scratch else 0f), 1f, 0f, 0f)
                drawPart(elbow, 0f, -.14f, 0f, .075f, .19f, .08f, MONKEY_FUR)
                drawPart(elbow, 0f, -.32f, .02f, .105f, .09f, .075f, MONKEY_FACE)
                drawPart(elbow, -side * .085f, -.28f, .04f, .038f, .065f, .04f, MONKEY_FACE)
                val leg = rigTransforms[3]
                System.arraycopy(parent, 0, leg, 0, 16)
                Matrix.translateM(leg, 0, side * .16f, -.34f, -.025f)
                Matrix.rotateM(leg, 0, pose.legSwingDegrees, 1f, 0f, 0f)
                val tuck = (pose.legTuckDegrees / 38f).coerceIn(0f, 1f)
                drawPart(leg, 0f, -.12f + tuck * .10f, 0f, .115f, .21f, .12f, MONKEY_FUR)
                drawPart(leg, 0f, -.30f + tuck * .18f, .09f, .13f, .08f, .19f, MONKEY_FACE)
            }
            val neck = rigTransforms[4]
            System.arraycopy(root, 0, neck, 0, 16)
            Matrix.translateM(neck, 0, 0f, .32f, 0f)
            Matrix.rotateM(neck, 0, pose.neckPitchDegrees, 1f, 0f, 0f)
            Matrix.translateM(neck, 0, 0f, -.32f, 0f)
            prepareHead(neck, .68f, pose.headTiltDegrees)
            drawPart(head, 0f, .66f, 0f, .34f, .36f, .28f, MONKEY_FUR)
            for (side in -1..1 step 2) {
                drawPart(head, side * .35f, .64f, -.005f, .15f, .17f, .085f, MONKEY_FUR)
                drawPart(head, side * .365f, .64f, .068f, .09f, .11f, .025f, MONKEY_EAR)
                drawPart(head, side * .13f, .68f, .225f, .17f, .21f, .075f, MONKEY_FACE)
                surface = CharacterSurface.EYE
                drawLivingEye(head, side * .135f, .71f, .295f, .067f, .087f,
                    MONKEY_FACE, WHITE, EYE_DARK, horizontal = false, slit = false)
                surface = CharacterSurface.SOFT_FUR
            }
            drawPart(head, 0f, .50f, .265f, .22f, .13f, .105f, MONKEY_FACE)
            surface = CharacterSurface.NOSE
            drawPart(head, 0f, .56f, .363f, .06f, .035f, .025f, MONKEY_DARK)
            surface = CharacterSurface.MATTE
            val mouth = ((life?.mouthOpen ?: 0f) + pose.jawOpen).coerceIn(0f, 1f)
            drawPart(head, 0f, .445f, .35f, .09f, .009f + .045f * mouth, .014f, MONKEY_DARK)
            drawPart(head, -.055f, .46f, .348f, .04f, .008f, .012f, MONKEY_DARK, rotateZ = -18f)
            drawPart(head, .055f, .46f, .348f, .04f, .008f, .012f, MONKEY_DARK, rotateZ = 18f)
            surface = CharacterSurface.SOFT_FUR
            drawPart(head, 0f, 1.00f, -.01f, .085f, .12f, .07f, MONKEY_FUR, rotateZ = -23f)
        } finally {
            surface = oldSurface
        }
    }

    private fun CharacterDrawContext.tail(parent: FloatArray, wag: Float) {
        val tail = rigTransforms[5]
        System.arraycopy(parent, 0, tail, 0, 16)
        Matrix.translateM(tail, 0, 0f, -.22f, -.20f)
        Matrix.rotateM(tail, 0, wag, 0f, 1f, 0f)
        // Closely overlapping rounded sections form a hooked silhouette without gaps.
        for (i in 0..18) {
            val u = i / 18f
            val angle = u * PI.toFloat() * 1.35f
            val radius = .065f - .025f * u
            drawPart(tail, .26f * (1f - cos(angle)), .20f * u + .30f * sin(angle), -.34f * u,
                radius, radius, radius, if (i == 18) MONKEY_DARK else MONKEY_FUR)
        }
    }
}
