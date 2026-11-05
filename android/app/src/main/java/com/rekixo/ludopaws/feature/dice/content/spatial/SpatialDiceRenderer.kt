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
import kotlin.math.pow
import kotlin.math.sqrt

internal class EmberForgeDiceRenderer : SpatialDiceRenderer(SpatialDiceMaterial.FORGE)
internal class PrismRoyalDiceRenderer : SpatialDiceRenderer(SpatialDiceMaterial.PRISM)

/** Projects a real bevelled mesh and its face-local engravings onto Android's canvas. */
internal open class SpatialDiceRenderer(private val material: SpatialDiceMaterial) : DiceRenderer {
    override val usesPerimeterCountdown = false
    override val usesSpatialRoll = true

    private val forged = material == SpatialDiceMaterial.FORGE
    private val facets = if (forged) forgeMesh else prismMesh
    private val surface = SpatialDiceSurfaces.forMaterial(material)
    private val rotation = FloatArray(9)
    private val projection = FloatArray(8)
    private val faceMatrix = Matrix()
    private val path = Path()
    private val faceBounds = RectF(-1f, -1f, 1f, 1f)
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val details = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = .025f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val reflection = LinearGradient(-1f, -.46f, 1f, .46f,
        if (forged) intArrayOf(0x00FFD7A2, 0x4AFFB86F, 0x9AFFDBB0.toInt(), 0x23FFC388, 0x00FFD7A2)
        else intArrayOf(0x00D6FFFF, 0x52D1A9FF, 0xE0F4FFFF.toInt(), 0x62A0EDFF, 0x00D6FFFF),
        if (forged) floatArrayOf(0f, .31f, .48f, .70f, 1f) else floatArrayOf(0f, .43f, .50f, .56f, 1f),
        Shader.TileMode.CLAMP)
    private val pitLight = LinearGradient(-.16f, -.16f, .16f, .16f,
        0xFF161322.toInt(), material.metal, Shader.TileMode.CLAMP)
    private val pipLight = RadialGradient(-.045f, -.065f, .22f,
        if (forged) 0xFFFFE3A2.toInt() else Color.WHITE, material.pip, Shader.TileMode.CLAMP)
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
        facet.vertices.forEachIndexed { index, vertex ->
            val depth = CAMERA / (CAMERA - transform(6, vertex))
            val x = transform(0, vertex) * depth
            val y = transform(3, vertex) * depth
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        val light = (-.42f * nx - .55f * ny + .71f * nz).coerceIn(0f, 1f)
        val reflectionStrength = (-.22f * nx - .30f * ny + .928f * nz)
            .coerceIn(0f, 1f).pow(if (forged) 7 else 19)
        val bevelColor = lit(material.metal, .42f + light * .78f)
        body.color = if (facet.face == 0) {
            mix(bevelColor, if (forged) 0xFFFFE0A7.toInt() else 0xFFE8FDFF.toInt(), reflectionStrength * .72f)
        } else material.mid
        canvas.drawPath(path, body)

        val corners = facet.faceCorners ?: return
        corners.forEachIndexed { index, vertex ->
            val depth = CAMERA / (CAMERA - transform(6, vertex))
            projection[index * 2] = transform(0, vertex) * depth
            projection[index * 2 + 1] = transform(3, vertex) * depth
        }
        if (!faceMatrix.setPolyToPoly(faceSquare, 0, projection, 0, 4)) return

        val layer = canvas.save()
        canvas.clipPath(path)
        canvas.concat(faceMatrix)
        details.color = Color.WHITE
        canvas.drawBitmap(surface, null, faceBounds, details)
        details.color = (76f * (1f - light)).toInt() shl 24
        canvas.drawRect(faceBounds, details)

        // The strip reflects the fixed key light as the surface turns.
        val shiftX = nx * .68f
        val shiftY = ny * .47f
        val highlight = canvas.save()
        canvas.translate(shiftX, shiftY)
        details.color = Color.WHITE
        val peakReflection = if (forged) 202f else 255f
        details.alpha = (reflectionStrength * peakReflection).toInt()
        details.shader = reflection
        canvas.drawRect(-1f - shiftX, -1f - shiftY, 1f - shiftX, 1f - shiftY, details)
        details.shader = null
        canvas.restoreToCount(highlight)
        drawInlay(canvas, facet.face)
        canvas.restoreToCount(layer)
    }

    private fun drawInlay(canvas: Canvas, face: Int) {
        rim.color = material.metal
        if (forged) {
            canvas.drawRoundRect(-.91f, -.91f, .91f, .91f, .19f, .19f, rim)
            for (x in rivetPositions) for (y in rivetPositions) {
                details.color = 0xFF211620.toInt()
                canvas.drawCircle(x, y, .073f, details)
                details.color = material.metal
                canvas.drawCircle(x - .009f, y - .015f, .049f, details)
                details.color = 0xFFFFD59C.toInt()
                canvas.drawCircle(x - .023f, y - .03f, .014f, details)
            }
        } else {
            path.rewind()
            path.moveTo(-.90f, -.28f)
            path.lineTo(-.90f, -.50f)
            path.lineTo(-.50f, -.90f)
            path.lineTo(-.28f, -.90f)
            path.moveTo(.90f, .28f)
            path.lineTo(.90f, .50f)
            path.lineTo(.50f, .90f)
            path.lineTo(.28f, .90f)
            canvas.drawPath(path, rim)
        }
        val dots = pips[face - 1]
        for (index in dots.indices step 2) {
            val saved = canvas.save()
            canvas.translate(dots[index], dots[index + 1])
            details.color = Color.WHITE
            details.shader = pitLight
            if (forged) {
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

    private fun mix(from: Int, to: Int, amount: Float): Int = Color.rgb(
        (Color.red(from) + (Color.red(to) - Color.red(from)) * amount).toInt(),
        (Color.green(from) + (Color.green(to) - Color.green(from)) * amount).toInt(),
        (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount).toInt(),
    )

    private data class Point(val x: Float, val y: Float, val z: Float) {
        operator fun plus(other: Point) = Point(x + other.x, y + other.y, z + other.z)
        operator fun times(scale: Float) = Point(x * scale, y * scale, z * scale)
        fun dot(other: Point) = x * other.x + y * other.y + z * other.z
        fun unit() = this * (1f / sqrt(dot(this)))
    }

    private data class Facet(
        val normal: Point,
        val vertices: List<Point>,
        val face: Int = 0,
        val faceCorners: List<Point>? = null,
    ) {
        val planeDistance = normal.dot(vertices.first())
    }

    private companion object {
        private const val CAMERA = 6.5f
        private val faceSquare = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f)
        private val rivetPositions = floatArrayOf(-.75f, .75f)
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
        private val forgeMesh = mesh(edge = .86f, corner = .77f)
        private val prismMesh = mesh(edge = .81f, corner = .47f)

        private fun mesh(edge: Float, corner: Float): List<Facet> = buildList {
            val axes = listOf(x, y, z)
            val signs = listOf(-1f, 1f)
            for (i in axes.indices) for (j in i + 1..2) for (a in signs) for (b in signs) {
                val u = axes[i] * a
                val v = axes[j] * b
                val span = axes[3 - i - j] * corner
                val first = u + v * edge
                val second = u * edge + v
                add(Facet((u + v).unit(), listOf(first + span, first + span * -1f,
                    second + span * -1f, second + span)))
            }
            for (a in signs) for (b in signs) for (c in signs) {
                val u = x * a
                val v = y * b
                val w = z * c
                add(Facet((u + v + w).unit(), listOf(
                    u + v * edge + w * corner,
                    u * edge + v + w * corner,
                    u * corner + v + w * edge,
                    u * corner + v * edge + w,
                    u * edge + v * corner + w,
                    u + v * corner + w * edge,
                )))
            }
            add(face(z, x, y, 1, edge, corner))
            add(face(y, x, z * -1f, 2, edge, corner))
            add(face(x, z * -1f, y, 3, edge, corner))
            add(face(x * -1f, z, y, 4, edge, corner))
            add(face(y * -1f, x, z, 5, edge, corner))
            add(face(z * -1f, x * -1f, y, 6, edge, corner))
        }

        private fun face(normal: Point, horizontal: Point, vertical: Point, number: Int, edge: Float, corner: Float): Facet {
            fun point(x: Float, y: Float) = normal + horizontal * x + vertical * y
            return Facet(normal, listOf(
                point(-corner, -edge), point(corner, -edge),
                point(edge, -corner), point(edge, corner),
                point(corner, edge), point(-corner, edge),
                point(-edge, corner), point(-edge, -corner),
            ), number, listOf(
                point(-edge, -edge), point(edge, -edge), point(edge, edge), point(-edge, edge),
            ))
        }
    }
}
