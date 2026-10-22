package com.rekixo.ludopaws.feature.characters.content.dog

import android.opengl.Matrix
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.*

/** Cute puppy face: expressive reflective eyes, rounded muzzle, smile and hinged floppy ears. */
internal object DogFaceRenderer {
    fun draw(ctx: CharacterDrawContext, parent: FloatArray, pose: CharacterPose) = with(ctx) {
        surface = CharacterSurface.COAT
        dogMesh(parent, DogGeometry.head, DOG_TAN)
        dogMesh(parent, DogGeometry.muzzle, DOG_CREAM)
        // A centered cream blaze visually ties the glossy, oversized eyes together.
        drawPart(parent, 0f, 1.154f, .292f, .086f, .138f, .028f, DOG_CREAM)
        drawPart(parent, 0f, 1.012f, .352f, .057f, .111f, .029f, DOG_CREAM)
        for (side in listOf(-1f, 1f)) {
            surface = CharacterSurface.COAT
            drawPart(parent, side * .355f, .785f, .365f, .086f, .046f, .024f, DOG_BLUSH)
            eye(parent, side)
            val ear = rigTransforms[3]
            System.arraycopy(parent, 0, ear, 0, 16)
            val t = life?.timeSeconds ?: 0f
            val listen = if (life != null) sin(t * .8f + side * 1.7f) * 2.5f else 0f
            Matrix.translateM(ear, 0, side * .410f, 1.12f, -.035f)
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
        drawPart(parent, 0f, .685f, .539f, .205f, .016f + open * .06f, .036f, DOG_LIP)
        // Permanent upward corners keep the puppy friendly between mouth animations.
        for (side in listOf(-1f, 1f)) {
            drawPart(parent, side * .174f, .714f, .532f, .033f, .020f, .014f, DOG_LIP)
        }
        surface = CharacterSurface.COAT
        dogMesh(jaw, DogGeometry.jaw, DOG_CREAM)
        if (open > .09f) {
            surface = CharacterSurface.NOSE
            drawPart(jaw, 0f, .675f, .535f, .080f, .016f, .073f, TONGUE_PINK)
        }
    }

    private fun CharacterDrawContext.eye(parent: FloatArray, side: Float) {
        val x = side * .229f
        val openness = life?.blink ?: 1f
        val gx = (life?.gazeX ?: 0f) * .027f
        val gy = (life?.gazeY ?: 0f) * .023f

        // Large cream whites, warm brown irises and two tiny catchlights:
        // readable as a friendly puppy even at pawn-sized render scales.
        surface = CharacterSurface.COAT
        drawPart(parent, x, 1.018f, .334f, .146f, .168f, .055f, DOG_EAR_BROWN)
        surface = CharacterSurface.EYE
        drawPart(parent, x, 1.018f, .387f, .131f, .151f * openness, .043f, WHITE)
        drawPart(parent, x + gx, 1.014f + gy, .419f, .095f, .117f * openness, .023f, DOG_IRIS)
        drawPart(parent, x + gx, 1.014f + gy, .442f, .067f, .088f * openness, .017f, EYE_DARK)
        if (openness > .25f) {
            drawPart(parent, x + gx - .029f, 1.064f + gy, .462f,
                .028f, .034f * openness, .008f, WHITE)
            drawPart(parent, x + gx + .036f, .970f + gy, .457f,
                .012f, .014f * openness, .006f, WHITE)
        }
        surface = CharacterSurface.COAT
        drawPart(parent, x, 1.190f, .264f, .110f, .030f, .032f, DOG_CREAM,
            rotateZ = side * 9f)
    }
}
