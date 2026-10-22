package com.rekixo.ludopaws.feature.characters.content.duck

import android.opengl.Matrix
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*

/** Rounded skull, gaze-following eyes and a hinged lower bill for quacks/yawns. */
internal object DuckFaceRenderer {
    fun draw(ctx: CharacterDrawContext, parent: FloatArray, pose: CharacterPose) = with(ctx) {
        surface = CharacterSurface.COAT
        duckMesh(parent, DuckGeometry.head, DUCK_YELLOW)
        for (side in -1..1 step 2) {
            val x = side * .205f
            val open = life?.blink ?: 1f
            val gx = (life?.gazeX ?: 0f) * .45f
            val gy = (life?.gazeY ?: 0f) * .45f
            surface = CharacterSurface.COAT
            drawPart(parent, x, 1.015f, .311f, .110f, .135f, .049f, DUCK_BELLY)
            surface = CharacterSurface.EYE
            drawPart(parent, x, 1.015f, .337f, .095f, .120f * open, .033f, DUCK_EYE_GOLD)
            drawPart(parent, x+gx, 1.015f+gy*open, .366f, .071f, .098f*open, .015f, EYE_DARK)
            if (open > .3f) drawPart(parent, x+gx-.025f, 1.055f+gy, .379f,
                .022f, .027f*open, .008f, WHITE)
        }
        surface = CharacterSurface.NOSE
        duckMesh(parent, DuckGeometry.bill, DUCK_ORANGE, y = .815f, z = .405f)
        for (side in -1..1 step 2) drawPart(parent, side*.10f, .861f, .535f,
            .020f, .009f, .012f, DUCK_BILL_SHADOW)
        val opening = ((life?.mouthOpen ?: 0f) + pose.jawOpen).coerceIn(0f,1f)
        surface = CharacterSurface.MATTE
        drawPart(parent, 0f, .762f, .578f, .26f, .006f + opening*.04f, .025f, DUCK_BILL_SHADOW)
        val jaw = rigTransforms[4]
        System.arraycopy(parent,0,jaw,0,16)
        Matrix.translateM(jaw,0,0f,.775f,.235f)
        Matrix.rotateM(jaw,0,-opening*25f,1f,0f,0f)
        Matrix.translateM(jaw,0,0f,-.775f,-.235f)
        surface = CharacterSurface.NOSE
        duckMesh(jaw, DuckGeometry.bill, DUCK_BILL_LOWER, y = .749f, z = .403f, sx = .94f, sy = .52f, sz = .94f)
        surface = CharacterSurface.COAT
        duckMesh(parent, DuckGeometry.tuft, DUCK_BELLY, x = -.045f, y = 1.27f, z = -.025f, rz = -16f)
        duckMesh(parent, DuckGeometry.tuft, DUCK_YELLOW, x = .045f, y = 1.27f, z = -.045f, sy = .75f, rz = 12f)
    }
}
