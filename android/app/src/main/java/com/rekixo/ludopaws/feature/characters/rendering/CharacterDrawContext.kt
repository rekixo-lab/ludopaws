package com.rekixo.ludopaws.feature.characters.rendering

import android.opengl.Matrix
import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import com.rekixo.ludopaws.feature.characters.shared.LudoPaws3DLifeMotion
import com.rekixo.ludopaws.feature.characters.shared.*
import kotlin.math.cos
import kotlin.math.sin

/** One mutable context per GL scene. Stateless character renderers can also serve portraits. */
internal abstract class CharacterDrawContext {
    var life: LudoPaws3DLifeMotion? = null
    val head = FloatArray(16)
    val rigTransforms = Array(8) { FloatArray(16) }
    val rigScratch = FloatArray(16)
    var surface = CharacterSurface.MATTE
    var partyEnergy = 0f
    // Optional continuous limb deformation, zero for rigid parts and other species.
    var limbBendY = 0f
    var limbBendZ = 0f

    abstract fun drawGeometry(
        parent: FloatArray, geometry: MeshGeometry,
        translateX: Float, translateY: Float, translateZ: Float,
        scaleX: Float, scaleY: Float, scaleZ: Float, color: FloatArray,
        rotateX: Float = 0f, rotateY: Float = 0f, rotateZ: Float = 0f,
    )

    abstract fun drawPart(
        parent: FloatArray, translateX: Float, translateY: Float, translateZ: Float,
        scaleX: Float, scaleY: Float, scaleZ: Float, color: FloatArray,
        rotateX: Float = 0f, rotateY: Float = 0f, rotateZ: Float = 0f,
    )

    fun drawTail(parent: FloatArray, wag: Float, coat: FloatArray, tip: FloatArray, length: Float) {
        var x = .49f
        var y = -.23f
        // Each segment starts at the previous end; curl increases toward the tip.
        repeat(5) { segment ->
            val degrees = -48f + wag * (1f + segment * .10f) + segment * 16f
            val angle = Math.toRadians(degrees.toDouble())
            val dx = -sin(angle).toFloat() * length / 5f
            val dy = cos(angle).toFloat() * length / 5f
            val radius = .115f - segment * .015f
            drawPart(parent, x + dx * .5f, y + dy * .5f, -.33f,
                radius, length / 8f, radius, if (segment >= 4) tip else coat, rotateZ = degrees)
            x += dx
            y += dy
        }
    }

    fun prepareHead(parent: FloatArray, centerY: Float, tilt: Float) {
        System.arraycopy(parent, 0, head, 0, 16)
        val pivotY = centerY - .38f
        Matrix.translateM(head, 0, 0f, pivotY, 0f)
        Matrix.rotateM(head, 0, tilt + (life?.headTilt ?: 0f) + (life?.shake ?: 0f), 0f, 0f, 1f)
        Matrix.rotateM(head, 0, life?.headPitch ?: 0f, 1f, 0f, 0f)
        Matrix.rotateM(head, 0, (life?.gazeX ?: 0f) * 180f, 0f, 1f, 0f)
        Matrix.translateM(head, 0, 0f, -pivotY, 0f)
    }

    fun drawLivingEye(parent: FloatArray, x: Float, y: Float, z: Float,
        sx: Float, sy: Float, skin: FloatArray, iris: FloatArray, pupil: FloatArray,
        horizontal: Boolean, slit: Boolean) {
        val openness = life?.blink ?: 1f
        val gx = life?.gazeX ?: 0f
        val gy = (life?.gazeY ?: 0f) * openness
        drawPart(parent, x, y, z - .009f, sx * 1.14f, sy * 1.09f, .075f, skin)
        drawPart(parent, x, y, z, sx, sy * openness, .075f, iris)
        drawPart(parent, x + gx, y + gy, z + .067f,
            if (horizontal) sx * .65f else if (slit) sx * .27f else sx * .64f,
            sy * openness * (if (horizontal) .28f else .77f), .025f, pupil)
        if (openness > .25f) {
            drawPart(parent, x + gx - .025f, y + gy + .04f * openness,
                z + .091f, .021f, .026f * openness, .012f, WHITE)
        }
    }

}
