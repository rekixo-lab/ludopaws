package com.rekixo.ludopaws.feature.boards.content.classic

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.math.min

import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.boards.rendering.BoardRenderer

/** Per-view paint/cache ownership, also reusable by previews. */
internal class ClassicBoardRenderer : BoardRenderer {
    private var density = 1f
    private var theme = ClassicBoardDefinition.content
    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val strokePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }
    private val shadowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x50000000
        }
    // One deterministic, low-contrast bitmap is reused for every surface and frame.
    // Transparent pixels preserve the team's exact base colors; tiny black/white
    // fibers only break up the overly smooth, glossy finish.
    private val grainPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader =
                BitmapShader(
                    createGrainBitmap(),
                    Shader.TileMode.REPEAT,
                    Shader.TileMode.REPEAT,
                )
        }


    private val stoneGrainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(createStoneGrainBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private val pebbleShader = BitmapShader(
        createPebbleBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT,
    )
    private val pebblePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = pebbleShader
    }
    private val pebbleMatrix = Matrix()
    private val rockReliefShader = BitmapShader(
        createRockReliefBitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT,
    )
    private val rockReliefPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        shader = rockReliefShader
        alpha = 100
    }
    private val rockReliefMatrix = Matrix()

    override fun draw(canvas: Canvas, size: Float, density: Float, definition: BoardDefinition) {
        this.density = density
        theme = definition
        val cell = size / 15f
        drawYardDepth(canvas, cell)
        drawCrossTileDepth(canvas, cell)
        drawCenterDepth(canvas, cell)
    }

    private fun drawYardDepth(
        canvas: Canvas,
        cell: Float,
    ) {
        drawRaisedYard(canvas, cell, row = 0, col = 0, color = theme.teamColor(RED))
        drawRaisedYard(canvas, cell, row = 0, col = 9, color = theme.teamColor(GREEN))
        drawRaisedYard(canvas, cell, row = 9, col = 9, color = theme.teamColor(YELLOW))
        drawRaisedYard(canvas, cell, row = 9, col = 0, color = theme.teamColor(BLUE))
    }

    private fun drawRaisedYard(
        canvas: Canvas,
        cell: Float,
        row: Int,
        col: Int,
        color: Int,
    ) {
        val yard =
            RectF(
                axisBoundary(col, cell),
                axisBoundary(row, cell),
                axisBoundary(col + 6, cell),
                axisBoundary(row + 6, cell),
            )

        // Matte quadrant treatment: keep the color vivid but lower the broad
        // white reflection that made the corner feel like polished plastic.
        fillPaint.shader =
            LinearGradient(
                yard.left,
                yard.top,
                yard.right,
                yard.bottom,
                intArrayOf(
                    0x24FFFFFF,
                    0x08FFFFFF,
                    0x1C000000,
                ),
                floatArrayOf(0f, 0.46f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(yard, fillPaint)
        fillPaint.shader = null
        drawGrain(canvas, yard)

        val platform =
            RectF(
                yard.left + cell * 0.50f,
                yard.top + cell * 0.50f,
                yard.right - cell * 0.50f,
                yard.bottom - cell * 0.50f,
            )
        val radius = cell * 0.40f
        val depth = cell * 0.13f

        // The legacy board underneath draws a dark rounded stroke exactly on this
        // platform boundary. Cover the tiny outward half of that stroke with a
        // slightly larger molded shell so no black pencil-like outline can leak
        // around the glossy home platform in normal Ludo Paws presentation.
        val outlineCover = dp(1.4f)
        val platformShell =
            RectF(
                platform.left - outlineCover,
                platform.top - outlineCover,
                platform.right + outlineCover,
                platform.bottom + outlineCover,
            )
        val shellRadius = radius + outlineCover
        fillPaint.shader =
            LinearGradient(
                platformShell.left,
                platformShell.top,
                platformShell.right,
                platformShell.bottom,
                intArrayOf(
                    brighten(color, 1.24f),
                    brighten(color, 1.10f),
                    color,
                    darken(color, 0.80f),
                ),
                floatArrayOf(0f, 0.25f, 0.68f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRoundRect(
            platformShell,
            shellRadius,
            shellRadius,
            fillPaint,
        )
        fillPaint.shader = null

        // Soft lower body: this reads as thickness instead of a black border line.
        val platformDepth =
            RectF(
                platform.left + cell * 0.025f,
                platform.top + depth,
                platform.right + cell * 0.025f,
                platform.bottom + depth,
            )
        fillPaint.shader =
            LinearGradient(
                platformDepth.left,
                platformDepth.top,
                platformDepth.right,
                platformDepth.bottom,
                intArrayOf(
                    darken(color, 0.76f),
                    darken(color, 0.58f),
                    darken(color, 0.42f),
                ),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRoundRect(
            platformDepth,
            radius,
            radius,
            fillPaint,
        )
        fillPaint.shader = null

        // Broad outer bevel. Because this is a filled surface rather than a stroke,
        // the rim fades naturally around the curved corners like molded plastic.
        fillPaint.shader =
            LinearGradient(
                platform.left,
                platform.top,
                platform.right,
                platform.bottom,
                intArrayOf(
                    brighten(color, 1.28f),
                    brighten(color, 1.12f),
                    color,
                    darken(color, 0.80f),
                ),
                floatArrayOf(0f, 0.24f, 0.66f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRoundRect(
            platform,
            radius,
            radius,
            fillPaint,
        )
        fillPaint.shader = null

        val face =
            RectF(
                platform.left + cell * 0.115f,
                platform.top + cell * 0.105f,
                platform.right - cell * 0.115f,
                platform.bottom - cell * 0.145f,
            )
        val faceRadius = radius * 0.74f

        // Ludo Paws requires clean white token bays. Keep the team-colored molded
        // rim/depth around the bay, but never paint team color over the white face.
        fillPaint.shader = null
        fillPaint.color = Color.WHITE
        canvas.drawRoundRect(
            face,
            faceRadius,
            faceRadius,
            fillPaint,
        )

        // Diffuse top sheen: a broad translucent fill, intentionally not a stroke.
        val sheen =
            RectF(
                face.left + cell * 0.035f,
                face.top + cell * 0.025f,
                face.right - cell * 0.035f,
                face.top + face.height() * 0.46f,
            )
        fillPaint.shader =
            LinearGradient(
                sheen.left,
                sheen.top,
                sheen.left,
                sheen.bottom,
                intArrayOf(
                    0x20FFFFFF,
                    0x08FFFFFF,
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRoundRect(
            sheen,
            faceRadius * 0.90f,
            faceRadius * 0.90f,
            fillPaint,
        )
        fillPaint.shader = null
        // Apply grain last so the colored pawn platform reads as tactile matte material.
        drawGrain(canvas, face, faceRadius)
    }

    private fun drawCrossTileDepth(
        canvas: Canvas,
        cell: Float,
    ) {
        for (row in 0 until 6) {
            for (col in 6 until 9) {
                drawRaisedCellOverlay(canvas, cell, row, col)
            }
        }
        for (row in 9 until 15) {
            for (col in 6 until 9) {
                drawRaisedCellOverlay(canvas, cell, row, col)
            }
        }
        for (row in 6 until 9) {
            for (col in 0 until 6) {
                drawRaisedCellOverlay(canvas, cell, row, col)
            }
        }
        for (row in 6 until 9) {
            for (col in 9 until 15) {
                drawRaisedCellOverlay(canvas, cell, row, col)
            }
        }
    }

    private fun drawRaisedCellOverlay(
        canvas: Canvas,
        cell: Float,
        row: Int,
        col: Int,
    ) {
        if ((row to col) in NEUTRAL_SAFE_CELLS || (row to col) in COLORED_ENTRIES) {
            val raw = RectF(axisBoundary(col, cell), axisBoundary(row, cell),
                axisBoundary(col + 1, cell), axisBoundary(row + 1, cell))
            val team = COLORED_ROADS[row to col]?.let { theme.teamColor(it) }
            drawSafeCourtyard(canvas, raw, team)
            return
        }
        COLORED_ROADS[row to col]?.let { color ->
            drawStoneRoadCell(canvas, cell, row, col, theme.teamColor(color))
            return
        }
        val raw = RectF(axisBoundary(col, cell), axisBoundary(row, cell),
            axisBoundary(col + 1, cell), axisBoundary(row + 1, cell))
        val unit = min(raw.width(), raw.height())
        // Leave the original grid stroke completely exposed, including its inner half.
        val inset = maxOf(unit * .045f, dp(1.1f))
        val surface = RectF(raw.left + inset, raw.top + inset,
            raw.right - inset, raw.bottom - inset)
        val saved = canvas.save()
        canvas.clipRect(surface)
        // Square texture coordinates preserve pebble proportions on the stretched lanes.
        pebbleMatrix.setScale(unit / 112f, unit / 112f)
        pebbleMatrix.postTranslate(raw.left + col * unit * .731f,
            raw.top + row * unit * .619f)
        pebbleShader.setLocalMatrix(pebbleMatrix)
        canvas.drawRect(surface, pebblePaint)
        fillPaint.shader = LinearGradient(surface.left, surface.top,
            surface.right, surface.bottom,
            intArrayOf(0x18FFFFFF, Color.TRANSPARENT, 0x163D3932),
            floatArrayOf(0f, .56f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(surface, fillPaint)
        fillPaint.shader = null
        canvas.restoreToCount(saved)

    }

    /** Open timber rest stop: low seats at the sides, clear approaches and center. */
    private fun drawSafeCourtyard(canvas: Canvas, raw: RectF, team: Int?) {
        val unit = min(raw.width(), raw.height())
        val gap = maxOf(dp(1.1f), unit * .045f)
        val area = RectF(raw.left + gap, raw.top + gap, raw.right - gap, raw.bottom - gap)
        val saved = canvas.save()
        canvas.clipRect(area)
        fillPaint.shader = null
        fillPaint.color = 0xFF634126.toInt()
        canvas.drawRect(area, fillPaint)
        val floor = RectF(area.left + unit * .035f, area.top + unit * .035f,
            area.right - unit * .035f, area.bottom - unit * .07f)
        fillPaint.shader = LinearGradient(floor.left, floor.top, floor.right, floor.bottom,
            intArrayOf(0xFFFFE2A0.toInt(), 0xFFD6A967.toInt(), 0xFFBC874C.toInt()),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(floor, fillPaint)
        fillPaint.shader = null
        val plankHeight = unit * .20f
        strokePaint.strokeWidth = unit * .012f
        var y = floor.top + plankHeight
        while (y < floor.bottom) {
            strokePaint.color = 0x806A431F.toInt()
            canvas.drawLine(floor.left, y, floor.right, y, strokePaint)
            strokePaint.color = 0x80FFECC0.toInt()
            canvas.drawLine(floor.left, y + unit * .017f, floor.right,
                y + unit * .017f, strokePaint)
            y += plankHeight
        }
        // Fine grain follows the boards and remains quieter than the border lines.
        strokePaint.strokeWidth = unit * .006f
        strokePaint.color = 0x405F3A19
        repeat(12) { index ->
            val gy = floor.top + floor.height() * (index + .5f) / 12f
            canvas.drawPath(Path().apply {
                moveTo(floor.left, gy)
                cubicTo(floor.centerX() - unit * .1f, gy - unit * .025f,
                    floor.centerX() + unit * .12f, gy + unit * .025f, floor.right, gy)
            }, strokePaint)
        }
        // Benches flank the travel direction; the two lane approaches stay open.
        val horizontal = raw.height() > raw.width()
        fun bench(rect: RectF) {
            val shadow = RectF(rect)
            shadow.offset(unit * .012f, unit * .045f)
            fillPaint.color = 0x7052381D
            canvas.drawRoundRect(shadow, unit * .035f, unit * .035f, fillPaint)
            // Visible short feet separate a bench from a flat stripe on the deck.
            fillPaint.color = 0xFF603B21.toInt()
            val alongX = rect.width() > rect.height()
            for (fraction in floatArrayOf(.18f, .82f)) {
                val x = if (alongX) rect.left + rect.width() * fraction else rect.centerX()
                val y = if (alongX) rect.centerY() else rect.top + rect.height() * fraction
                canvas.drawRoundRect(RectF(x - unit * .025f, y,
                    x + unit * .025f, y + unit * .10f),
                    unit * .01f, unit * .01f, fillPaint)
            }
            fillPaint.color = 0xFF70441F.toInt()
            canvas.drawRoundRect(rect, unit * .025f, unit * .025f, fillPaint)
            val seat = RectF(rect.left, rect.top, rect.right, rect.bottom - unit * .03f)
            fillPaint.shader = LinearGradient(seat.left, seat.top, seat.right, seat.bottom,
                intArrayOf(0xFFFFDA89.toInt(), 0xFFBB7C34.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(seat, unit * .025f, unit * .025f, fillPaint)
            fillPaint.shader = null
            strokePaint.strokeWidth = unit * .012f
            strokePaint.color = 0xFFFFE4A2.toInt()
            canvas.drawLine(seat.left + unit * .02f, seat.top + unit * .012f,
                seat.right - unit * .02f, seat.top + unit * .012f, strokePaint)
            strokePaint.strokeWidth = unit * .008f
            strokePaint.color = 0x806C421E.toInt()
            if (alongX) {
                canvas.drawLine(seat.left + unit * .02f, seat.centerY(),
                    seat.right - unit * .02f, seat.centerY(), strokePaint)
            } else {
                canvas.drawLine(seat.centerX(), seat.top + unit * .02f,
                    seat.centerX(), seat.bottom - unit * .02f, strokePaint)
            }
        }
        if (horizontal) {
            for (bottom in listOf(false, true)) {
                val top = if (bottom) floor.bottom - unit * .14f else floor.top + unit * .025f
                bench(RectF(floor.left + floor.width() * .20f, top,
                    floor.right - floor.width() * .20f, top + unit * .11f))
            }
        } else {
            for (right in listOf(false, true)) {
                val left = if (right) floor.right - unit * .14f else floor.left + unit * .025f
                bench(RectF(left, floor.top + floor.height() * .20f,
                    left + unit * .11f, floor.bottom - floor.height() * .20f))
            }
        }
        // Colored entry rest stops retain their team identity as a small woven mat.
        val matHalfWidth = unit * if (horizontal) .27f else .23f
        val matHalfHeight = unit * if (horizontal) .23f else .27f
        val mat = RectF(raw.centerX() - matHalfWidth, raw.centerY() - matHalfHeight,
            raw.centerX() + matHalfWidth, raw.centerY() + matHalfHeight)
        val matShadow = RectF(mat)
        matShadow.offset(unit * .012f, unit * .025f)
        fillPaint.color = 0x50432C19
        canvas.drawRoundRect(matShadow, unit * .035f, unit * .035f, fillPaint)
        // Neutral stops use a distinct muted teal rather than looking like green entries.
        val matColor = team ?: 0xFF467E8B.toInt()
        fillPaint.shader = LinearGradient(mat.left, mat.top, mat.right, mat.bottom,
            intArrayOf(mixStone(matColor, Color.WHITE, .12f), darken(matColor, .80f)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(mat, unit * .035f, unit * .035f, fillPaint)
        fillPaint.shader = null
        strokePaint.strokeWidth = unit * .006f
        strokePaint.color = 0x35FFF2CC
        for (thread in 1 until 9) {
            val ty = mat.top + mat.height() * thread / 9f
            canvas.drawLine(mat.left + unit * .025f, ty, mat.right - unit * .025f, ty, strokePaint)
        }
        strokePaint.strokeWidth = unit * .015f
        strokePaint.color = 0xC0FFF0C6.toInt()
        val seam = RectF(mat)
        seam.inset(unit * .035f, unit * .035f)
        canvas.drawRoundRect(seam, unit * .02f, unit * .02f, strokePaint)
        fillPaint.color = Color.WHITE
        canvas.restoreToCount(saved)
    }

    /** Opaque stone treatment only for the four colored lanes and their colored entry cells. */
    private fun drawStoneRoadCell(canvas: Canvas, cell: Float, row: Int, col: Int, team: Int) {
        val raw = RectF(axisBoundary(col, cell), axisBoundary(row, cell),
            axisBoundary(col + 1, cell), axisBoundary(row + 1, cell))
        val unit = min(raw.width(), raw.height())
        val gap = maxOf(unit * .045f, dp(1.1f))
        val depth = unit * .11f
        val body = RectF(raw.left + gap, raw.top + gap + depth * .42f,
            raw.right - gap, raw.bottom - gap)
        val face = RectF(raw.left + gap, raw.top + gap,
            raw.right - gap, raw.bottom - gap - depth)
        val corner = unit * .055f
        val shapeSeed = row * 71L + col * 137L
        // Clean mineral pigment with only a little warm stone tint.
        val hsv = FloatArray(3)
        Color.colorToHSV(team, hsv)
        hsv[1] = (hsv[1] * 1.18f).coerceAtMost(1f)
        hsv[2] = (hsv[2] * 1.09f).coerceAtMost(1f)
        val pigment = mixStone(Color.HSVToColor(hsv), 0xFFF1E9DA.toInt(), .055f)
        val bodyPath = chamferedStone(body, corner, shapeSeed)
        fillPaint.color = Color.WHITE
        fillPaint.shader = LinearGradient(body.left, body.top, body.right, body.bottom,
            intArrayOf(mixStone(pigment, Color.WHITE, .10f), darken(pigment, .70f)),
            null, Shader.TileMode.CLAMP)
        canvas.drawPath(bodyPath, fillPaint)
        fillPaint.shader = null

        // A wide sloping bevel gives the colored stone slab a raised edge.
        fillPaint.shader = LinearGradient(face.left, face.top, face.right, face.bottom,
            intArrayOf(mixStone(pigment, Color.WHITE, .23f), pigment, darken(pigment, .78f)),
            floatArrayOf(0f, .43f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(chamferedStone(face, corner, shapeSeed), fillPaint)
        fillPaint.shader = null
        val bevel = unit * .029f
        val top = RectF(face.left + bevel, face.top + bevel,
            face.right - bevel, face.bottom - bevel)
        val topPath = chamferedStone(top, corner * .67f, shapeSeed)
        fillPaint.shader = LinearGradient(top.left, top.top, top.right, top.bottom,
            intArrayOf(mixStone(pigment, Color.WHITE, .11f), pigment, darken(pigment, .94f)),
            floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(topPath, fillPaint)
        fillPaint.shader = null
        rockReliefMatrix.setScale(unit / 115f, unit / 115f)
        rockReliefMatrix.postTranslate(raw.left + col * unit * .37f,
            raw.top + row * unit * .61f)
        rockReliefShader.setLocalMatrix(rockReliefMatrix)
        canvas.drawPath(topPath, rockReliefPaint)
        canvas.drawPath(topPath, stoneGrainPaint)

    }

    private fun chamferedStone(rect: RectF, corner: Float, seed: Long): Path = Path().apply {
        val random = Random(seed)
        // Small inward chips preserve the cell boundary and break machine-cut symmetry.
        moveTo(rect.left + corner, rect.top)
        lineTo(rect.right - corner, rect.top)
        lineTo(rect.right, rect.top + corner)
        for (index in 1..4) {
            lineTo(rect.right - random.nextFloat() * corner * .60f,
                rect.top + rect.height() * index / 5f)
        }
        lineTo(rect.right, rect.bottom - corner)
        lineTo(rect.right - corner, rect.bottom)
        for (index in 1..4) {
            lineTo(rect.right - rect.width() * index / 5f,
                rect.bottom - random.nextFloat() * corner * .65f)
        }
        lineTo(rect.left + corner, rect.bottom)
        lineTo(rect.left, rect.bottom - corner)
        for (index in 4 downTo 1) {
            lineTo(rect.left + random.nextFloat() * corner * .55f,
                rect.top + rect.height() * index / 5f)
        }
        lineTo(rect.left, rect.top + corner)
        close()
    }

    private fun mixStone(a: Int, b: Int, amount: Float): Int = Color.rgb(
        (Color.red(a) * (1f - amount) + Color.red(b) * amount).toInt(),
        (Color.green(a) * (1f - amount) + Color.green(b) * amount).toInt(),
        (Color.blue(a) * (1f - amount) + Color.blue(b) * amount).toInt(),
    )

    private fun drawCenterDepth(
        canvas: Canvas,
        cell: Float,
    ) {
        val center =
            RectF(
                axisBoundary(6, cell),
                axisBoundary(6, cell),
                axisBoundary(9, cell),
                axisBoundary(9, cell),
            )
        fillPaint.shader =
            LinearGradient(
                center.left,
                center.top,
                center.right,
                center.bottom,
                intArrayOf(
                    0x20FFFFFF,
                    0x05FFFFFF,
                    0x14000000,
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(center, fillPaint)
        fillPaint.shader = null
        drawGrain(canvas, center)

        strokePaint.strokeWidth = dp(1.3f)
        strokePaint.color = 0x8EFFFFFF.toInt()
        canvas.drawLine(
            center.left + dp(1f),
            center.top + dp(1f),
            center.right - dp(1f),
            center.top + dp(1f),
            strokePaint,
        )
        canvas.drawLine(
            center.left + dp(1f),
            center.top + dp(1f),
            center.left + dp(1f),
            center.bottom - dp(1f),
            strokePaint,
        )
    }

    private fun drawGrain(
        canvas: Canvas,
        rect: RectF,
        radius: Float = 0f,
    ) {
        if (radius > 0f) {
            canvas.drawRoundRect(rect, radius, radius, grainPaint)
        } else {
            canvas.drawRect(rect, grainPaint)
        }
    }

    /** Mirrors the released LudoBoardView visual boundaries without changing them. */
    private fun axisBoundary(
        index: Int,
        cell: Float,
    ): Float {
        val yardSpan = cell * 5f
        val roadSpan = cell * 5f
        val yardStep = yardSpan / 6f
        val roadStep = roadSpan / 3f

        return when {
            index <= 6 -> index * yardStep
            index <= 9 -> yardSpan + (index - 6) * roadStep
            else -> yardSpan + roadSpan + (index - 9) * yardStep
        }
    }

    private fun brighten(
        color: Int,
        factor: Float,
    ): Int =
        Color.rgb(
            (Color.red(color) * factor).toInt().coerceIn(0, 255),
            (Color.green(color) * factor).toInt().coerceIn(0, 255),
            (Color.blue(color) * factor).toInt().coerceIn(0, 255),
        )

    private fun darken(
        color: Int,
        factor: Float,
    ): Int =
        Color.rgb(
            (Color.red(color) * factor).toInt().coerceIn(0, 255),
            (Color.green(color) * factor).toInt().coerceIn(0, 255),
            (Color.blue(color) * factor).toInt().coerceIn(0, 255),
        )

    private fun dp(value: Float): Float =
        value * density

    private companion object {
        val RED = Color.rgb(241, 37, 47)
        val GREEN = Color.rgb(0, 169, 80)
        val YELLOW = Color.rgb(255, 216, 27)
        val BLUE = Color.rgb(48, 151, 215)

        // Logical coordinates mirror the released lanes. No white road cell is included.
        val COLORED_ENTRIES = setOf(6 to 1, 1 to 8, 8 to 13, 13 to 6)
        val COLORED_ROADS = buildMap<Pair<Int, Int>, Int> {
            for (index in 1..5) {
                put(7 to index, RED)
                put(index to 7, GREEN)
                put(7 to (14 - index), YELLOW)
                put((14 - index) to 7, BLUE)
            }
            put(6 to 1, RED)
            put(1 to 8, GREEN)
            put(8 to 13, YELLOW)
            put(13 to 6, BLUE)
        }

        val NEUTRAL_SAFE_CELLS = setOf(2 to 6, 6 to 12, 12 to 8, 8 to 2)

        /** Large cached patch of randomly packed gravel, with three stone size ranges. */
        private fun createPebbleBitmap(): Bitmap {
            val size = 512
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFFB9B6AD.toInt())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val random = Random(0x504542424CL)
            val packed = ArrayList<FloatArray>()
            // Place large stones first, then fill the gaps with smaller chips.
            repeat(6000) { attempt ->
                val radius = when {
                    attempt < 700 -> 10f + random.nextFloat() * 12f
                    attempt < 2600 -> 5f + random.nextFloat() * 6f
                    else -> 2.5f + random.nextFloat() * 3.5f
                }
                val x = random.nextFloat() * size
                val y = random.nextFloat() * size
                val overlaps = packed.any { previous ->
                    val dx = kotlin.math.abs(x - previous[0]).let { min(it, size - it) }
                    val dy = kotlin.math.abs(y - previous[1]).let { min(it, size - it) }
                    val separation = (radius + previous[2]) * .88f + 1f
                    dx * dx + dy * dy < separation * separation
                }
                if (!overlaps) packed.add(floatArrayOf(x, y, radius))
            }
            for (stoneData in packed) {
                val radius = stoneData[2]
                val stretch = .64f + random.nextFloat() * .34f
                val rotation = random.nextFloat() * Math.PI * 2.0
                val points = List(9) { index ->
                    val angle = index * Math.PI * 2.0 / 9.0
                    val r = radius * (.78f + random.nextFloat() * .24f)
                    val px = kotlin.math.cos(angle) * r
                    val py = kotlin.math.sin(angle) * r * stretch
                    Pair((px * kotlin.math.cos(rotation) - py * kotlin.math.sin(rotation)).toFloat(),
                        (px * kotlin.math.sin(rotation) + py * kotlin.math.cos(rotation)).toFloat())
                }
                val path = Path().apply {
                    moveTo((points.last().first + points[0].first) * .5f,
                        (points.last().second + points[0].second) * .5f)
                    repeat(points.size) { index ->
                        val a = points[index]
                        val b = points[(index + 1) % points.size]
                        quadTo(a.first, a.second, (a.first + b.first) * .5f,
                            (a.second + b.second) * .5f)
                    }
                    close()
                }
                val tone = 198 + random.nextInt(46)
                val relief = 1.2f + random.nextFloat() * 2.8f
                for (dy in -1..1) for (dx in -1..1) {
                    val saved = canvas.save()
                    canvas.translate(stoneData[0] + dx * size, stoneData[1] + dy * size)
                    paint.shader = null
                    paint.color = 0x38443D32
                    canvas.save()
                    canvas.translate(relief * .65f, relief)
                    canvas.drawPath(path, paint)
                    canvas.restore()
                    paint.shader = RadialGradient(-radius * .32f, -radius * .40f,
                        radius * 1.45f,
                        intArrayOf(Color.rgb(min(255, tone + 28), min(255, tone + 27),
                            min(255, tone + 22)), Color.rgb(tone, tone, tone - 5),
                            Color.rgb(tone - 64, tone - 65, tone - 67)),
                        floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
                    canvas.drawPath(path, paint)
                    // Uneven mineral faces break the smooth oval/button appearance.
                    canvas.clipPath(path)
                    paint.shader = null
                    paint.color = 0x153C3830
                    canvas.drawPath(Path().apply {
                        moveTo(-radius, radius * .1f)
                        lineTo(radius * .1f, -radius * .16f)
                        lineTo(radius * .5f, radius)
                        lineTo(-radius, radius)
                        close()
                    }, paint)
                    canvas.restoreToCount(saved)
                }
            }
            paint.shader = null
            repeat(14000) {
                paint.color = if (random.nextBoolean()) 0x24FFFFFF else 0x203E3A34
                canvas.drawCircle(random.nextFloat() * size, random.nextFloat() * size,
                    .18f + random.nextFloat() * .52f, paint)
            }
            return bitmap
        }

        /** Directionally lit mineral relief, transparent so every team's pigment remains visible. */
        private fun createRockReliefBitmap(): Bitmap {
            val size = 256
            val heights = FloatArray(size * size)
            val random = Random(0x524F434BL)
            // Periodic waves at several scales form a rough split-stone height field.
            repeat(28) { octave ->
                val frequency = if (octave < 12) 2 + random.nextInt(8) else 10 + random.nextInt(28)
                val fx = frequency
                val fy = 1 + random.nextInt(frequency)
                val phase = random.nextDouble() * Math.PI * 2.0
                val amplitude = if (octave < 12) 1.8f else .25f
                for (y in 0 until size) for (x in 0 until size) {
                    heights[y * size + x] += kotlin.math.sin(
                        (x * fx + y * fy) * Math.PI * 2.0 / size + phase,
                    ).toFloat() * amplitude
                }
            }
            val pixels = IntArray(size * size)
            for (y in 0 until size) for (x in 0 until size) {
                val h = heights[y * size + x]
                val slopeX = heights[y * size + (x + 1) % size] - h
                val slopeY = heights[((y + 1) % size) * size + x] - h
                val light = (-slopeX * .55f - slopeY * .75f) * 28f + h * 1.8f
                val alpha = kotlin.math.abs(light).toInt().coerceIn(0, 62)
                pixels[y * size + x] = if (light >= 0f) Color.argb(alpha, 255, 250, 236)
                    else Color.argb(alpha, 45, 39, 31)
            }
            return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }

        private fun createStoneGrainBitmap(): Bitmap {
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
            val random = Random(0x53544F4EL)
            repeat(1_100) {
                val tone = if (random.nextBoolean()) 255 else 38
                paint.color = Color.argb(6 + random.nextInt(15), tone, tone, tone)
                val x = random.nextFloat() * 128f
                val y = random.nextFloat() * 128f
                val radius = .16f + random.nextFloat() * .58f
                canvas.drawOval(x - radius, y - radius * .6f, x + radius, y + radius * .6f, paint)
            }
            // Faint short mineral veins, not dark cracks or painted stepping markers.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = .45f
            repeat(16) { index ->
                paint.color = if (index % 2 == 0) 0x0DFFFFFF else 0x0A3D3830
                val x = random.nextFloat() * 128f
                val y = random.nextFloat() * 128f
                canvas.drawPath(Path().apply {
                    moveTo(x, y)
                    rQuadTo(3f, -1.8f, 7f, -.8f)
                    rQuadTo(2f, 1.1f, 5f, -.9f)
                }, paint)
            }
            return bitmap
        }

        private fun createGrainBitmap(): Bitmap {
            val size = 128
            val bitmap =
                Bitmap.createBitmap(
                    size,
                    size,
                    Bitmap.Config.ARGB_8888,
                )
            val textureCanvas = Canvas(bitmap)
            val random = Random(0x4C55444FL)
            val speckPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                }

            // Sparse low-alpha flecks: enough to interrupt flat color without
            // making the board look dirty or changing its recognizable palette.
            repeat(620) {
                val tone = if (random.nextBoolean()) 0 else 255
                val alpha = 7 + random.nextInt(14)
                speckPaint.color = Color.argb(alpha, tone, tone, tone)
                val x = random.nextFloat() * size
                val y = random.nextFloat() * size
                val radius = if (random.nextInt(7) == 0) 0.85f else 0.38f
                textureCanvas.drawCircle(x, y, radius, speckPaint)
            }

            // Short, irregular fibers give a fine paper/graphed-board grain,
            // not a repeated fabric pattern or large speckled stone texture.
            repeat(48) {
                val tone = if (random.nextBoolean()) 0 else 255
                val alpha = 6 + random.nextInt(10)
                speckPaint.color = Color.argb(alpha, tone, tone, tone)
                speckPaint.strokeWidth = 0.45f + random.nextFloat() * 0.45f
                val x = random.nextFloat() * size
                val y = random.nextFloat() * size
                val length = 2f + random.nextFloat() * 6f
                val drift = (random.nextFloat() - 0.5f) * 1.4f
                textureCanvas.drawLine(x, y, x + length, y + drift, speckPaint)
            }

            return bitmap
        }
    }
}
