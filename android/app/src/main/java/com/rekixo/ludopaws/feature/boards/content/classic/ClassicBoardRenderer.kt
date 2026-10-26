package com.rekixo.ludopaws.feature.boards.content.classic

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import com.rekixo.ludopaws.LudoPawsBoardPalette
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
                    texture(0),
                    Shader.TileMode.REPEAT,
                    Shader.TileMode.REPEAT,
                )
        }


    private val stoneGrainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(texture(1), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private var appliedTextures: Array<Bitmap>? = null
    override fun prepare(onReady: () -> Unit) = prepareTextures(onReady)

    private fun applyPreparedTextures() {
        val ready = preparedTextures ?: return
        if (appliedTextures === ready) return
        appliedTextures = ready
        grainPaint.shader = BitmapShader(ready[0], Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        stoneGrainPaint.shader = BitmapShader(ready[1], Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    override fun draw(canvas: Canvas, size: Float, density: Float, definition: BoardDefinition) {
        applyPreparedTextures()
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
        fillPaint.color = mixStone(LudoPawsBoardPalette.joint, LudoPawsBoardPalette.stoneShade, .35f)
        canvas.drawRect(cell * 5f, 0f, cell * 10f, cell * 5f, fillPaint)
        canvas.drawRect(cell * 5f, cell * 10f, cell * 10f, cell * 15f, fillPaint)
        canvas.drawRect(0f, cell * 5f, cell * 5f, cell * 10f, fillPaint)
        canvas.drawRect(cell * 10f, cell * 5f, cell * 15f, cell * 10f, fillPaint)
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
        val color = ClassicRoadProfile.team(row, col)?.let(theme::teamColor) ?: theme.neutralCellColor
        drawStoneRoadCell(canvas, cell, row, col, color)
        val raw = RectF(axisBoundary(col, cell), axisBoundary(row, cell),
            axisBoundary(col + 1, cell), axisBoundary(row + 1, cell))
        if ((row to col) in NEUTRAL_SAFE_CELLS) drawSafeShield(canvas, raw, cell)
    }

    /** Shield and checkmark identify neutral safe cells without covering their stone surface. */
    private fun drawSafeShield(canvas: Canvas, raw: RectF, cell: Float) {
        val cx = raw.centerX()
        val cy = raw.centerY()
        val r = cell * .28f
        val shield = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r * .83f, cy - r * .65f)
            lineTo(cx + r * .75f, cy + r * .12f)
            quadTo(cx + r * .58f, cy + r * .70f, cx, cy + r)
            quadTo(cx - r * .58f, cy + r * .70f, cx - r * .75f, cy + r * .12f)
            lineTo(cx - r * .83f, cy - r * .65f)
            close()
        }
        val saved = canvas.save()
        canvas.clipRect(raw.left + dp(1.1f), raw.top + dp(1.1f),
            raw.right - dp(1.1f), raw.bottom - dp(1.1f))
        canvas.save()
        canvas.translate(0f, cell * .025f)
        fillPaint.color = 0x50314952
        canvas.drawPath(shield, fillPaint)
        canvas.restore()
        fillPaint.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r,
            intArrayOf(0xFF47979D.toInt(), LudoPawsBoardPalette.safe),
            null, Shader.TileMode.CLAMP)
        canvas.drawPath(shield, fillPaint)
        fillPaint.shader = null
        strokePaint.strokeWidth = cell * .025f
        strokePaint.color = LudoPawsBoardPalette.stoneLight
        canvas.drawPath(shield, strokePaint)
        strokePaint.strokeWidth = cell * .045f
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.color = Color.WHITE
        canvas.drawPath(Path().apply {
            moveTo(cx - r * .36f, cy)
            lineTo(cx - r * .06f, cy + r * .30f)
            lineTo(cx + r * .40f, cy - r * .28f)
        }, strokePaint)
        strokePaint.strokeCap = Paint.Cap.BUTT
        fillPaint.color = Color.WHITE
        canvas.restoreToCount(saved)
    }

    private fun drawStoneRoadCell(canvas: Canvas, cell: Float, row: Int, col: Int, team: Int) {
        val raw = RectF(axisBoundary(col, cell), axisBoundary(row, cell),
            axisBoundary(col + 1, cell), axisBoundary(row + 1, cell))
        val unit = min(raw.width(), raw.height())
        val depth = unit * ClassicRoadProfile.DEPTH
        val normalized = ClassicRoadProfile.face(ClassicRoadProfile.raw(row, col))
        val scale = cell / 10f
        val face = RectF(normalized.left * scale, normalized.top * scale,
            normalized.right * scale, normalized.bottom * scale)
        val body = RectF(face).apply { offset(0f, depth) }
        val corner = unit * ClassicRoadProfile.CORNER
        val pigment = team
        val bodyPath = Path().apply { addRoundRect(body, corner, corner, Path.Direction.CW) }
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
        canvas.drawRoundRect(face, corner, corner, fillPaint)
        fillPaint.shader = null
        val bevel = unit * ClassicRoadProfile.BEVEL
        val top = RectF(face.left + bevel, face.top + bevel,
            face.right - bevel, face.bottom - bevel)
        val topPath = Path().apply { addRoundRect(top, corner - bevel, corner - bevel, Path.Direction.CW) }
        fillPaint.shader = LinearGradient(top.left, top.top, top.right, top.bottom,
            intArrayOf(mixStone(pigment, Color.WHITE, .11f), pigment, darken(pigment, .94f)),
            floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(topPath, fillPaint)
        fillPaint.shader = null
        // Fine mineral grain only; no diagonal or wavy bands across the walking face.
        canvas.drawPath(topPath, stoneGrainPaint)

        // Restore the original team-colored entry marker beside each starting yard.
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
            fillPaint.color = LudoPawsBoardPalette.stoneLight
            canvas.drawPath(star, fillPaint)
            fillPaint.color = Color.WHITE
        }
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
        @Volatile private var preparedTextures: Array<Bitmap>? = null
        private val neutralTexture = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        private val waiting = mutableListOf<() -> Unit>()
        private var preparing = false
        private val textureWorker = java.util.concurrent.Executors.newSingleThreadExecutor { work ->
            Thread(work, "LudoPawsRoadTextures").apply { isDaemon = true }
        }
        private fun texture(index: Int): Bitmap = preparedTextures?.get(index) ?: neutralTexture
        private fun prepareTextures(onReady: () -> Unit) {
            synchronized(waiting) {
                if (preparedTextures != null) { onReady(); return }
                waiting += onReady
                if (preparing) return
                preparing = true
            }
            textureWorker.execute {
                val result = runCatching {
                    ClassicRoadScene.prepare()
                    arrayOf(createGrainBitmap(), createStoneGrainBitmap())
                }
                val callbacks = synchronized(waiting) {
                    result.onSuccess { preparedTextures = it }
                    preparing = false
                    waiting.toList().also { waiting.clear() }
                }
                result.onFailure { android.util.Log.e("RoadTextures", "Texture preparation failed", it) }
                callbacks.forEach { it() }
            }
        }

        val RED = LudoPawsBoardPalette.coral
        val GREEN = LudoPawsBoardPalette.jade
        val YELLOW = LudoPawsBoardPalette.honey
        val BLUE = LudoPawsBoardPalette.sky

        // Logical coordinates mirror the released lanes. No white road cell is included.
        val COLORED_ENTRIES = setOf(6 to 1, 1 to 8, 8 to 13, 13 to 6)

        val NEUTRAL_SAFE_CELLS = setOf(2 to 6, 6 to 12, 12 to 8, 8 to 2)


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
