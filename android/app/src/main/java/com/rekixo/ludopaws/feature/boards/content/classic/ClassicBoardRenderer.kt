package com.rekixo.ludopaws.feature.boards.content.classic

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
        pebbleMatrix.setScale(unit / 128f, unit / 128f)
        pebbleMatrix.postTranslate(raw.left + (col % 3) * unit * .21f,
            raw.top + (row % 3) * unit * .17f)
        pebbleShader.setLocalMatrix(pebbleMatrix)
        canvas.drawRect(surface, pebblePaint)
        fillPaint.shader = LinearGradient(surface.left, surface.top,
            surface.right, surface.bottom,
            intArrayOf(0x18FFFFFF, Color.TRANSPARENT, 0x163D3932),
            floatArrayOf(0f, .56f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(surface, fillPaint)
        fillPaint.shader = null
        canvas.restoreToCount(saved)

        // Opaque gravel must not hide the four neutral safe-cell markers underneath.
        if ((row to col) in NEUTRAL_SAFE_CELLS) {
            val star = Path()
            repeat(10) { index ->
                val angle = Math.toRadians(index * 36.0 - 90.0)
                val radius = cell * .27f * if (index % 2 == 0) 1f else .45f
                val x = raw.centerX() + kotlin.math.cos(angle).toFloat() * radius
                val y = raw.centerY() + kotlin.math.sin(angle).toFloat() * radius
                if (index == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            fillPaint.color = Color.rgb(45, 138, 204)
            canvas.drawPath(star, fillPaint)
            fillPaint.color = Color.WHITE
        }
    }

    /** Opaque stone treatment only for the four colored lanes and their colored entry cells. */
    private fun drawStoneRoadCell(canvas: Canvas, cell: Float, row: Int, col: Int, team: Int) {
        val raw = RectF(axisBoundary(col, cell), axisBoundary(row, cell),
            axisBoundary(col + 1, cell), axisBoundary(row + 1, cell))
        val unit = min(raw.width(), raw.height())
        val gap = unit * .038f
        val depth = unit * .11f
        val body = RectF(raw.left + gap, raw.top + gap + depth * .42f,
            raw.right - gap, raw.bottom - gap)
        val face = RectF(raw.left + gap, raw.top + gap,
            raw.right - gap, raw.bottom - gap - depth)
        val corner = unit * .105f
        // Muted mineral pigment keeps each team's hue, with a light natural-stone face.
        val pigment = mixStone(team, 0xFFF1E9DA.toInt(), .34f)
        val bodyPath = chamferedStone(body, corner)
        fillPaint.color = Color.WHITE
        fillPaint.shader = LinearGradient(body.left, body.top, body.right, body.bottom,
            intArrayOf(mixStone(pigment, Color.WHITE, .10f), darken(pigment, .70f)),
            null, Shader.TileMode.CLAMP)
        canvas.drawPath(bodyPath, fillPaint)
        fillPaint.shader = null

        // A wide sloping bevel gives the colored stone slab a raised edge.
        fillPaint.shader = LinearGradient(face.left, face.top, face.right, face.bottom,
            intArrayOf(mixStone(pigment, Color.WHITE, .40f), pigment, darken(pigment, .78f)),
            floatArrayOf(0f, .43f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(chamferedStone(face, corner), fillPaint)
        fillPaint.shader = null
        val bevel = unit * .068f
        val top = RectF(face.left + bevel, face.top + bevel,
            face.right - bevel, face.bottom - bevel)
        val topPath = chamferedStone(top, corner * .67f)
        fillPaint.shader = LinearGradient(top.left, top.top, top.right, top.bottom,
            intArrayOf(mixStone(pigment, Color.WHITE, .17f), pigment, darken(pigment, .94f)),
            floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(topPath, fillPaint)
        fillPaint.shader = null
        canvas.drawPath(topPath, stoneGrainPaint)

        // The opaque stone face must preserve the existing colored safe-entry star.
        if ((row to col) in COLORED_ENTRIES) {
            val star = Path()
            repeat(10) { index ->
                val angle = Math.toRadians(index * 36.0 - 90.0)
                val radius = cell * .27f * if (index % 2 == 0) 1f else .43f
                val x = raw.centerX() + kotlin.math.cos(angle).toFloat() * radius
                val y = raw.centerY() + kotlin.math.sin(angle).toFloat() * radius
                if (index == 0) star.moveTo(x, y) else star.lineTo(x, y)
            }
            star.close()
            fillPaint.color = team
            canvas.drawPath(star, fillPaint)
            fillPaint.color = Color.WHITE
        }
    }

    private fun chamferedStone(rect: RectF, corner: Float): Path = Path().apply {
        moveTo(rect.left + corner, rect.top)
        lineTo(rect.right - corner, rect.top)
        lineTo(rect.right, rect.top + corner)
        lineTo(rect.right, rect.bottom - corner)
        lineTo(rect.right - corner, rect.bottom)
        lineTo(rect.left + corner, rect.bottom)
        lineTo(rect.left, rect.bottom - corner)
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

        /** Cached seamless gravel relief; generated once, with no per-frame stone geometry. */
        private fun createPebbleBitmap(): Bitmap {
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFFCDCBC5.toInt())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val random = Random(0x504542424CL)
            // Staggered, jittered small stones with irregular softly rounded silhouettes.
            for (row in 0 until 7) {
                for (col in 0 until 7) {
                    val spacing = 128f / 7f
                    val cx = (col + .5f + (row % 2) * .5f) * spacing
                    val cy = (row + .5f) * spacing
                    val x = cx + (random.nextFloat() - .5f) * 4f
                    val y = cy + (random.nextFloat() - .5f) * 4f
                    val rx = 7.5f + random.nextFloat() * 2.5f
                    val ry = 6.5f + random.nextFloat() * 2.5f
                    val points = List(8) { index ->
                        val angle = index * Math.PI / 4.0
                        val variation = .86f + random.nextFloat() * .22f
                        Pair(kotlin.math.cos(angle).toFloat() * rx * variation,
                            kotlin.math.sin(angle).toFloat() * ry * variation)
                    }
                    val stone = Path().apply {
                        moveTo((points[7].first + points[0].first) * .5f,
                            (points[7].second + points[0].second) * .5f)
                        repeat(8) { index ->
                            val current = points[index]
                            val next = points[(index + 1) % 8]
                            quadTo(current.first, current.second,
                                (current.first + next.first) * .5f,
                                (current.second + next.second) * .5f)
                        }
                        close()
                    }
                    val tone = 218 + random.nextInt(24)
                    // Wrap edge stones so repeating the bitmap never creates a seam.
                    for (dy in -1..1) for (dx in -1..1) {
                        val saved = canvas.save()
                        canvas.translate(x + dx * 128f, y + dy * 128f)
                        paint.shader = null
                        paint.color = 0x264A463E
                        canvas.save()
                        canvas.translate(.9f, 1.7f)
                        canvas.drawPath(stone, paint)
                        canvas.restore()
                        paint.shader = LinearGradient(-rx, -ry, rx, ry,
                            intArrayOf(0xFFFAF9F4.toInt(), Color.rgb(tone, tone, tone - 4),
                                Color.rgb(tone - 43, tone - 44, tone - 47)),
                            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
                        canvas.drawPath(stone, paint)
                        // A smaller pale face leaves the lower bevel visible.
                        canvas.scale(.79f, .76f)
                        canvas.translate(-.5f, -.7f)
                        paint.shader = LinearGradient(-rx, -ry, rx, ry,
                            intArrayOf(Color.rgb(min(255, tone + 14), min(255, tone + 14),
                                min(255, tone + 10)), Color.rgb(tone, tone, tone - 3)),
                            null, Shader.TileMode.CLAMP)
                        canvas.drawPath(stone, paint)
                        canvas.restoreToCount(saved)
                    }
                }
            }
            paint.shader = null
            repeat(900) {
                val light = random.nextBoolean()
                paint.color = if (light) 0x25FFFFFF else 0x133E3A34
                canvas.drawCircle(random.nextFloat() * 128f, random.nextFloat() * 128f,
                    .15f + random.nextFloat() * .32f, paint)
            }
            return bitmap
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
