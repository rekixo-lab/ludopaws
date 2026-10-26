package com.rekixo.ludopaws.feature.boards.content.monsoon

import android.opengl.Matrix
import com.rekixo.ludopaws.feature.characters.rendering.CharacterDrawContext
import com.rekixo.ludopaws.feature.characters.rendering.CharacterSurface
import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal class MonsoonBoardScene {
    private val transform = FloatArray(16)
    private val puddleX = floatArrayOf(10f, 39f, 110f, 139f, 110f, 139f, 10f, 39f)
    private val puddleY = floatArrayOf(37f, 11f, 37f, 11f, 137f, 111f, 137f, 111f)
    private val mudColor = floatArrayOf(.39f, .28f, .19f, 1f)
    private val waterColor = floatArrayOf(.32f, .49f, .51f, 1f)
    private val rainColor = floatArrayOf(.72f, .84f, .81f, 1f)
    private val impactColor = floatArrayOf(.60f, .74f, .71f, 1f)
    private val mudSplashColor = floatArrayOf(.62f, .45f, .29f, 1f)
    private val rimColor = floatArrayOf(.39f, .43f, .36f, 1f)
    private val banks = Builder().apply {
        puddleX.indices.forEach { index ->
            val wide = index % 2 == 0
            ovalRing(puddleX[index], puddleY[index], if (wide) 6.4f else 5.4f,
                if (wide) 4.1f else 3.5f, .84f, .18f, 1.05f)
        }
    }.build()
    private val pools = Builder().apply {
        puddleX.indices.forEach { index ->
            val wide = index % 2 == 0
            disc(puddleX[index], puddleY[index], if (wide) 5.4f else 4.5f,
                if (wide) 3.4f else 2.9f, .38f)
        }
    }.build()
    private val roadRims = Builder().apply {
        for (row in 0..14) for (column in 0..14) {
            if (!(column in 6..8 && (row < 6 || row > 8)) &&
                !(row in 6..8 && (column < 6 || column > 8))) continue
            val left = axis(column) + .8f
            val top = axis(row) + .8f
            val right = axis(column + 1) - .8f
            val bottom = axis(row + 1) - .8f
            val outer = arrayOf(Point(left, top, .2f), Point(right, top, .2f),
                Point(right, bottom, .2f), Point(left, bottom, .2f))
            val inner = arrayOf(Point(left + .24f, top + .24f, .64f), Point(right - .24f, top + .24f, .64f),
                Point(right - .24f, bottom - .24f, .64f), Point(left + .24f, bottom - .24f, .64f))
            repeat(4) { side -> quad(outer[side], outer[(side + 1) % 4], inner[(side + 1) % 4], inner[side]) }
        }
    }.build()
    private val ripple = Builder().apply { ovalRing(0f, 0f, 1f, 1f, .85f, 0f, .08f) }.build()
    private val crown = Builder().apply {
        repeat(12) { side ->
            val angle = side * 2f * PI.toFloat() / 12f
            val next = (side + 1) * 2f * PI.toFloat() / 12f
            val middle = (angle + next) * .5f
            triangle(Point(cos(angle) * .55f, sin(angle) * .55f, 0f),
                Point(cos(next) * .55f, sin(next) * .55f, 0f),
                Point(cos(middle) * .82f, sin(middle) * .82f, if (side % 2 == 0) 1.1f else .65f))
        }
    }.build()
    private val drop = Builder().apply {
        val tip = Point(0f, 1f, 0f)
        val tail = Point(0f, -1f, .2f)
        triangle(tip, Point(-.16f, .3f, .1f), tail)
        triangle(tip, tail, Point(.16f, .3f, .1f))
    }.build()

    fun draw(context: CharacterDrawContext, size: Float, turns: Int, nowMillis: Long, animate: Boolean) {
        val previousSurface = context.surface
        context.life = null
        context.limbBendY = 0f
        context.limbBendZ = 0f
        Matrix.setIdentityM(transform, 0)
        Matrix.translateM(transform, 0, size * .5f, size * .5f, 0f)
        Matrix.rotateM(transform, 0, turns * 90f, 0f, 0f, 1f)
        Matrix.scaleM(transform, 0, size / 150f, size / 150f, size / 150f)
        Matrix.translateM(transform, 0, -75f, -75f, 0f)
        context.surface = CharacterSurface.MATTE
        context.drawGeometry(transform, banks, 0f, 0f, 0f, 1f, 1f, 1f, mudColor)
        context.drawGeometry(transform, roadRims, 0f, 0f, 0f, 1f, 1f, 1f, rimColor)
        context.surface = CharacterSurface.EYE
        context.drawGeometry(transform, pools, 0f, 0f, 0f, 1f, 1f, 1f, waterColor)
        if (animate) {
            repeat(16) { index ->
                val poolIndex = index % puddleX.size
                val phase = ((nowMillis % 1600L + index * 137L) % 1600L) / 1600f
                val mudImpact = index >= 8
                val targetX = puddleX[poolIndex] + if (!mudImpact) -.9f else if (poolIndex % 2 == 0) 5.4f else 4.6f
                val targetY = puddleY[poolIndex] + if (!mudImpact) -.4f else .2f
                val impactHeight = if (mudImpact) 1.14f else .52f
                val splashColor = if (mudImpact) mudSplashColor else impactColor
                if (phase < .62f) {
                    val fall = 1f - phase / .62f
                    context.drawGeometry(transform, drop, targetX + fall * 3f, targetY - fall * 9f,
                        impactHeight + .1f + fall * 12f, 1f, 1.2f, 1f, rainColor, rotateZ = 18f)
                } else {
                    val impact = (phase - .62f) / .38f
                    if (impact < .92f) {
                        val radius = .35f + impact * 2.2f
                        val thickness = (1f - impact).coerceAtLeast(.08f)
                        splashColor[3] = 1f - impact
                        context.drawGeometry(transform, ripple, targetX, targetY, impactHeight,
                            radius, radius * .70f, thickness, splashColor)
                        if (impact < .48f) {
                            val lift = sin(impact / .48f * PI.toFloat()).coerceAtLeast(.02f)
                            context.drawGeometry(transform, crown, targetX, targetY, impactHeight + .02f,
                                .6f + impact, .6f + impact, lift, splashColor, rotateX = -28f)
                        }
                    }
                }
            }
        }
        context.surface = previousSurface
    }

    private data class Point(val x: Float, val y: Float, val z: Float)

    private class Builder {
        private val vertices = ArrayList<Float>()
        private val indices = ArrayList<Short>()

        fun triangle(first: Point, second: Point, third: Point) {
            val edgeX = second.x - first.x
            val edgeY = second.y - first.y
            val edgeZ = second.z - first.z
            val otherX = third.x - first.x
            val otherY = third.y - first.y
            val otherZ = third.z - first.z
            var normalX = edgeY * otherZ - edgeZ * otherY
            var normalY = edgeZ * otherX - edgeX * otherZ
            var normalZ = edgeX * otherY - edgeY * otherX
            val length = sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ)
            if (length < .00001f) return
            val direction = if (normalZ < 0f) -1f else 1f
            normalX *= direction / length
            normalY *= direction / length
            normalZ *= direction / length
            for (point in arrayOf(first, second, third)) {
                indices.add((vertices.size / 6).toShort())
                vertices.add(point.x); vertices.add(point.y); vertices.add(point.z)
                vertices.add(normalX); vertices.add(normalY); vertices.add(normalZ)
            }
        }

        fun quad(first: Point, second: Point, third: Point, fourth: Point) {
            triangle(first, second, third)
            triangle(first, third, fourth)
        }

        fun ovalRing(centerX: Float, centerY: Float, radiusX: Float, radiusY: Float,
            inner: Float, low: Float, high: Float) {
            repeat(24) { side ->
                val angle = side * 2f * PI.toFloat() / 24f
                val next = (side + 1) * 2f * PI.toFloat() / 24f
                fun point(at: Float, radius: Float, height: Float) = Point(
                    centerX + cos(at) * radiusX * radius,
                    centerY + sin(at) * radiusY * radius, height)
                quad(point(angle, 1f, low), point(next, 1f, low),
                    point(next, inner, high), point(angle, inner, high))
                quad(point(angle, inner, high), point(next, inner, high),
                    point(next, inner * .92f, low), point(angle, inner * .92f, low))
            }
        }

        fun disc(centerX: Float, centerY: Float, radiusX: Float, radiusY: Float, height: Float) {
            repeat(32) { side ->
                val angle = side * 2f * PI.toFloat() / 32f
                val next = (side + 1) * 2f * PI.toFloat() / 32f
                triangle(Point(centerX, centerY, height),
                    Point(centerX + cos(angle) * radiusX, centerY + sin(angle) * radiusY, height),
                    Point(centerX + cos(next) * radiusX, centerY + sin(next) * radiusY, height))
            }
        }

        fun build() = MeshGeometry(vertices.toFloatArray(), indices.toShortArray())
    }

    private fun axis(index: Int): Float = when {
        index <= 6 -> index * 50f / 6f
        index <= 9 -> 50f + (index - 6) * 50f / 3f
        else -> 100f + (index - 9) * 50f / 6f
    }
}
