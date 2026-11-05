package com.rekixo.ludopaws.feature.dice.content.spatial

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.rekixo.ludopaws.feature.dice.domain.DicePalette
import com.rekixo.ludopaws.feature.dice.rendering.DiceRenderFrame
import com.rekixo.ludopaws.feature.dice.rendering.DiceRenderer
import kotlin.math.sqrt

internal class EmberForgeDiceRenderer : SpatialDiceRenderer(SpatialDiceMaterial.FORGE)
internal class PrismRoyalDiceRenderer : SpatialDiceRenderer(SpatialDiceMaterial.PRISM)

/** Projects a real bevelled mesh and its face-local engravings onto Android's canvas. */
internal open class SpatialDiceRenderer(private val material: SpatialDiceMaterial) : DiceRenderer {
    override val usesPerimeterCountdown = false
    override val usesSpatialRoll = true

    private val facets = if (material == SpatialDiceMaterial.FORGE) forgeMesh else prismMesh
    private val rotation = FloatArray(9)
    private val projection = FloatArray(8)
    private val faceMatrix = Matrix()
    private val path = Path()
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val details = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = .025f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val faceLight = LinearGradient(-1f, -1f, 1f, 1f,
        intArrayOf(material.light, material.mid, material.dark), null, Shader.TileMode.CLAMP)
    private val pitLight = LinearGradient(-.16f, -.16f, .16f, .16f,
        0xFF161322.toInt(), material.metal, Shader.TileMode.CLAMP)
    private val pipLight = RadialGradient(-.045f, -.065f, .22f,
        Color.WHITE, material.pip, Shader.TileMode.CLAMP)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, 0x630E1018, 0x000E1018, Shader.TileMode.CLAMP)
    }
    private val bounds = RectF()

    override fun draw(canvas: Canvas, size: Float, palette: DicePalette, frame: DiceRenderFrame): RectF {
        val pose = when {
            frame.rolling -> SpatialDiceMotion.rolling(frame.rollStartFace, frame.rollElapsedMillis)
            frame.settling -> SpatialDiceMotion.settling(frame.rollStartFace, frame.face, frame.rollElapsedMillis, frame.settleProgress)
            else -> SpatialDiceMotion.resting(frame.face)
        }
        pose.writeMatrix(rotation)
        val rise = (frame.rollElapsedMillis / 230f).coerceIn(0f, 1f)
        val landing = frame.settleProgress.coerceIn(0f, 1f)
        val lift = when {
            frame.rolling -> .045f * ease(rise)
            frame.settling -> .045f * ease(rise) * (1f - ease(landing))
            else -> 0f
        }

        val shadowLayer = canvas.save()
        canvas.translate(size * .5f, size * .825f)
        canvas.scale(size * (.41f + lift), size * .12f)
        shadow.alpha = (255f - 1200f * lift).toInt()
        canvas.drawCircle(0f, 0f, 1f, shadow)
        canvas.restoreToCount(shadowLayer)

        val die = canvas.save()
        canvas.translate(size * .5f, size * (.475f - lift))
        canvas.scale(size * .255f, size * .255f)
        facets.forEach { drawFacet(canvas, it) }
        canvas.restoreToCount(die)
        bounds.set(size * .07f, size * .07f, size * .93f, size * .93f)
        return bounds
    }

    private fun drawFacet(canvas: Canvas, facet: Facet) {
        val nx = transform(0, facet.normal)
        val ny = transform(3, facet.normal)
        val nz = transform(6, facet.normal)
        if (nz * CAMERA <= facet.planeDistance) return

        path.rewind()
        facet.vertices.forEachIndexed { i, vertex ->
            val depth = CAMERA / (CAMERA - transform(6, vertex))
            val x = transform(0, vertex) * depth
            val y = transform(3, vertex) * depth
            projection[i * 2] = x
            projection[i * 2 + 1] = y
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val light = (-.42f * nx - .55f * ny + .71f * nz).coerceIn(0f, 1f)
        body.color = lit(if (facet.face == 0) material.metal else material.mid, .55f + light * .63f)
        canvas.drawPath(path, body)
        if (facet.face == 0 || !faceMatrix.setPolyToPoly(faceSquare, 0, projection, 0, 4, inner)) return

        val surface = canvas.save()
        canvas.concat(faceMatrix)
        details.color = Color.WHITE
        details.shader = faceLight
        canvas.drawRect(-1f, -1f, 1f, 1f, details)
        details.shader = null
        drawInlay(canvas, facet.face)
        details.color = ((57f * (1f - light)).toInt() shl 24)
        canvas.drawRect(-1f, -1f, 1f, 1f, details)
        canvas.restoreToCount(surface)
    }

    private fun drawInlay(canvas: Canvas, face: Int) {
        rim.color = material.metal
        if (material == SpatialDiceMaterial.FORGE) {
            canvas.drawRoundRect(-.91f, -.91f, .91f, .91f, .12f, .12f, rim)
            canvas.drawLine(-.79f, -.76f, -.56f, -.76f, rim)
            canvas.drawLine(.56f, .76f, .79f, .76f, rim)
        } else {
            path.rewind()
            path.moveTo(-.91f, -.52f)
            path.lineTo(-.91f, -.78f)
            path.lineTo(-.78f, -.91f)
            path.lineTo(-.52f, -.91f)
            path.moveTo(.91f, .52f)
            path.lineTo(.91f, .78f)
            path.lineTo(.78f, .91f)
            path.lineTo(.52f, .91f)
            canvas.drawPath(path, rim)
        }
        val dots = pips[face - 1]
        for (index in dots.indices step 2) {
            val saved = canvas.save()
            canvas.translate(dots[index], dots[index + 1])
            details.color = Color.WHITE
            details.shader = pitLight
            if (material == SpatialDiceMaterial.FORGE) {
                canvas.drawCircle(0f, 0f, .165f, details)
                details.shader = pipLight
                canvas.drawCircle(0f, 0f, .113f, details)
            } else {
                diamond(.18f)
                canvas.drawPath(path, details)
                details.shader = pipLight
                diamond(.132f)
                canvas.drawPath(path, details)
            }
            details.shader = null
            canvas.restoreToCount(saved)
        }
    }

    private fun diamond(radius: Float) {
        path.rewind()
        path.moveTo(0f, -radius)
        path.lineTo(radius, 0f)
        path.lineTo(0f, radius)
        path.lineTo(-radius, 0f)
        path.close()
    }

    private fun ease(progress: Float): Float = progress * progress * (3f - 2f * progress)

    private fun transform(row: Int, p: Point): Float =
        rotation[row] * p.x + rotation[row + 1] * p.y + rotation[row + 2] * p.z

    private fun lit(color: Int, light: Float): Int = Color.rgb(
        (Color.red(color) * light).toInt().coerceIn(0, 255),
        (Color.green(color) * light).toInt().coerceIn(0, 255),
        (Color.blue(color) * light).toInt().coerceIn(0, 255),
    )

    private data class Point(val x: Float, val y: Float, val z: Float) {
        operator fun plus(other: Point) = Point(x + other.x, y + other.y, z + other.z)
        operator fun times(scale: Float) = Point(x * scale, y * scale, z * scale)
        fun dot(other: Point) = x * other.x + y * other.y + z * other.z
        fun unit() = this * (1f / sqrt(dot(this)))
    }

    private data class Facet(val normal: Point, val vertices: List<Point>, val face: Int = 0) {
        val planeDistance = normal.dot(vertices.first())
    }

    private companion object {
        private const val CAMERA = 6.5f
        private val faceSquare = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f)
        private val pips = listOf(
            floatArrayOf(0f, 0f),
            floatArrayOf(-.46f, -.46f, .46f, .46f),
            floatArrayOf(-.46f, -.46f, 0f, 0f, .46f, .46f),
            floatArrayOf(-.46f, -.46f, .46f, -.46f, -.46f, .46f, .46f, .46f),
            floatArrayOf(-.46f, -.46f, .46f, -.46f, 0f, 0f, -.46f, .46f, .46f, .46f),
            floatArrayOf(-.46f, -.54f, .46f, -.54f, -.46f, 0f, .46f, 0f, -.46f, .54f, .46f, .54f),
        )

        private val x = Point(1f, 0f, 0f)
        private val y = Point(0f, 1f, 0f)
        private val z = Point(0f, 0f, 1f)
        private val forgeMesh = mesh(.86f)
        private val prismMesh = mesh(.73f)

        private fun mesh(inner: Float): List<Facet> = buildList {
            val axes = listOf(x, y, z)
            val signs = listOf(-1f, 1f)
            for (i in axes.indices) for (j in i + 1..2) for (a in signs) for (b in signs) {
                val u = axes[i] * a
                val v = axes[j] * b
                val span = axes[3 - i - j] * inner
                val first = u + v * inner
                val second = u * inner + v
                add(Facet((u + v).unit(), listOf(first + span, first + span * -1f,
                    second + span * -1f, second + span)))
            }
            for (a in signs) for (b in signs) for (c in signs) {
                val u = x * a
                val v = y * b
                val w = z * c
                add(Facet((u + v + w).unit(), listOf(u + v * inner + w * inner,
                    u * inner + v + w * inner, u * inner + v * inner + w)))
            }
            add(face(z, x, y, 1, inner))
            add(face(y, x, z * -1f, 2, inner))
            add(face(x, z * -1f, y, 3, inner))
            add(face(x * -1f, z, y, 4, inner))
            add(face(y * -1f, x, z, 5, inner))
            add(face(z * -1f, x * -1f, y, 6, inner))
        }

        private fun face(normal: Point, horizontal: Point, vertical: Point, number: Int, inner: Float): Facet {
            val u = horizontal * inner
            val v = vertical * inner
            return Facet(normal, listOf(
                normal + u * -1f + v * -1f,
                normal + u + v * -1f,
                normal + u + v,
                normal + u * -1f + v,
            ), number)
        }
    }
}
