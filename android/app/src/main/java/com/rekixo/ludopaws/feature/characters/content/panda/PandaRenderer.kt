package com.rekixo.ludopaws.feature.characters.content.panda

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.*

private val PANDA_COAT = floatArrayOf(.94f, .93f, .89f, 1f)
private val PANDA_DARK = floatArrayOf(.045f, .052f, .065f, 1f)
private val PANDA_PAD = floatArrayOf(.16f, .17f, .19f, 1f)

/** Genuine scene geometry shared by board pawns, Shop previews and portrait rendering. */
internal object PandaRenderer : CharacterRenderer {
    override fun stepTravelProgress(progress: Float) = CharacterHopDynamics.travel(progress)

    override fun sample(motion: CharacterMotion, progress: Float, hopPlayback: CharacterHopPlayback): CharacterPose {
        val p = progress.coerceIn(0f, 1f)
        val wave = sin(p * PI * 2.0).toFloat()
        val anatomy = if (hopPlayback == CharacterHopPlayback.SETTLE) 1f else p
        val arc = sin(stepTravelProgress(anatomy) * PI).toFloat().coerceAtLeast(0f)
        val pose = when (motion) {
            CharacterMotion.IDLE -> CharacterPose(
                headTiltDegrees = 7f * wave, bodyRollDegrees = 2.5f * wave,
                primaryAppendageDegrees = 17f * wave,
                secondaryAppendageDegrees = 8f * wave,
            )
            CharacterMotion.HOP -> CharacterPose(
                legTuckDegrees = 24f * arc, legSwingDegrees = -14f * arc,
                bodyPitchDegrees = -5f * arc, neckPitchDegrees = 4f * arc,
                primaryAppendageDegrees = 18f * arc,
            )
            CharacterMotion.HOME -> CharacterPose(
                liftY = .15f * abs(sin(p * PI * 3.0).toFloat()) * (1f - p),
                bodyYawDegrees = 360f * p, headTiltDegrees = 6f * wave,
                primaryAppendageDegrees = 26f * wave,
            )
        }
        return CharacterHopDynamics.apply(pose, motion, progress, .52f, hopPlayback)
    }

    override fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose, teamColor: LudoPawsTeamColor) = with(context) {
        val previousSurface = surface
        try {
            surface = CharacterSurface.SOFT_FUR
            // The life clock continues across short idle loops; posture never resets per loop.
            // Its idle weight fades out on movement/reactions so the Panda stands to respond.
            val q = life?.let { it.followStance(PandaPosture.allFours(it.timeSeconds, it.idleWeight)) } ?: 0f
            val model = rigTransforms[6]
            System.arraycopy(parent, 0, model, 0, 16)
            val size = PandaDefinition.MODEL_SIZE_MULTIPLIER
            Matrix.scaleM(model, 0, size, size, size)
            val root = rigTransforms[0]
            System.arraycopy(model, 0, root, 0, 16)
            Matrix.translateM(root, 0, 0f, -pose.bodyCrouch, 0f)
            Matrix.rotateM(root, 0, pose.bodyPitchDegrees, 1f, 0f, 0f)
            Matrix.rotateM(root, 0, pose.bodyRollDegrees * (1f - q), 0f, 0f, 1f)
            val torso = rigTransforms[1]
            System.arraycopy(root, 0, torso, 0, 16)
            Matrix.translateM(torso, 0, 0f, -.16f * q, -.08f * q)
            Matrix.rotateM(torso, 0, 65f * q, 1f, 0f, 0f)
            val breath = 1f + (life?.breath ?: 0f)
            // Broad shoulder band, white round belly and a small white tail.
            drawPart(torso, 0f, -.02f, 0f, .39f * breath, .46f, .31f * breath, PANDA_COAT)
            drawPart(torso, 0f, .26f, -.015f, .40f, .19f, .29f, PANDA_DARK)
            drawPart(torso, 0f, -.15f, -.32f, .12f, .12f, .12f, PANDA_COAT)
            for (side in -1..1 step 2) {
                val arm = rigTransforms[2]
                System.arraycopy(root, 0, arm, 0, 16)
                Matrix.translateM(arm, 0, side * .35f, .25f - .40f * q, .32f * q)
                // Fold onto the front paws, then free the arms for an asymmetric greeting.
                val waveArm = pose.primaryAppendageDegrees * (if (side == 1) 1f else .45f)
                Matrix.rotateM(arm, 0, side * (10f + waveArm) * (1f - q), 0f, 0f, 1f)
                Matrix.rotateM(arm, 0, pose.secondaryAppendageDegrees * (1f - q), 1f, 0f, 0f)
                drawPart(arm, side * .045f * (1f - q), -.21f - .02f * q, .035f,
                    .14f, .29f, .15f, PANDA_DARK)
                drawPart(arm, 0f, -.43f - .04f * q, .065f + .065f * q,
                    .16f, .10f, .19f, PANDA_DARK)
                val leg = rigTransforms[3]
                System.arraycopy(model, 0, leg, 0, 16)
                Matrix.translateM(leg, 0, side * .23f, -.35f, -.025f - .24f * q)
                Matrix.rotateM(leg, 0, pose.legSwingDegrees, 1f, 0f, 0f)
                val tuck = (pose.legTuckDegrees / 38f).coerceIn(0f, 1f)
                drawPart(leg, 0f, -.13f + tuck * .10f, .02f, .18f, .23f, .18f, PANDA_DARK)
                drawPart(leg, 0f, -.27f + tuck * .16f, .12f, .19f, .10f, .23f, PANDA_DARK)
                drawPart(leg, 0f, -.27f + tuck * .16f, .324f, .10f, .052f, .015f, PANDA_PAD)
            }
            val neck = rigTransforms[4]
            System.arraycopy(root, 0, neck, 0, 16)
            // Keep the face looking forward when the shoulders lower onto four paws.
            Matrix.translateM(neck, 0, 0f, -.45f * q, .43f * q)
            Matrix.translateM(neck, 0, 0f, .36f, 0f)
            Matrix.rotateM(neck, 0, pose.neckPitchDegrees, 1f, 0f, 0f)
            Matrix.translateM(neck, 0, 0f, -.36f, 0f)
            prepareHead(neck, .74f, pose.headTiltDegrees)
            drawPart(head, 0f, .69f, .025f, .46f, .41f, .34f, PANDA_COAT)
            for (side in -1..1 step 2) {
                drawPart(head, side * .34f, 1.00f, -.015f, .16f, .17f, .12f, PANDA_DARK)
                drawPart(head, side * .34f, 1.00f, .09f, .085f, .095f, .025f, PANDA_PAD)
                drawPart(head, side * .185f, .73f, .304f, .145f, .18f, .045f,
                    PANDA_DARK, rotateZ = -side * 23f)
                surface = CharacterSurface.EYE
                drawLivingEye(head, side * .185f, .75f, .34f,
                    .062f, .078f, PANDA_DARK, WHITE, EYE_DARK, horizontal = false, slit = false)
                surface = CharacterSurface.SOFT_FUR
                drawPart(head, side * .085f, .53f, .327f, .12f, .085f, .07f, PANDA_COAT)
            }
            surface = CharacterSurface.NOSE
            drawPart(head, 0f, .585f, .405f, .07f, .047f, .035f, PANDA_DARK)
            surface = CharacterSurface.MATTE
            val mouth = ((life?.mouthOpen ?: 0f) + pose.jawOpen).coerceIn(0f, 1f)
            drawPart(head, 0f, .478f, .369f, .065f, .009f + mouth * .03f, .014f, PANDA_DARK)
        } finally {
            surface = previousSurface
        }
    }
}
