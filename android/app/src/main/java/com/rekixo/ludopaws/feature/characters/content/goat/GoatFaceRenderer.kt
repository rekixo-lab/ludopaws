package com.rekixo.ludopaws.feature.characters.content.goat

import android.opengl.Matrix
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.*

/** Skull, jaw, ears and beard share proper pivots; eyes have amber irises and horizontal pupils. */
internal object GoatFaceRenderer {
    fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose) = with(context) {
        surface = CharacterSurface.COAT
        // Nostrils flare slightly with each breath.
        val flare = 1f + ((life?.breath ?: 0f) * 4f).coerceIn(-.1f, .15f)
        goatMesh(parent, GoatGeometry.head, GOAT_CREAM)
        // Nasal bridge slopes into the longer muzzle instead of two round cheek balls.
        drawPart(parent, 0f, .91f, .22f, .105f, .23f, .11f, GOAT_IVORY, rotateX = -15f)
        goatMesh(parent, GoatGeometry.muzzle, GOAT_MUZZLE, y = .755f, z = .355f)
        for (side in -1..1 step 2) {
            drawEye(parent, side.toFloat())
            drawEar(parent, side.toFloat(), pose)
            surface = CharacterSurface.HORN
            goatMesh(parent, GoatGeometry.horn, GOAT_HORN,
                side * .18f, 1.245f, -.10f, sx = side.toFloat(), rx = -9f, rz = -side * 10f)
        }
        surface = CharacterSurface.NOSE
        drawPart(parent, 0f, .735f, .505f, .13f, .055f, .055f, GOAT_NOSE_TAUPE)
        for (side in -1..1 step 2) {
            drawPart(parent, side * .071f, .735f, .552f, .028f * flare, .013f * flare, .014f,
                NOSE_DARK, rotateZ = -side * 18f)
        }
        surface = CharacterSurface.MATTE
        drawPart(parent, 0f, .691f, .533f, .006f, .025f, .008f, GOAT_LIP)
        drawJaw(parent, pose)
        surface = CharacterSurface.COAT
        goatMesh(parent, GoatGeometry.tuft, GOAT_CREAM, -.075f, 1.275f, .13f, sy = .7f, rz = -17f)
        goatMesh(parent, GoatGeometry.tuft, GOAT_CREAM, .035f, 1.29f, .14f, sy = .8f, rz = 10f)
    }

    private fun CharacterDrawContext.drawEye(parent: FloatArray, side: Float) {
        val x = side * .248f
        val open = life?.blink ?: 1f
        val gx = (life?.gazeX ?: 0f) * .38f
        val gy = (life?.gazeY ?: 0f) * .35f
        surface = CharacterSurface.MATTE
        drawPart(parent, x, 1.035f, .209f, .092f, .067f, .045f, GOAT_EYE_RIM, rotateY = side * 13f)
        surface = CharacterSurface.EYE
        drawPart(parent, x, 1.035f, .239f, .079f, .056f * open, .034f, GOAT_EYE_GOLD, rotateY = side * 13f)
        drawPart(parent, x + gx, 1.035f + gy * open, .271f, .051f, .011f * open, .009f, EYE_DARK)
        if (open > .3f) {
            drawPart(parent, x + gx - .018f, 1.049f + gy, .281f, .011f, .010f * open, .005f, WHITE)
        }
        surface = CharacterSurface.COAT
        drawPart(parent, x, 1.113f, .222f, .099f, .024f, .047f, GOAT_CREAM, rotateZ = -side * 8f)
        drawPart(parent, x, .976f, .235f, .081f, .013f, .025f, GOAT_IVORY)
    }

    private fun CharacterDrawContext.drawEar(parent: FloatArray, side: Float, pose: CharacterPose) {
        val ear = rigTransforms[3]
        System.arraycopy(parent, 0, ear, 0, 16)
        val t = life?.timeSeconds ?: 0f
        val flick = if (life != null) sin(t * 9f + side) * max(0f, sin(t * .71f + side * 2f)).pow(18) * 5f else 0f
        // Each ear slowly 'listens' on its own rhythm, so the two never move in sync.
        val listen = if (life != null) sin(t * .37f + side * 1.9f) * 4f + sin(t * .91f - side) * 1.5f else 0f
        Matrix.translateM(ear, 0, side * .29f, 1.12f, -.06f)
        Matrix.rotateM(ear, 0, -side * (68f + pose.primaryAppendageDegrees * .55f + flick + listen), 0f, 0f, 1f)
        Matrix.rotateM(ear, 0, -10f + pose.primaryAppendageDegrees * .3f, 1f, 0f, 0f)
        surface = CharacterSurface.COAT
        goatMesh(ear, GoatGeometry.ear, GOAT_EAR_TAN, sx = .9f, sy = .84f)
        surface = CharacterSurface.MATTE
        goatMesh(ear, GoatGeometry.ear, GOAT_EAR_INNER, y = .04f, z = .034f, sx = .62f, sy = .70f, sz = .24f)
    }

    private fun CharacterDrawContext.drawJaw(parent: FloatArray, pose: CharacterPose) {
        val jaw = rigTransforms[4]
        System.arraycopy(parent, 0, jaw, 0, 16)
        val t = life?.timeSeconds ?: 0f
        val chew = if (life?.isIdle == true) max(0f, sin(t * .43f)).pow(12) * (.5f + .5f * sin(t * 7f)) * .08f else 0f
        val opening = ((life?.mouthOpen ?: 0f) + pose.jawOpen + chew).coerceIn(0f, 1f)
        Matrix.translateM(jaw, 0, 0f, .80f, .12f)
        Matrix.rotateM(jaw, 0, -opening * 24f, 1f, 0f, 0f)
        // Goats chew cud sideways, not up and down.
        Matrix.rotateM(jaw, 0, chew * sin(t * 7f + 1.2f) * 90f, 0f, 1f, 0f)
        Matrix.translateM(jaw, 0, 0f, -.80f, -.12f)
        surface = CharacterSurface.MATTE
        drawPart(parent, 0f, .653f, .475f, .12f, .012f + opening * .042f, .035f, GOAT_LIP)
        surface = CharacterSurface.COAT
        goatMesh(jaw, GoatGeometry.jaw, GOAT_IVORY, y = .636f, z = .355f)
        if (opening > .15f) {
            surface = CharacterSurface.NOSE
            drawPart(jaw, 0f, .665f, .465f, .052f, .016f, .052f, GOAT_TONGUE)
        }
        val beard = rigTransforms[5]
        System.arraycopy(jaw, 0, beard, 0, 16)
        Matrix.translateM(beard, 0, 0f, .57f, .35f)
        Matrix.rotateM(beard, 0, pose.secondaryAppendageDegrees * .45f, 0f, 0f, 1f)
        Matrix.rotateM(beard, 0, pose.secondaryAppendageDegrees * .23f, 1f, 0f, 0f)
        surface = CharacterSurface.COAT
        goatMesh(beard, GoatGeometry.beard, GOAT_BEARD)
        goatMesh(beard, GoatGeometry.beard, GOAT_IVORY, x = -.045f, y = .025f, z = .015f, sx = .6f, sy = .8f, sz = .7f, rz = -12f)
        goatMesh(beard, GoatGeometry.beard, GOAT_FUR_LIGHT, x = .048f, y = .02f, z = .01f, sx = .55f, sy = .74f, sz = .65f, rz = 13f)
    }
}
