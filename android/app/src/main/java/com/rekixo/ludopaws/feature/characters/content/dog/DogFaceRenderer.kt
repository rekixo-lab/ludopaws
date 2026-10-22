package com.rekixo.ludopaws.feature.characters.content.dog

import android.opengl.Matrix
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.*

/** Hinged floppy ears and jaw stay with the head; the gaze and blink use shared life state. */
internal object DogFaceRenderer {
    fun draw(ctx: CharacterDrawContext, parent: FloatArray, pose: CharacterPose) = with(ctx) {
        surface = CharacterSurface.COAT
        dogMesh(parent, DogGeometry.head, DOG_TAN)
        dogMesh(parent, DogGeometry.muzzle, DOG_CREAM)
        for (side in listOf(-1f, 1f)) {
            eye(parent, side)
            val ear = rigTransforms[3]
            System.arraycopy(parent, 0, ear, 0, 16)
            val t = life?.timeSeconds ?: 0f
            val listen = if (life != null) sin(t * .8f + side * 1.7f) * 2.5f else 0f
            Matrix.translateM(ear, 0, side * .365f, 1.12f, -.035f)
            Matrix.rotateM(ear, 0, side * (12f + pose.primaryAppendageDegrees * .7f + listen), 0f, 0f, 1f)
            Matrix.rotateM(ear, 0, -8f + pose.primaryAppendageDegrees * .28f, 1f, 0f, 0f)
            surface = CharacterSurface.COAT
            dogMesh(ear, DogGeometry.ear, DOG_EAR_BROWN, sx = side)
        }
        surface = CharacterSurface.NOSE
        drawPart(parent, 0f, .83f, .595f, .125f, .080f, .065f, NOSE_DARK)
        drawPart(parent, -.031f, .859f, .651f, .027f, .013f, .008f, NOSE_HIGHLIGHT)
        surface = CharacterSurface.MATTE
        drawPart(parent, 0f, .737f, .588f, .006f, .031f, .009f, DOG_LIP)
        val t = life?.timeSeconds ?: 0f
        val pant = if (life?.isIdle == true) max(0f, sin(t * .63f)).pow(6) * (.12f + .05f * sin(t * 5f)) else 0f
        val open = ((life?.mouthOpen ?: 0f) + pose.jawOpen + pant).coerceIn(0f, 1f)
        val jaw = rigTransforms[4]
        System.arraycopy(parent, 0, jaw, 0, 16)
        Matrix.translateM(jaw, 0, 0f, .74f, .20f)
        Matrix.rotateM(jaw, 0, -open * 27f, 1f, 0f, 0f)
        Matrix.translateM(jaw, 0, 0f, -.74f, -.20f)
        drawPart(parent, 0f, .685f, .539f, .205f, .010f + open * .06f, .036f, DOG_LIP)
        surface = CharacterSurface.COAT
        dogMesh(jaw, DogGeometry.jaw, DOG_CREAM)
        if (open > .09f) {
            surface = CharacterSurface.NOSE
            drawPart(jaw, 0f, .675f, .535f, .080f, .016f, .073f, TONGUE_PINK)
        }
    }

    private fun CharacterDrawContext.eye(parent: FloatArray, side: Float) {
        val x = side * .215f
        val open = life?.blink ?: 1f
        val gx = (life?.gazeX ?: 0f) * .5f
        val gy = (life?.gazeY ?: 0f) * .5f
        surface = CharacterSurface.COAT
        drawPart(parent, x, 1.015f, .285f, .115f, .132f, .052f, DOG_EAR_BROWN)
        surface = CharacterSurface.EYE
        drawPart(parent, x, 1.015f, .313f, .101f, .118f * open, .038f, DOG_IRIS)
        drawPart(parent, x + gx, 1.015f + gy * open, .346f, .067f, .087f * open, .015f, EYE_DARK)
        if (open > .3f) {
            drawPart(parent, x + gx - .026f, 1.055f + gy, .360f, .021f, .025f * open, .008f, WHITE)
        }
        surface = CharacterSurface.COAT
        drawPart(parent, x, 1.17f, .249f, .100f, .025f, .036f, DOG_CREAM, rotateZ = side * 8f)
    }
}
