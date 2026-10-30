package com.rekixo.ludopaws.feature.characters.content.cock

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.PI
import kotlin.math.sin

internal val COCK_RED = floatArrayOf(.83f, .09f, .08f, 1f)
internal val COCK_GOLD = floatArrayOf(1f, .64f, .12f, 1f)
internal val COCK_COAT = floatArrayOf(.65f, .22f, .09f, 1f)
internal val COCK_TAIL = floatArrayOf(.025f, .28f, .23f, 1f)
internal val COCK_TEAL = floatArrayOf(.04f, .46f, .37f, 1f)

internal object CockRenderer : CharacterRenderer {
    override fun stepTravelProgress(progress: Float) = CharacterHopDynamics.travel(progress)

    override fun sample(motion: CharacterMotion, progress: Float, hopPlayback: CharacterHopPlayback): CharacterPose {
        val phase = progress.coerceIn(0f, 1f)
        val wave = sin(phase * PI * 2.0).toFloat()
        val anatomyPhase = if (motion == CharacterMotion.HOP && hopPlayback == CharacterHopPlayback.SETTLE) 1f else phase
        val travel = stepTravelProgress(anatomyPhase)
        val arc = sin(travel * PI).toFloat().coerceAtLeast(0f)
        val pose = when (motion) {
            CharacterMotion.IDLE -> CharacterPose(
                headTiltDegrees = 3f * wave, neckPitchDegrees = -2f * wave,
                primaryAppendageDegrees = 3f * wave, secondaryAppendageDegrees = 4f * wave,
            )
            CharacterMotion.HOP -> CharacterPose(
                liftY = .54f * 4f * travel * (1f - travel), legTuckDegrees = 32f * arc,
                legPaddleDegrees = 34f * wave * arc, primaryAppendageDegrees = 38f * arc,
                secondaryAppendageDegrees = -12f * arc, bodyPitchDegrees = -5f * arc,
                headTiltDegrees = -5f * arc,
            )
            CharacterMotion.HOME -> CharacterPose(
                liftY = .20f * kotlin.math.abs(sin(phase * PI * 3.0).toFloat()) * (1f - phase),
                bodyYawDegrees = 360f * phase, primaryAppendageDegrees = 48f * sin(phase * PI).toFloat(),
                neckPitchDegrees = -10f * sin(phase * PI).toFloat(), secondaryAppendageDegrees = 10f * wave,
            )
        }
        return CharacterHopDynamics.apply(pose, motion, progress, .54f, hopPlayback)
    }

    override fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose, teamColor: LudoPawsTeamColor) = with(context) {
        val previousSurface = surface
        try {
            val torso = rigTransforms[0]
            System.arraycopy(parent, 0, torso, 0, 16)
            Matrix.translateM(torso, 0, 0f, -pose.bodyCrouch, 0f)
            Matrix.rotateM(torso, 0, pose.bodyPitchDegrees, 1f, 0f, 0f)
            Matrix.rotateM(torso, 0, pose.bodyRollDegrees, 0f, 0f, 1f)
            feet(parent, pose)
            surface = CharacterSurface.BODY_COAT
            val breath = 1f + (life?.breath ?: 0f)
            drawGeometry(torso, CockGeometry.body, 0f, 0f, 0f, breath, 1f, breath * (1f + .07f * pose.gestureWeight), COCK_COAT)
            surface = CharacterSurface.COAT
            for (featherIndex in -2..2) {
                val spread = featherIndex.toFloat()
                drawGeometry(torso, CockGeometry.feather, spread * .065f, .02f, -.38f,
                    1f, 1.20f - kotlin.math.abs(spread) * .10f, 1f,
                    if (featherIndex % 2 == 0) COCK_TAIL else COCK_TEAL,
                    -28f + pose.secondaryAppendageDegrees, 0f, spread * 19f)
            }
            for (side in -1..1 step 2) wing(torso, side.toFloat(), pose)
            val neck = rigTransforms[2]
            System.arraycopy(torso, 0, neck, 0, 16)
            Matrix.translateM(neck, 0, 0f, .30f, 0f)
            Matrix.rotateM(neck, 0, pose.neckPitchDegrees, 1f, 0f, 0f)
            Matrix.translateM(neck, 0, 0f, -.30f, 0f)
            drawGeometry(neck, CockGeometry.neck, 0f, 0f, 0f, 1f, 1f, 1f, COCK_GOLD)
            for (featherIndex in -2..2) {
                drawPart(neck, featherIndex * .087f, .36f, .24f,
                    .055f, .17f, .035f, COCK_COAT, rotateZ = featherIndex * 9f)
            }
            prepareHead(neck, .97f, pose.headTiltDegrees)
            face(head, pose)
        } finally {
            surface = previousSurface
        }
    }

    private fun CharacterDrawContext.wing(parent: FloatArray, side: Float, pose: CharacterPose) {
        val wing = rigTransforms[1]
        System.arraycopy(parent, 0, wing, 0, 16)
        Matrix.translateM(wing, 0, side * .37f, .19f, -.02f)
        Matrix.rotateM(wing, 0, side * (12f + pose.primaryAppendageDegrees), 0f, 0f, 1f)
        drawGeometry(wing, CockGeometry.wing, 0f, 0f, 0f, 1f, 1f, 1f, COCK_COAT)
        for (featherIndex in 0..2) {
            drawPart(wing, side * (featherIndex - 1) * .07f, -.23f, .06f,
                .045f, .17f, .035f, if (featherIndex == 1) COCK_TEAL else COCK_TAIL,
                rotateZ = side * (featherIndex - 1) * 10f)
        }
    }

    private fun CharacterDrawContext.feet(parent: FloatArray, pose: CharacterPose) {
        val tuck = (pose.legTuckDegrees / 38f).coerceIn(0f, 1f)
        surface = CharacterSurface.NOSE
        for (side in -1..1 step 2) {
            val foot = rigTransforms[3]
            System.arraycopy(parent, 0, foot, 0, 16)
            Matrix.translateM(foot, 0, side * .19f, -.37f, .02f)
            Matrix.rotateM(foot, 0, side * pose.legPaddleDegrees, 1f, 0f, 0f)
            drawPart(foot, 0f, -.17f + tuck * .07f, 0f, .045f, .18f - tuck * .07f, .045f, COCK_GOLD)
            for (toeIndex in -1..1) {
                drawPart(foot, toeIndex * .065f, -.37f + tuck * .14f, .10f,
                    .029f, .04f, .15f, COCK_GOLD, rotateY = toeIndex * 22f)
            }
            drawPart(foot, 0f, -.37f + tuck * .14f, -.09f, .025f, .035f, .09f, COCK_GOLD)
        }
    }

    private fun CharacterDrawContext.face(parent: FloatArray, pose: CharacterPose) {
        surface = CharacterSurface.COAT
        drawGeometry(parent, CockGeometry.head, 0f, 0f, 0f, 1f, 1f, 1f, COCK_GOLD)
        for (side in -1..1 step 2) {
            surface = CharacterSurface.EYE
            drawLivingEye(parent, side * .155f, .99f, .245f,
                .095f, .115f, COCK_RED, WHITE, EYE_DARK, horizontal = false, slit = false)
            surface = CharacterSurface.NOSE
            drawPart(parent, side * .067f, .66f, .235f, .065f, .14f, .06f, COCK_RED)
        }
        for (combIndex in 0..3) {
            drawPart(parent, 0f, 1.20f + (if (combIndex == 1 || combIndex == 2) .10f else .04f),
                .14f - combIndex * .105f, .065f, .14f, .09f, COCK_RED)
        }
        val opening = ((life?.mouthOpen ?: 0f) + pose.jawOpen).coerceIn(0f, 1f)
        surface = CharacterSurface.NOSE
        drawGeometry(parent, CockGeometry.beak, 0f, .84f, .245f, 1f, 1f, 1f, COCK_GOLD, 90f)
        val jaw = rigTransforms[4]
        System.arraycopy(parent, 0, jaw, 0, 16)
        Matrix.translateM(jaw, 0, 0f, .80f, .245f)
        Matrix.rotateM(jaw, 0, opening * 25f, 1f, 0f, 0f)
        drawGeometry(jaw, CockGeometry.beak, 0f, 0f, 0f, .85f, .85f, .50f, COCK_COAT, 90f)
    }
}
