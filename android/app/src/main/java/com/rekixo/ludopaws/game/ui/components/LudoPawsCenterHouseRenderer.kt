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
 * Four richly layered 2.5D storybook houses, drawn with Kotlin Canvas.
 *
 * The board's 5x5-cell center retains its EXACT four gameplay triangles.
 * Each roof meets the same (50,50) apex; the walls, windows and entry face
 * outward toward each team's approach lane.
 *
 * This is NOT four rectangular sprites painted on top of the Ludo paths.
 * Every architectural detail (facade, beveled eaves, roof tiles, greenery,
 * doorway) is clipped to its own original triangular region. One pre-rendered
 * 1024x1024 bitmap keeps all roof gradients and tile paths off hot frames.
 * The game still renders finish pawns and rank labels above the art.
 */
internal class LudoPawsCenterHouseRenderer {
    private data class HouseColors(
        val roof: Int,
        val roofLight: Int,
        val roofShade: Int,
        val roofDeep: Int,
        val grout: Int,
        val accent: Int,
    )

    private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var cachedArtwork: Bitmap? = null

    fun draw(canvas: Canvas, cell: Float) {
        val square = LudoPawsCenterHouseGeometry.bounds(cell) ?: return
        val art = cachedArtwork ?: renderOnce().also { cachedArtwork = it }
        val destination = RectF(square.left, square.top, square.right, square.bottom)
        val save = canvas.save()
        canvas.clipRect(destination)
        canvas.drawBitmap(art, null, destination, bitmapPaint)
        canvas.restoreToCount(save)
    }

    private fun renderOnce(): Bitmap {
        val art = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val c = Canvas(art)
        c.scale(10.24f, 10.24f)

        // The roof colors match the released board, not the home yard positions.
        for (team in listOf("GREEN", "YELLOW", "BLUE", "RED")) {
            val triangle = requireNotNull(LudoPawsCenterHouseGeometry.triangle(team))
            val turn = requireNotNull(LudoPawsCenterHouseGeometry.rotationDegrees(team))
            val colors = requireNotNull(colorsFor(team))
            check(triangle.apex == LudoPawsRoofPoint(50f, 50f))
            val save = c.save()
            c.rotate(turn, 50f, 50f)
            drawHouseFacingTop(c, colors)
            c.restoreToCount(save)
        }
        // Dark, thin joints hide any subpixel antialiasing seams, but no opaque
        // disk is placed on the four-way finish apex where the pawns arrive.
        val corners = arrayOf(
            0f to 0f, 100f to 0f, 100f to 100f, 0f to 100f,
        )
        for ((x, y) in corners) {
            stroke(c, x, y, 50f, 50f, 0x602A211F, 0.64f)
        }
        return art
    }

    private fun drawHouseFacingTop(c: Canvas, colors: HouseColors) {
        val region = triangle(0f, 0f, 100f, 0f, 50f, 50f)
        val save = c.save()
        c.clipPath(region)

        // The wide wedge is the HOUSE, not an oversized second roof. Warm
        // plaster remains visible around the new compact colored roof.
        fill(c, region, 0xFFE3C5A3.toInt())
        gradient(c, region, 0f, 0f, 72f, 45f,
            0xFFFFF5DB.toInt(), 0xFFDFC09B.toInt())

        // 1. Broad ivory wall with a darker cast shadow underneath the eaves.
        val wall = polygon(
            0f, 0f, 100f, 0f, 80f, 20f, 20f, 20f,
        )
        gradient(c, wall, 4f, 0f, 5f, 21f, 0xFFFFF9E7.toInt(), 0xFFE3C4A1.toInt())
        // Corner stone bases, paneled wall and pale sunlit plaster.
        stroke(c, 1f, 0.9f, 99f, 0.9f, 0xBFFFF5DC.toInt(), 1.1f)
        stroke(c, 12f, 9f, 20.3f, 18.7f, 0x44AD8256, 0.78f)
        stroke(c, 88f, 9f, 79.7f, 18.7f, 0x44AD8256, 0.78f)
        insetWallTrim(c)

        // Oversized arched entrance and windows: readable at actual phone
        // size, rather than tiny repeated badges around a flat roof emblem.
        window(c, 24f, 7.5f, 1.06f)
        window(c, 76f, 7.5f, 1.06f)
        doorway(c)

        // Tiny supporting plants are behind the roof eave, not a solid
        // ornamental hedge all the way around the triangle.
        flowerBox(c, 10.5f, 14.5f, .88f)
        flowerBox(c, 89.5f, 14.5f, .88f)

        // 2. The roof is NOW a compact triangle INSIDE the original wedge.
        // Base moved toward the apex and both ends drawn inward symmetrically.
        // IMPORTANT: only roof geometry is changed; door/windows/steps stay.
        val roofBounds = LudoPawsCenterHouseGeometry.compactRoof()
        val left = roofBounds.start.x
        val right = roofBounds.end.x
        val baseY = roofBounds.start.y
        val peakX = roofBounds.apex.x
        val peakY = roofBounds.apex.y
        val spanY = peakY - baseY

        // Small cast shadow + thick eave following the *new* roof, not the
        // old 76-unit span. No leftover roof edges beside the cream wall.
        val underEave = polygon(
            left - 1.0f, baseY + 2.7f,
            right + 1.0f, baseY + 2.7f,
            peakX, peakY, peakX, peakY - 1.9f,
        )
        fill(c, underEave, 0x59372020)
        val roofThickness = polygon(
            left - 0.75f, baseY + 1.4f,
            right + 0.75f, baseY + 1.4f,
            right, baseY + 3.7f,
            peakX, peakY,
            left, baseY + 3.7f,
        )
        gradient(c, roofThickness, left, baseY + 1.0f,
            right, peakY - 4f, colors.roofShade, colors.roofDeep)

        // 3. Separate light and shaded roof planes. The center ridge still
        // reaches exactly (50,50), shared by all four colored finish wedges.
        val roof = triangle(left, baseY, right, baseY, peakX, peakY)
        gradient(c, roof, left, baseY, right, peakY - 2f,
            colors.roofLight, colors.roof)
        val leftPlane = triangle(left, baseY, peakX, baseY, peakX, peakY)
        gradient(c, leftPlane, left, baseY, peakX, peakY - 3f,
            0x72FFFFFF, 0x00FFFFFF)
        val rightPlane = triangle(peakX, baseY, right, baseY, peakX, peakY)
        gradient(c, rightPlane, peakX, baseY, right, peakY - 3f,
            0x08000000, 0x680B0911)

        // 4. The tiles scale WITH the smaller roof instead of keeping the
        // old 9-row wide triangle that caused clutter and stray grout lines.
        val shingles = c.save()
        c.clipPath(roof)
        val rowCount = 7
        val rowHeight = (spanY - 1.8f) / rowCount
        for (row in 0 until rowCount) {
            val y0 = baseY + 1.3f + row * rowHeight
            val y1 = (y0 + rowHeight).coerceAtMost(peakY - .25f)
            if (y0 >= peakY - .25f) break

            // Interpolate X from the ACTUAL straight roof edges so shrinking
            // the base never leaves old-size tiles outside the new triangle.
            val t0 = ((y0 - baseY) / spanY).coerceIn(0f, 1f)
            val t1 = ((y1 - baseY) / spanY).coerceIn(0f, 1f)
            val left0 = left + (peakX - left) * t0
            val right0 = right + (peakX - right) * t0
            val left1 = left + (peakX - left) * t1
            val right1 = right + (peakX - right) * t1

            val columns = when {
                row < 3 -> 7
                row < 5 -> 5
                else -> 3
            }
            val stagger = if (row % 2 == 0) 0f else .5f
            for (col in 0 until columns) {
                val u0 = ((col + stagger) / columns).coerceAtMost(1f)
                val u1 = ((col + 1f + stagger) / columns).coerceAtMost(1f)
                if (u1 <= u0) continue
                val x00 = left0 + (right0 - left0) * u0
                val x01 = left0 + (right0 - left0) * u1
                val x10 = left1 + (right1 - left1) * u0
                val x11 = left1 + (right1 - left1) * u1
                val tile = polygon(
                    x00 + .20f, y0 + .12f,
                    x01 - .20f, y0 + .12f,
                    x11 - .20f, y1 - .19f,
                    x10 + .20f, y1 - .19f,
                )
                val base = when ((row + col) % 4) {
                    0 -> colors.roof
                    1 -> colors.roofLight
                    else -> colors.accent
                }
                gradient(c, tile, x00, y0, x11, y1,
                    blend(base, colors.roofLight, .29f),
                    blend(base, colors.roofShade, .38f))
                stroke(c, x10 + .19f, y1 - .12f, x11 - .19f, y1 - .12f,
                    colors.grout, .66f)
                stroke(c, x00 + .5f, y0 + .45f, x01 - .4f, y0 + .45f,
                    0x84FFFFFF.toInt(), .35f)
            }
        }
        c.restoreToCount(shingles)

        // 5. All raised edges follow the new roof footprint. The tip, not
        // its base, remains anchored at the game's true central point.
        stroke(c, left - 0.75f, baseY + 2.8f,
            right + 0.75f, baseY + 2.8f, colors.roofDeep, 2.30f)
        stroke(c, left, baseY, right, baseY, colors.roofShade, 2.40f)
        stroke(c, left + .7f, baseY - 1.0f,
            right - .7f, baseY - 1.0f, colors.roofLight, 1.34f)
        stroke(c, left, baseY, peakX, peakY, colors.roofDeep, 1.46f)
        stroke(c, right, baseY, peakX, peakY, colors.roofDeep, 1.46f)
        stroke(c, left + .5f, baseY - .1f,
            peakX - .1f, peakY - .3f, 0x79FFFFFF, .62f)
        stroke(c, peakX, baseY, peakX, peakY - .3f,
            colors.roofDeep, 1.55f)
        stroke(c, peakX - .5f, baseY + .2f,
            peakX - .5f, peakY - .9f, 0xBDFFECC7.toInt(), .70f)

        // The existing stair/door remain untouched in their original facade
        // positions. The smaller roof now sits behind the visible entrance.
        step(c, 40.3f, 16.7f, 59.7f, 19.6f,
            0xFFAB9077.toInt(), 0xFFFFE9C8.toInt())
        step(c, 42.8f, 15.7f, 57.2f, 18.2f,
            0xFFB69C81.toInt(), 0xFFFFF6DE.toInt())
        c.restoreToCount(save)
    }

    private fun insetWallTrim(c: Canvas) {
        // Narrow pilasters with warm side shadows make the facade feel solid.
        for (x in listOf(15.9f, 84.1f)) {
            round(c, x - 1.35f, 1.5f, x + 1.35f, 16.9f,
                .75f, 0xFFBDA380.toInt())
            round(c, x - 1.05f, 1.5f, x + .65f, 16.5f,
                .68f, 0xFFFFEED0.toInt())
            stroke(c, x - .75f, 2f, x - .75f, 15.6f,
                0x9BFFF8E7.toInt(), .50f)
        }
    }

    private fun doorway(c: Canvas) {
        // Three nested arched layers plus door planks, brass knob, recess and
        // step cast-shadow. The opening is intentionally the facade's focal point.
        val opening = arch(40.6f, 1.6f, 59.4f, 17.8f, 9.15f)
        fill(c, opening, 0xFF6D381C.toInt())
        val gold = arch(41.5f, 2.2f, 58.5f, 17.1f, 8.2f)
        gradient(c, gold, 43f, 3f, 57f, 16f,
            0xFFFFD27B.toInt(), 0xFFB96820.toInt())
        val inside = arch(43.4f, 3.9f, 56.6f, 16.8f, 6.4f)
        gradient(c, inside, 44f, 5f, 57f, 16f,
            0xFF643111.toInt(), 0xFF9C4E1B.toInt())
        for (x in listOf(46.6f, 50f, 53.4f)) {
            stroke(c, x, 8f, x, 16.6f, 0x65401B0F, .52f)
        }
        stroke(c, 44.8f, 8f, 44.8f, 16f, 0x81F7AB64.toInt(), .46f)
        dot(c, 53.4f, 11.2f, 1.12f, 0xFF60300D.toInt())
        dot(c, 53.1f, 10.8f, .80f, 0xFFFFCC36.toInt())
        dot(c, 52.85f, 10.5f, .28f, 0xFFFFF9C6.toInt())
    }

    private fun window(c: Canvas, x: Float, y: Float, size: Float) {
        val saved = c.save()
        c.translate(x, y)
        c.scale(size, size)
        fill(c, arch(-7.15f, -5.6f, 7.15f, 5.65f, 6.4f), 0xFF9D5222.toInt())
        fill(c, arch(-6.1f, -4.9f, 6.1f, 4.95f, 5.7f), 0xFFFFC26A.toInt())
        fill(c, arch(-4.9f, -3.85f, 4.9f, 3.92f, 4.8f), 0xFF175B93.toInt())
        // Blue glass gradient and translucent reflection.
        val glass = arch(-4.3f, -3.2f, 4.3f, 3.45f, 4f)
        gradient(c, glass, -4f, -3.2f, 4f, 3.5f,
            0xFF6DD9FE.toInt(), 0xFF1666AF.toInt())
        stroke(c, -3.0f, -2.4f, 1.6f, 2.3f, 0x8EFFFFFF.toInt(), 1.3f)
        stroke(c, 0f, -3.8f, 0f, 3.9f, 0xFFF6BA59.toInt(), 1.1f)
        stroke(c, -4.5f, .85f, 4.5f, .85f, 0xFFE8A849.toInt(), .93f)
        round(c, -7.1f, 4.1f, 7.1f, 6.25f, .75f, 0xFFC88335.toInt())
        round(c, -6.9f, 4.0f, 6.9f, 5.1f, .60f, 0xFFFFDB90.toInt())
        c.restoreToCount(saved)
    }

    private fun flowerBox(c: Canvas, x: Float, y: Float, size: Float) {
        val s = c.save()
        c.translate(x, y)
        c.scale(size, size)
        // Dark underside and wooden trough are only a subtle accent.
        oval(c, 0f, 2.2f, 7.7f, 2.6f, 0x653F2B22)
        for (i in 0 until 5) {
            val dx = (i - 2) * 2.15f
            leaf(c, dx, -1.3f, 3.1f, 2.1f,
                if (i % 2 == 0) 0xFF2C9147.toInt() else 0xFF63C75C.toInt())
        }
        flower(c, -3.6f, -2.3f, .83f, 0xFFFF6B5F.toInt())
        flower(c, 1.4f, -2.7f, .79f, 0xFFFFFCF4.toInt())
        flower(c, 4.6f, -1.8f, .60f, 0xFFFFB33D.toInt())
        round(c, -6.25f, .9f, 6.25f, 3.3f, .7f, 0xFFB9772C.toInt())
        round(c, -5.85f, 1.0f, 5.85f, 2f, .45f, 0xFFFFBE5B.toInt())
        c.restoreToCount(s)
    }

    private fun flower(c: Canvas, x: Float, y: Float, size: Float, petal: Int) {
        for (i in 0 until 5) {
            val a = i * Math.PI * 2.0 / 5.0
            dot(c, x + cos(a).toFloat() * 2f * size,
                y + sin(a).toFloat() * 2f * size, 1.6f * size, petal)
        }
        dot(c, x, y, 1.35f * size, 0xFFFFD33E.toInt())
        dot(c, x - .25f * size, y - .35f * size, .5f * size, 0xFFFFF7B5.toInt())
    }

    private fun step(
        c: Canvas, l: Float, top: Float, r: Float, bottom: Float,
        shade: Int, topColor: Int,
    ) {
        round(c, l, top + .85f, r, bottom + .85f, .85f, shade)
        round(c, l + .7f, top, r - .7f, bottom - .35f, .8f, topColor)
        stroke(c, l + 1.3f, top + .45f, r - 1.3f, top + .45f,
            0xACFFFFFF.toInt(), .50f)
    }

    private fun arch(l: Float, top: Float, r: Float, bottom: Float, rise: Float): Path {
        val x = (l + r) / 2f
        val radius = (r - l) / 2f
        val shoulder = top + rise
        return Path().apply {
            moveTo(l, bottom)
            lineTo(l, shoulder)
            cubicTo(l, top - .8f, x - radius * .25f, top, x, top)
            cubicTo(x + radius * .25f, top, r, top - .8f, r, shoulder)
            lineTo(r, bottom)
            close()
        }
    }

    private fun triangle(
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
    ): Path = Path().apply {
        moveTo(x0, y0)
        lineTo(x1, y1)
        lineTo(x2, y2)
        close()
    }

    private fun polygon(vararg points: Float): Path = Path().apply {
        require(points.size >= 6 && points.size % 2 == 0)
        moveTo(points[0], points[1])
        var i = 2
        while (i < points.size) {
            lineTo(points[i], points[i + 1])
            i += 2
        }
        close()
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) * (1f - f) + Color.red(b) * f).toInt(),
            (Color.green(a) * (1f - f) + Color.green(b) * f).toInt(),
            (Color.blue(a) * (1f - f) + Color.blue(b) * f).toInt(),
        )
    }

    private fun fill(c: Canvas, shape: Path, color: Int) {
        brush.shader = null
        brush.style = Paint.Style.FILL
        brush.color = color
        c.drawPath(shape, brush)
    }

    private fun gradient(
        c: Canvas, path: Path,
        x0: Float, y0: Float, x1: Float, y1: Float, from: Int, to: Int,
    ) {
        brush.style = Paint.Style.FILL
        brush.color = Color.WHITE
        brush.shader = LinearGradient(x0, y0, x1, y1, from, to, Shader.TileMode.CLAMP)
        c.drawPath(path, brush)
        brush.shader = null
    }

    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int) {
        brush.shader = null
        brush.style = Paint.Style.FILL
        brush.color = color
        c.drawOval(x-rx, y-ry, x+rx, y+ry, brush)
    }

    private fun leaf(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int) {
        oval(c, x, y, rx, ry, color)
        oval(c, x-rx*.25f, y-ry*.29f, rx*.43f, ry*.45f, 0x46FFFFFF)
    }

    private fun dot(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        brush.shader = null
        brush.style = Paint.Style.FILL
        brush.color = color
        c.drawCircle(x, y, radius, brush)
    }

    private fun round(
        c: Canvas, l: Float, t: Float, r: Float, b: Float,
        rad: Float, color: Int,
    ) {
        brush.shader = null
        brush.style = Paint.Style.FILL
        brush.color = color
        c.drawRoundRect(l, t, r, b, rad, rad, brush)
    }

    private fun stroke(
        c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float,
        color: Int, width: Float,
    ) {
        brush.shader = null
        brush.style = Paint.Style.STROKE
        brush.color = color
        brush.strokeWidth = width
        c.drawLine(x0, y0, x1, y1, brush)
        brush.style = Paint.Style.FILL
    }

    private fun colorsFor(team: String): HouseColors? = when (team) {
        "GREEN" -> HouseColors(
            roof = 0xFF27BD62.toInt(), roofLight = 0xFF86F7A0.toInt(),
            roofShade = 0xFF0D8A48.toInt(), roofDeep = 0xFF07522F.toInt(),
            grout = 0xBF145538.toInt(), accent = 0xFF43D975.toInt(),
        )
        "YELLOW" -> HouseColors(
            roof = 0xFFF3BC23.toInt(), roofLight = 0xFFFFE784.toInt(),
            roofShade = 0xFFBA861A.toInt(), roofDeep = 0xFF76540E.toInt(),
            grout = 0xB881641E.toInt(), accent = 0xFFF7D048.toInt(),
        )
        "BLUE" -> HouseColors(
            roof = 0xFF298DDA.toInt(), roofLight = 0xFF8CDEFC.toInt(),
            roofShade = 0xFF145BA4.toInt(), roofDeep = 0xFF0C3B72.toInt(),
            grout = 0xBF194A75.toInt(), accent = 0xFF48AAEF.toInt(),
        )
        "RED" -> HouseColors(
            roof = 0xFFF03D3C.toInt(), roofLight = 0xFFFF8E78.toInt(),
            roofShade = 0xFFB41D2C.toInt(), roofDeep = 0xFF751225.toInt(),
            grout = 0xBF7E2933.toInt(), accent = 0xFFFF5F57.toInt(),
        )
        else -> null
    }
}
