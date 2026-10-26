package com.rekixo.ludopaws.feature.boards.content.classic

import android.graphics.RectF
import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPawsBoardPalette
import com.rekixo.ludopaws.feature.characters.rendering.CharacterDrawContext
import com.rekixo.ludopaws.feature.characters.rendering.CharacterSurface
import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal object ClassicRoadProfile {
    const val GAP = .026f
    const val DEPTH = .045f
    const val CORNER = .28f
    const val BEVEL = .028f

    fun boundary(index: Int): Float = when {
        index <= 6 -> index * 50f / 6f
        index <= 9 -> 50f + (index - 6) * 50f / 3f
        else -> 100f + (index - 9) * 50f / 6f
    }

    fun raw(row: Int, column: Int) = RectF(boundary(column), boundary(row),
        boundary(column + 1), boundary(row + 1))

    fun face(raw: RectF): RectF {
        val unit = min(raw.width(), raw.height())
        val gap = unit * GAP
        return RectF(raw.left + gap, raw.top + gap,
            raw.right - gap, raw.bottom - gap - unit * DEPTH)
    }

    fun isRoad(row: Int, column: Int): Boolean =
        column in 6..8 && (row < 6 || row > 8) ||
            row in 6..8 && (column < 6 || column > 8)

    fun team(row: Int, column: Int): Int? = when {
        row == 7 && column in 1..5 || row == 6 && column == 1 -> LudoPawsBoardPalette.coral
        column == 7 && row in 1..5 || row == 1 && column == 8 -> LudoPawsBoardPalette.jade
        row == 7 && column in 9..13 || row == 8 && column == 13 -> LudoPawsBoardPalette.honey
        column == 7 && row in 9..13 || row == 13 && column == 6 -> LudoPawsBoardPalette.sky
        else -> null
    }
}

internal class ClassicRoadScene {
    private val transform = FloatArray(16)

    fun draw(context: CharacterDrawContext, size: Float, turns: Int) {
        val previous = context.surface
        context.life = null
        context.limbBendY = 0f
        context.limbBendZ = 0f
        context.partyEnergy = 0f
        Matrix.setIdentityM(transform, 0)
        Matrix.translateM(transform, 0, size * .5f, size * .5f, 0f)
        Matrix.rotateM(transform, 0, turns * 90f, 0f, 0f, 1f)
        Matrix.scaleM(transform, 0, size / 150f, size / 150f, size / 150f)
        Matrix.translateM(transform, 0, -75f, -75f, 0f)
        artwork.forEach { batch ->
            context.surface = batch.surface
            context.drawGeometry(transform, batch.mesh, 0f, 0f, 0f, 1f, 1f, 1f, batch.color)
        }
        context.surface = previous
    }

    private data class Point(val x: Float, val y: Float, val z: Float)
    private data class Batch(val mesh: MeshGeometry, val color: FloatArray, val surface: CharacterSurface)

    private class Builder {
        val vertices = ArrayList<Float>()
        val indices = ArrayList<Short>()

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

        fun build() = MeshGeometry(vertices.toFloatArray(), indices.toShortArray())
    }

    companion object {
        fun prepare() { artwork }

        private fun rgb(color: Int) = floatArrayOf(((color shr 16) and 255) / 255f,
            ((color shr 8) and 255) / 255f, (color and 255) / 255f, 1f)

        private fun outline(bounds: RectF, radius: Float, height: Float): List<Point> = buildList {
            val corner = min(radius, min(bounds.width(), bounds.height()) * .5f)
            repeat(4) { quadrant ->
                val centerX = if (quadrant == 0 || quadrant == 3) bounds.right - corner else bounds.left + corner
                val centerY = if (quadrant < 2) bounds.bottom - corner else bounds.top + corner
                repeat(5) { step ->
                    val angle = (quadrant * 90f + step * 22.5f) * PI.toFloat() / 180f
                    add(Point(centerX + cos(angle) * corner, centerY + sin(angle) * corner, height))
                }
            }
        }

        private val artwork: List<Batch> by lazy {
            val builders = linkedMapOf<Int, Builder>()
            val safeFaces = Builder()
            val symbols = Builder()
            val darkFoliage = Builder()
            val lightFoliage = Builder()
            val trunks = Builder()
            for (row in 0..14) for (column in 0..14) {
                if (!ClassicRoadProfile.isRoad(row, column)) continue
                val raw = ClassicRoadProfile.raw(row, column)
                val unit = min(raw.width(), raw.height())
                val face = ClassicRoadProfile.face(raw)
                val bevel = unit * ClassicRoadProfile.BEVEL
                val depth = unit * ClassicRoadProfile.DEPTH
                val color = ClassicRoadProfile.team(row, column) ?: ClassicBoardDefinition.content.neutralCellColor
                val builder = builders.getOrPut(color) { Builder() }
                val bottom = RectF(face).apply { offset(0f, depth) }
                val top = RectF(face).apply { inset(bevel, bevel) }
                val lowerRing = outline(bottom, unit * ClassicRoadProfile.CORNER, .025f)
                val bevelRing = outline(face, unit * ClassicRoadProfile.CORNER, depth * .65f)
                val upperRing = outline(top, unit * ClassicRoadProfile.CORNER - bevel, depth)
                upperRing.indices.forEach { index ->
                    val next = (index + 1) % upperRing.size
                    builder.triangle(Point(top.centerX(), top.centerY(), depth), upperRing[index], upperRing[next])
                    builder.quad(upperRing[index], upperRing[next], bevelRing[next], bevelRing[index])
                    builder.quad(bevelRing[index], bevelRing[next], lowerRing[next], lowerRing[index])
                }
                val safe = row == 2 && column == 6 || row == 6 && column == 12 ||
                    row == 12 && column == 8 || row == 8 && column == 2
                val entry = row == 6 && column == 1 || row == 1 && column == 8 ||
                    row == 8 && column == 13 || row == 13 && column == 6
                val centerX = raw.centerX()
                val centerY = raw.centerY()
                val height = depth + .035f
                if (safe) {
                    val shield = listOf(Point(centerX, centerY - 2.6f, height),
                        Point(centerX + 2f, centerY - 1.7f, height), Point(centerX + 1.7f, centerY + .8f, height),
                        Point(centerX, centerY + 2.5f, height), Point(centerX - 1.7f, centerY + .8f, height),
                        Point(centerX - 2f, centerY - 1.7f, height))
                    shield.indices.forEach { index ->
                        safeFaces.triangle(Point(centerX, centerY, height), shield[index], shield[(index + 1) % shield.size])
                    }
                    fun segment(startX: Float, startY: Float, endX: Float, endY: Float) {
                        val deltaX = endX - startX
                        val deltaY = endY - startY
                        val length = sqrt(deltaX * deltaX + deltaY * deltaY)
                        val offsetX = -deltaY / length * .22f
                        val offsetY = deltaX / length * .22f
                        symbols.quad(Point(startX + offsetX, startY + offsetY, height + .03f),
                            Point(endX + offsetX, endY + offsetY, height + .03f),
                            Point(endX - offsetX, endY - offsetY, height + .03f),
                            Point(startX - offsetX, startY - offsetY, height + .03f))
                    }
                    segment(centerX - .9f, centerY, centerX - .2f, centerY + .7f)
                    segment(centerX - .2f, centerY + .7f, centerX + 1.1f, centerY - .8f)
                } else if (entry) {
                    val points = List(10) { index ->
                        val angle = (index * 36f - 90f) * PI.toFloat() / 180f
                        val radius = if (index % 2 == 0) 2.4f else 1.05f
                        Point(centerX + cos(angle) * radius, centerY + sin(angle) * radius, height)
                    }
                    points.indices.forEach { index ->
                        symbols.triangle(Point(centerX, centerY, height), points[index], points[(index + 1) % points.size])
                    }
                }
            }
            for (originX in listOf(0f, 100f)) for (originY in listOf(0f, 100f)) {
                repeat(2) { tree ->
                    val centerX = originX + if (tree == 0) 5.8f else 44.5f
                    val centerY = originY + if (tree == 0) 6f else 44f
                    val span = if (tree == 0) 4.8f else 4.2f
                    repeat(8) { frond ->
                        val angle = frond * PI.toFloat() / 4f + tree * .25f
                        val middleX = centerX + cos(angle) * span * .55f
                        val middleY = centerY + sin(angle) * span * .55f
                        val tip = Point(centerX + cos(angle) * span, centerY + sin(angle) * span, 1.3f)
                        val ridge = Point(middleX, middleY, 3.4f)
                        val root = Point(centerX, centerY, 2.8f)
                        val edgeLeft = Point(middleX - sin(angle) * .85f, middleY + cos(angle) * .85f, 2.1f)
                        val edgeRight = Point(middleX + sin(angle) * .85f, middleY - cos(angle) * .85f, 2.1f)
                        lightFoliage.triangle(root, ridge, edgeLeft)
                        lightFoliage.triangle(ridge, tip, edgeLeft)
                        darkFoliage.triangle(root, edgeRight, ridge)
                        darkFoliage.triangle(ridge, edgeRight, tip)
                    }
                    repeat(6) { side ->
                        val angle = side * PI.toFloat() / 3f
                        val next = (side + 1) * PI.toFloat() / 3f
                        trunks.quad(Point(centerX + cos(angle) * .4f, centerY + 2.5f + sin(angle) * .4f, 0f),
                            Point(centerX + cos(next) * .4f, centerY + 2.5f + sin(next) * .4f, 0f),
                            Point(centerX + cos(next) * .25f, centerY + sin(next) * .25f, 2.8f),
                            Point(centerX + cos(angle) * .25f, centerY + sin(angle) * .25f, 2.8f))
                    }
                }
            }
            builders.map { (color, builder) -> Batch(builder.build(), rgb(color), CharacterSurface.GARDEN_STONE) } +
                listOf(Batch(safeFaces.build(), rgb(LudoPawsBoardPalette.safe), CharacterSurface.MATTE),
                    Batch(symbols.build(), rgb(LudoPawsBoardPalette.stoneLight), CharacterSurface.MATTE),
                    Batch(trunks.build(), rgb(0xFF766B43.toInt()), CharacterSurface.MATTE),
                    Batch(darkFoliage.build(), rgb(0xFF315E35.toInt()), CharacterSurface.GARDEN_LEAF),
                    Batch(lightFoliage.build(), rgb(0xFF588446.toInt()), CharacterSurface.GARDEN_LEAF))
        }
    }
}
