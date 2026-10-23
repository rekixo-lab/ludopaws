package com.rekixo.ludopaws

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A clean, entirely code-drawn home square inspired by the approved yard art.
 *
 * One composition is reused for all four Ludo team colors:
 * - square-to-square opaque cover: never exposes the old plain color strip
 * - a raised colored turf border and recessed golden-sand inner play space
 * - small corner timber fence details, sparse leaves, stones and daisies
 * - NO animals, pawn pads, central grass clutter, WebP atlas or image loading
 *
 * All four 5x5 physical-cell quadrants are painted, including inactive yards,
 * so two-player games retain a cohesive four-corner board design.
 * Rendering does not modify any pawn, path, home-slot or center geometry.
 *
 * Each static vector scene is rasterized once into a 512px bitmap per team.
 * Each frame then performs just four cached drawBitmap operations.
 */
internal class LudoPawsYardHabitatRenderer {
    private val bitmapCache = LruCache<String, Bitmap>(4)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    @Suppress("UNUSED_PARAMETER")
    fun draw(
        canvas: Canvas,
        cell: Float,
        habitatsByColor: Map<String, LudoPawsYardHabitat>,
    ) {
        if (!cell.isFinite() || cell <= 0f) return
        // The animal map is preserved for future optional animal-specific
        // decorations. Today's selected base design depends ONLY on team color.
        // The parent Chrome view already handles the player's perspective.
        for (color in listOf("RED", "GREEN", "YELLOW", "BLUE")) {
            val face = LudoPawsYardHabitatGeometry.face(color, cell) ?: continue
            val palette = LudoPawsYardPalettePolicy.forTeam(color) ?: continue
            val rect = RectF(face.left, face.top, face.right, face.bottom)
            val saved = canvas.save()
            // No rounded top-level clip: rounded corners would uncover the
            // old color quadrant beneath this full 5x5 replacement.
            canvas.clipRect(rect)
            canvas.drawBitmap(cachedBitmap(color, palette), null, rect, imagePaint)
            canvas.restoreToCount(saved)
        }
    }

    private fun cachedBitmap(color: String, palette: LudoPawsYardPalette): Bitmap {
        bitmapCache.get(color)?.let { return it }
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val scene = Canvas(bitmap)
        scene.scale(5.12f, 5.12f)
        paintHome(scene, palette)
        bitmapCache.put(color, bitmap)
        return bitmap
    }

    private fun paintHome(c: Canvas, palette: LudoPawsYardPalette) {
        // Underpaint every pixel; the four yards meet roads without gaps.
        rect(c, 0f, 0f, 100f, 100f, palette.rimDark)
        roundRect(c, 0.7f, 0.7f, 99.3f, 99.3f, 7.0f, palette.rim)
        outline(c, 1.1f, 1.1f, 98.9f, 98.9f, 7.1f, palette.rimLight, 1.5f)

        // Colored "grass" perimeter is ~13% wide; keep it open rather than
        // packing every tile with flowers. The ground takes ~72% of the square.
        gradient(c, RectF(2.1f, 2.1f, 97.9f, 97.9f),
            palette.bandLight, palette.bandDark, 6.4f)

        // Sparse background grass marks are limited to the OUTER band.
        val rng = Random(742)
        repeat(54) {
            val edge = it % 4
            val along = 3f + rng.nextFloat() * 94f
            val depth = 2.3f + rng.nextFloat() * 6.6f
            val (x, y) = when (edge) {
                0 -> along to depth
                1 -> (100f - depth) to along
                2 -> along to (100f - depth)
                else -> depth to along
            }
            tuft(c, x, y, 0.45f + rng.nextFloat() * 0.45f,
                palette.leafShade, 0x38FFFFFF)
        }

        // Soft inset gives the same carved dark-green lip as the reference.
        roundRect(c, 10.4f, 10.8f, 89.6f, 89.7f, 11.6f, palette.recessShadow)
        roundRect(c, 11.4f, 11.1f, 88.6f, 88.7f, 11.4f, palette.lipDark)
        roundRect(c, 12.3f, 12.3f, 87.7f, 87.6f, 10.8f, palette.lipLight)
        gradient(c, RectF(14.0f, 14.1f, 86.0f, 85.8f),
            palette.sandTop, palette.sandBottom, 9.5f)
        outline(c, 14.1f, 14.1f, 85.9f, 85.7f,
            9.4f, 0x5BFFF6C8, 0.8f)

        // The four animals stand at canonical 30/70% positions. Leave the
        // ENTIRE 20..80% interior free of scenery and obvious painted slots.
        // Only faint earthen stippling hints at texture, never dark spots.
        val dust = Random(2009)
        repeat(35) {
            val x = 16f + dust.nextFloat() * 68f
            val y = 16f + dust.nextFloat() * 68f
            val radius = 0.13f + dust.nextFloat() * 0.46f
            ellipse(c, x, y, radius * 1.30f, radius, palette.sandSpeck)
        }

        // Small fence pieces and plants are around CORNERS and outer ring,
        // with no props behind faces or paws in the wide inner clearing.
        for (horizontal in listOf(false, true)) {
            for (vertical in listOf(false, true)) {
                val save = c.save()
                if (horizontal) { c.translate(100f, 0f); c.scale(-1f, 1f) }
                if (vertical) { c.translate(0f, 100f); c.scale(1f, -1f) }
                cornerFoliage(c, palette)
                cornerTimber(c)
                c.restoreToCount(save)
            }
        }

        // A handful of flowers and stones on the OUTER band, not on the sand.
        val flowers = listOf(
            Triple(34f, 6.0f, 0.82f), Triple(66f, 6.3f, 0.90f),
            Triple(6.0f, 33f, 0.80f), Triple(94f, 32f, 0.80f),
            Triple(6.3f, 68f, 0.91f), Triple(93.4f, 69f, 0.84f),
            Triple(34f, 94f, 0.78f), Triple(66f, 94f, 0.95f),
        )
        for ((x, y, size) in flowers) flower(c, x, y, size)
        rock(c, 45f, 5.6f, 1f)
        rock(c, 94f, 55f, 0.85f)
        rock(c, 5.5f, 58f, 0.75f)
        rock(c, 59f, 94.5f, 1.05f)

        // Delicate glossy outer edge reads cleanly at the actual phone size.
        outline(c, 0.9f, 0.9f, 99.1f, 99.1f, 7f, palette.rimLight, 0.70f)
    }

    private fun cornerFoliage(c: Canvas, palette: LudoPawsYardPalette) {
        // A few broad cartoon leaves, not a continuous dense jungle carpet.
        for ((x, y, angle, size) in listOf(
            listOf(4.2f, 4.2f, -38f, 1.20f),
            listOf(21.7f, 7.7f, 40f, 0.82f),
            listOf(7.0f, 22f, -42f, 0.75f),
        )) {
            leafFan(c, x, y, angle, size, palette)
        }
    }

    private fun leafFan(
        c: Canvas, x: Float, y: Float, angle: Float,
        size: Float, colors: LudoPawsYardPalette,
    ) {
        val saved = c.save()
        c.rotate(angle, x, y)
        for (i in 0..3) {
            val a = -64f + i * 39f
            val t = Math.toRadians(a.toDouble())
            val px = x + cos(t).toFloat() * 2.0f * size
            val py = y - 1.0f * size + sin(t).toFloat() * 1.7f * size
            val leafSaved = c.save()
            c.rotate(a, px, py)
            ellipse(c, px, py, 1.7f * size, 3.0f * size,
                if (i % 2 == 0) colors.leafLight else colors.leafShade)
            ellipse(c, px - 0.4f * size, py - 0.9f * size,
                0.65f * size, 1.35f * size, 0x51E8FFBC)
            c.restoreToCount(leafSaved)
        }
        c.restoreToCount(saved)
    }

    private fun cornerTimber(c: Canvas) {
        // Wooden rail with bevel/highlight, plus two top-facing round posts.
        timberRail(c, 5.4f, 17.2f, 17.2f, 5.4f)
        timberRail(c, 17f, 5.4f, 23.6f, 5.4f)
        timberRail(c, 5.4f, 17.2f, 5.4f, 23.1f)
        timberPost(c, 5.4f, 17.2f)
        timberPost(c, 17.2f, 5.4f)
    }

    private fun timberRail(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        line(c, x1, y1 + 0.7f, x2, y2 + 0.7f, 0x904B2F17.toInt(), 5.5f)
        line(c, x1, y1, x2, y2, 0xFFB26A2F.toInt(), 4.6f)
        line(c, x1, y1 - 0.55f, x2, y2 - 0.55f, 0xFFF7BB62.toInt(), 2.8f)
        line(c, x1, y1 - 1.25f, x2, y2 - 1.25f, 0x9CFFE6A8.toInt(), 0.62f)
    }

    private fun timberPost(c: Canvas, x: Float, y: Float) {
        ellipse(c, x + 0.55f, y + 1.65f, 3.0f, 3.15f, 0x66562B16)
        roundRect(c, x - 2.55f, y - 1.6f, x + 2.55f, y + 2.6f,
            1.75f, 0xFFA05B2D.toInt())
        ellipse(c, x, y - 1.0f, 2.7f, 2.5f, 0xFF8C4E26.toInt())
        ellipse(c, x, y - 1.3f, 2.38f, 2.18f, 0xFFFFCF7C.toInt())
        ellipse(c, x - 0.48f, y - 1.89f, 1.18f, 0.80f, 0xB1FFF1BC.toInt())
    }

    private fun flower(c: Canvas, x: Float, y: Float, size: Float) {
        ellipse(c, x + 0.6f * size, y + 0.8f * size,
            3.4f * size, 2.3f * size, 0x4D315E20)
        for (i in 0 until 5) {
            val a = i * (Math.PI * 2.0 / 5.0)
            val px = x + cos(a).toFloat() * 2.05f * size
            val py = y + sin(a).toFloat() * 2.05f * size
            ellipse(c, px, py, 1.55f * size, 1.70f * size, Color.WHITE)
            ellipse(c, px - 0.32f * size, py - 0.36f * size,
                0.70f * size, 0.74f * size, 0xD7FFFFFF.toInt())
        }
        ellipse(c, x, y, 1.45f * size, 1.45f * size, 0xFFFFB424.toInt())
        ellipse(c, x - 0.34f * size, y - 0.48f * size,
            0.68f * size, 0.63f * size, 0xFFFFEA9D.toInt())
    }

    private fun rock(c: Canvas, x: Float, y: Float, scale: Float) {
        ellipse(c, x + 0.5f * scale, y + 1.3f * scale,
            2.5f * scale, 1.2f * scale, 0x493F4A3B)
        ellipse(c, x, y, 2.1f * scale, 1.65f * scale, 0xFF777E75.toInt())
        ellipse(c, x - 0.42f * scale, y - 0.55f * scale,
            1.32f * scale, 0.82f * scale, 0xFFC5C6B6.toInt())
    }

    private fun tuft(
        c: Canvas, x: Float, y: Float, scale: Float, dark: Int, shine: Int,
    ) {
        line(c, x, y + 1.05f * scale, x - 1.8f * scale,
            y - 2.5f * scale, dark, 1.05f * scale)
        line(c, x, y + 1.05f * scale, x + 0.1f * scale,
            y - 3.0f * scale, dark, 1.0f * scale)
        line(c, x, y + 1.05f * scale, x + 1.85f * scale,
            y - 2.0f * scale, dark, 1.05f * scale)
        ellipse(c, x, y - 1.6f * scale,
            0.25f * scale, 0.52f * scale, shine)
    }

    private fun gradient(
        c: Canvas, bounds: RectF, top: Int, bottom: Int, radius: Float,
    ) {
        brush.style = Paint.Style.FILL
        brush.color = Color.WHITE
        brush.shader = LinearGradient(
            bounds.left, bounds.top, bounds.right, bounds.bottom,
            top, bottom, Shader.TileMode.CLAMP,
        )
        c.drawRoundRect(bounds, radius, radius, brush)
        brush.shader = null
    }

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        brush.style = Paint.Style.FILL
        brush.color = color
        c.drawRect(l, t, r, b, brush)
    }

    private fun roundRect(
        c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int,
    ) {
        brush.style = Paint.Style.FILL
        brush.color = color
        c.drawRoundRect(l, t, r, b, radius, radius, brush)
    }

    private fun outline(
        c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float,
        color: Int, width: Float,
    ) {
        brush.style = Paint.Style.STROKE
        brush.color = color
        brush.strokeWidth = width
        c.drawRoundRect(l, t, r, b, radius, radius, brush)
        brush.style = Paint.Style.FILL
    }

    private fun ellipse(
        c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int,
    ) {
        brush.style = Paint.Style.FILL
        brush.color = color
        c.drawOval(x - rx, y - ry, x + rx, y + ry, brush)
    }

    private fun line(
        c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int,
        stroke: Float,
    ) {
        brush.style = Paint.Style.STROKE
        brush.color = color
        brush.strokeWidth = stroke
        c.drawLine(x1, y1, x2, y2, brush)
        brush.style = Paint.Style.FILL
    }
}
