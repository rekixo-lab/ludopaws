package com.rekixo.ludopaws

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Four small storybook houses inside the existing four CENTER finish triangles.
 *
 * The outer edge of each wedge has an ivory house facade with a door, windows
 * and sparse flowers. A tiled, team-colored roof grows to the EXACT common
 * center apex. The neighboring roof edges coincide: no new gaps, false center,
 * oversized house rectangles or overlapping roads.
 *
 * Visual layer only: rendered between classic board chrome and finish-rank
 * labels, below genuine pawns/finish FX. No changes to gameplay geometry.
 *
 * All vector detail is rasterized once to a 640px immutable-by-convention
 * bitmap; no paths, shaders, shingles or decorations allocate per frame.
 */
internal class LudoPawsCenterHouseRenderer {
    private data class RoofPalette(
        val base: Int,
        val bright: Int,
        val shade: Int,
        val deep: Int,
        val mortar: Int,
        val wall: Int,
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val displayPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var cachedArtwork: Bitmap? = null

    fun draw(canvas: Canvas, cell: Float) {
        val bounds = LudoPawsCenterHouseGeometry.bounds(cell) ?: return
        val art = cachedArtwork ?: buildArtwork().also { cachedArtwork = it }
        canvas.drawBitmap(
            art, null,
            RectF(bounds.left, bounds.top, bounds.right, bounds.bottom),
            displayPaint,
        )
    }

    private fun buildArtwork(): Bitmap {
        val result = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.scale(6.4f, 6.4f)
        // The original triangles use this same clockwise color order.
        for (team in listOf("GREEN", "YELLOW", "BLUE", "RED")) {
            val rotation = requireNotNull(LudoPawsCenterHouseGeometry.rotationDegrees(team))
            val palette = requireNotNull(palette(team))
            val restore = canvas.save()
            canvas.rotate(rotation, 50f, 50f)
            drawTopFacingHouse(canvas, palette)
            canvas.restoreToCount(restore)
        }

        // Thin diagonal join reads as four separate roof edges without making
        // a dark cross or hiding the single shared central apex.
        for ((x, y) in listOf(0f to 0f, 100f to 0f, 100f to 100f, 0f to 100f)) {
            line(canvas, x, y, 50f, 50f, 0x50302118, .52f)
        }
        return result
    }

    private fun drawTopFacingHouse(c: Canvas, roof: RoofPalette) {
        val wedge = Path().apply {
            moveTo(0f, 0f)
            lineTo(100f, 0f)
            lineTo(50f, 50f)
            close()
        }
        val saved = c.save()
        c.clipPath(wedge)

        // Full triangular color underpaint meets adjacent wedges seamlessly.
        fill(c, wedge, roof.deep)
        gradient(c, wedge, 0f, 0f, 100f, 50f, roof.bright, roof.shade)

        // The facade stays along the OUTER edge of the center square. Its
        // inner edge is covered by roof shingles, never drawn onto a road.
        val facade = Path().apply {
            moveTo(0f, 0f)
            lineTo(100f, 0f)
            lineTo(86f, 14f)
            lineTo(14f, 14f)
            close()
        }
        gradient(c, facade, 50f, 0f, 50f, 17f, 0xFFFFF1D8.toInt(), roof.wall)
        rect(c, 0f, 0f, 100f, 1.9f, 0x7DFFF9DE)
        // Little porch stones outside the central door.
        round(c, 39f, 12.0f, 61f, 15.1f, .8f, 0xFFCAB497.toInt())
        round(c, 41f, 10.6f, 59f, 12.8f, .65f, 0xFFFFE9BF.toInt())
        window(c, 29f, 7.1f)
        window(c, 71f, 7.1f)
        door(c, 50f, 5.0f)
        blossomCluster(c, 20f, 12.3f)
        blossomCluster(c, 80f, 12.3f)

        // Entire triangular roof lies between the TWO exact wedge diagonals.
        // Vertex (50,50) is shared by all four houses, with zero offset.
        val roofShape = Path().apply {
            moveTo(14f, 14f)
            lineTo(86f, 14f)
            lineTo(50f, 50f)
            close()
        }
        gradient(c, roofShape, 19f, 17f, 77f, 41f, roof.bright, roof.base)

        // Gentle left/right plane highlights suggest a small pitched roof,
        // not a flat printed team-color triangle.
        val leftPlane = Path().apply {
            moveTo(14f, 14f)
            lineTo(50f, 14f)
            lineTo(50f, 50f)
            close()
        }
        gradient(c, leftPlane, 16f, 14f, 50f, 49f, 0x3DFFFFFF, 0x04FFFFFF)
        val rightPlane = Path().apply {
            moveTo(50f, 14f)
            lineTo(86f, 14f)
            lineTo(50f, 50f)
            close()
        }
        gradient(c, rightPlane, 51f, 14f, 77f, 47f, 0x00000000, 0x38000000)

        // Roof-tile grout and the shifted vertical joints are CLIPPED to the
        // triangle; rows tighten naturally near the common roof apex.
        val roofSave = c.save()
        c.clipPath(roofShape)
        for (row in 0..8) {
            val y = 17.4f + row * 3.73f
            val width = 100f - y * 2f
            if (width <= 0f) continue
            val left = y
            val right = 100f - y
            line(c, left, y + .65f, right, y + .65f, roof.shade, .96f)
            line(c, left, y - .27f, right, y - .27f, 0x83FFFFFF.toInt(), .54f)
            val step = 7.2f
            var joint = left + if (row % 2 == 0) 3.2f else 6.5f
            while (joint < right) {
                line(c, joint, y - 3.25f, joint + .65f, y + .48f,
                    roof.mortar, .57f)
                joint += step
            }
        }
        c.restoreToCount(roofSave)

        // Neat tile-roof eaves and two sloping gable edges.
        line(c, 14f, 14.1f, 86f, 14.1f, roof.deep, 2.20f)
        line(c, 15f, 13.55f, 85f, 13.55f, roof.bright, 1.10f)
        line(c, 14f, 14f, 50f, 50f, roof.deep, 1.13f)
        line(c, 86f, 14f, 50f, 50f, roof.deep, 1.13f)
        line(c, 14.8f, 14.8f, 49.5f, 49.5f, 0x79FFFFFF, .53f)
        line(c, 50f, 15.2f, 50f, 49f, 0x55FFF5D9, .50f)

        // Clip restores exactly the parent CENTER bounds.
        c.restoreToCount(saved)
    }

    private fun door(c: Canvas, x: Float, y: Float) {
        round(c, x - 6.55f, y - 1.4f, x + 6.55f, 15.1f, 6.1f,
            0xFF9A491F.toInt())
        round(c, x - 5.60f, y - .20f, x + 5.60f, 14.5f, 5.2f,
            0xFFF3A138.toInt())
        round(c, x - 4.70f, y + .75f, x + 4.70f, 14.6f, 4.5f,
            0xFF9A4C24.toInt())
        line(c, x - 1.85f, y + 1.8f, x - 1.85f, 14.1f,
            0x42602F19, .73f)
        line(c, x + 1.85f, y + 1.8f, x + 1.85f, 14.1f,
            0x3DFFE0A6, .72f)
        dot(c, x + 2.8f, y + 7.3f, 1.10f, 0xFFFFDC53.toInt())
        dot(c, x + 2.5f, y + 7.0f, .44f, Color.WHITE)
    }

    private fun window(c: Canvas, x: Float, y: Float) {
        round(c, x - 6.2f, y - 4.7f, x + 6.2f, y + 6.0f, 4.0f,
            0xFFAF611F.toInt())
        round(c, x - 5.0f, y - 3.55f, x + 5.0f, y + 4.7f, 3.2f,
            0xFFFFC169.toInt())
        round(c, x - 4.1f, y - 2.7f, x + 4.1f, y + 3.6f, 2.7f,
            0xFF217DAB.toInt())
        round(c, x - 3.5f, y - 2.2f, x + 3.3f, y + 1.1f, 1.8f,
            0xFF74C8E9.toInt())
        line(c, x, y - 2.7f, x, y + 3.9f, 0xFFE8AB56.toInt(), .86f)
        line(c, x - 4.0f, y + .3f, x + 4.0f, y + .3f,
            0xFFE8AB56.toInt(), .86f)
    }

    private fun blossomCluster(c: Canvas, x: Float, y: Float) {
        dot(c, x - 3f, y + .1f, 3.6f, 0xFF397F35.toInt())
        dot(c, x + 2.0f, y - .5f, 3.8f, 0xFF4FB34C.toInt())
        dot(c, x + 4.1f, y + .6f, 2.8f, 0xFF326D31.toInt())
        flower(c, x - 1.7f, y, .64f, 0xFFFFE6E5.toInt())
        flower(c, x + 3.4f, y - 1.5f, .58f, 0xFFFF5E52.toInt())
    }

    private fun flower(c: Canvas, x: Float, y: Float, size: Float, petal: Int) {
        for (i in 0 until 5) {
            val a = i * Math.PI * 2.0 / 5.0
            val px = x + cos(a).toFloat() * 1.6f * size
            val py = y + sin(a).toFloat() * 1.6f * size
            dot(c, px, py, 1.36f * size, petal)
        }
        dot(c, x, y, 1.05f * size, 0xFFFFD449.toInt())
    }

    private fun fill(c: Canvas, shape: Path, color: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawPath(shape, paint)
    }

    private fun gradient(
        c: Canvas, shape: Path,
        x1: Float, y1: Float, x2: Float, y2: Float,
        start: Int, end: Int,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.shader = LinearGradient(x1, y1, x2, y2, start, end, Shader.TileMode.CLAMP)
        c.drawPath(shape, paint)
        paint.shader = null
    }

    private fun rect(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = color
        c.drawRect(left, top, right, bottom, paint)
    }

    private fun round(
        c: Canvas, left: Float, top: Float, right: Float, bottom: Float,
        radius: Float, color: Int,
    ) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = color
        c.drawRoundRect(left, top, right, bottom, radius, radius, paint)
    }

    private fun dot(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = color
        c.drawCircle(x, y, radius, paint)
    }

    private fun line(
        c: Canvas,
        x1: Float, y1: Float, x2: Float, y2: Float,
        color: Int, thickness: Float,
    ) {
        paint.style = Paint.Style.STROKE
        paint.shader = null
        paint.color = color
        paint.strokeWidth = thickness
        c.drawLine(x1, y1, x2, y2, paint)
        paint.style = Paint.Style.FILL
    }

    private fun palette(team: String): RoofPalette? = when (team) {
        "GREEN" -> RoofPalette(
            base = 0xFF21B45C.toInt(), bright = 0xFF64EC85.toInt(),
            shade = 0xFF138447.toInt(), deep = 0xFF09673B.toInt(),
            mortar = 0x99516D3C.toInt(), wall = 0xFFE7CDAB.toInt(),
        )
        "YELLOW" -> RoofPalette(
            base = 0xFFF4C12B.toInt(), bright = 0xFFFFE478.toInt(),
            shade = 0xFFC18B1B.toInt(), deep = 0xFF996F14.toInt(),
            mortar = 0x9B99762F.toInt(), wall = 0xFFEDD1A3.toInt(),
        )
        "BLUE" -> RoofPalette(
            base = 0xFF238FDE.toInt(), bright = 0xFF78D2FC.toInt(),
            shade = 0xFF1863AB.toInt(), deep = 0xFF0A4B87.toInt(),
            mortar = 0x9C315E96.toInt(), wall = 0xFFE5D1BA.toInt(),
        )
        "RED" -> RoofPalette(
            base = 0xFFEC4039.toInt(), bright = 0xFFFF8672.toInt(),
            shade = 0xFFB4202E.toInt(), deep = 0xFF8A1623.toInt(),
            mortar = 0x9B873B31.toInt(), wall = 0xFFEED4BA.toInt(),
        )
        else -> null
    }
}
