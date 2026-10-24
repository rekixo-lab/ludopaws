package com.rekixo.ludopaws.core.ui.art

import com.rekixo.ludopaws.LudoPawsBoardPalette
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Reusable code-drawn plywood texture for cards, panels and buttons across the app.
 * Grain, knots, shading and the continuous 3D timber rim are painted at runtime;
 * no bitmap/image asset is used.
 */
internal class PlywoodTextureDrawable(
    private val density: Float,
    private val cornerRadiusDp: Float = 15f,
    private val rimThicknessDp: Float = 5.5f,
    private val gameplayFinish: Boolean = false,
) : Drawable() {
    // Multiplying RGB by ~0.7 keeps the wood finish about 10 percentage points
    // lighter than the previous 40%-darkened test, while preserving its warm tone.
    private val plywoodShade = android.graphics.LightingColorFilter(
        if (gameplayFinish) Color.WHITE else 0xFFB3B3B3.toInt(),
        Color.TRANSPARENT,
    )
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = plywoodShade
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        colorFilter = plywoodShade
    }
    // Separate un-tinted relief paint lets highlights stay warm while the wood is shaded.
    private val reliefPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val lightSweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var drawableAlpha = 255

    override fun draw(canvas: Canvas) {
        val b = RectF(bounds)
        if (b.width() <= 0f || b.height() <= 0f) return

        val radius = cornerRadiusDp * density
        val outerPath = Path().apply {
            addRoundRect(b, radius, radius, Path.Direction.CW)
        }
        val layer = canvas.saveLayerAlpha(b, drawableAlpha)
        canvas.save()
        canvas.clipPath(outerPath)

        // Reference plywood is fairly evenly lit, with only a soft warm shift.
        // Avoid the darker band through the middle/lower part of the card.
        fill.shader = LinearGradient(
            b.left,
            b.top,
            b.left,
            b.bottom,
            if (gameplayFinish) intArrayOf(
                LudoPawsBoardPalette.mapleLight, LudoPawsBoardPalette.maple,
                LudoPawsBoardPalette.mapleShade, LudoPawsBoardPalette.maple,
            ) else intArrayOf(
                0xFFF0D0A1.toInt(),
                0xFFECC796.toInt(),
                0xFFECC796.toInt(),
                0xFFEFD0A0.toInt(),
            ),
            floatArrayOf(0f, 0.28f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(b, fill)
        fill.shader = null

        // Soft diagonal light gives the veneer a shallow, satin-like 3D sheen
        // without creating a hard stripe or a dark band through the center.
        lightSweepPaint.shader = LinearGradient(
            b.left,
            b.top,
            b.right,
            b.bottom,
            intArrayOf(
                0x25FFFFFF,
                0x00FFFFFF,
                0x0D6B3B1D,
            ),
            floatArrayOf(0f, 0.56f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(b, lightSweepPaint)
        lightSweepPaint.shader = null

        if (gameplayFinish) {
            // Narrow paired grain edges catch the same upper-left light as the board.
            val saved = canvas.save()
            val spacing = b.width() / 24f
            for (index in 1 until 24) {
                val x = b.left + spacing * index
                reliefPaint.strokeWidth = density * .65f
                reliefPaint.color = 0x404F2F17
                canvas.drawLine(x, b.top, x, b.bottom, reliefPaint)
                reliefPaint.color = 0x60FFE1A1
                canvas.drawLine(x + density, b.top, x + density, b.bottom, reliefPaint)
            }
            lightSweepPaint.shader = LinearGradient(b.left, b.top, b.right, b.bottom,
                intArrayOf(0x28FFE8AF, Color.TRANSPARENT, 0x1843210C),
                floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(b, lightSweepPaint)
            lightSweepPaint.shader = null
            canvas.restoreToCount(saved)
        }

        // Fine, restrained plywood grain: lower opacity keeps it from reading as stripes.
        // Irregular paths vary from one another, like natural veneer texture.
        val grainCount = 25
        for (index in 0 until grainCount) {
            val fraction = (index + 0.35f + (index % 4) * 0.11f) / grainCount
            val gx = b.left + b.width() * fraction
            val wave = density * (1.8f + (index % 5) * 0.8f)
            val driftA = ((index * 13) % 17 - 8) * density
            val driftB = ((index * 7) % 13 - 6) * density
            val grain = Path().apply {
                moveTo(gx + driftA * 0.15f, b.top)
                cubicTo(
                    gx - wave,
                    b.top + b.height() * 0.18f,
                    gx + wave * 0.55f + driftA,
                    b.top + b.height() * 0.34f,
                    gx + driftB,
                    b.top + b.height() * 0.51f,
                )
                cubicTo(
                    gx - wave * 0.7f + driftB,
                    b.top + b.height() * 0.70f,
                    gx + wave * 0.8f + driftA * 0.25f,
                    b.top + b.height() * 0.84f,
                    gx + driftA * 0.1f,
                    b.bottom,
                )
            }
            line.color = when (index % 4) {
                0 -> Color.argb(16, 112, 66, 32)
                1 -> Color.argb(12, 255, 241, 205)
                2 -> Color.argb(14, 128, 76, 35)
                else -> Color.argb(10, 255, 240, 205)
            }
            line.strokeWidth = (if (index % 5 == 0) 1.05f else 0.6f) * density
            canvas.drawPath(grain, line)
        }

        // Broader, softly wandering veneer grain gives the surface more plywood character.
        // These are low-contrast grain bands, not plank edges or hard seams.
        for (index in 0 until 17) {
            val fraction = ((index * 37) % 101) / 100f
            val gx = b.left + b.width() * fraction
            val wave = density * (5f + (index % 5) * 2.4f)
            val drift = density * (((index * 11) % 19) - 9).toFloat()
            val grain = Path().apply {
                moveTo(gx + drift * 0.12f, b.top)
                cubicTo(
                    gx - wave * 0.35f,
                    b.top + b.height() * 0.19f,
                    gx + wave * 0.70f + drift,
                    b.top + b.height() * 0.40f,
                    gx - wave * 0.15f,
                    b.top + b.height() * 0.58f,
                )
                cubicTo(
                    gx - wave * 0.55f + drift * 0.2f,
                    b.top + b.height() * 0.73f,
                    gx + wave * 0.45f,
                    b.top + b.height() * 0.88f,
                    gx + drift * 0.08f,
                    b.bottom,
                )
            }
            line.color = when (index % 5) {
                0 -> Color.argb(36, 116, 69, 36)
                1 -> Color.argb(28, 255, 238, 194)
                2 -> Color.argb(25, 132, 79, 39)
                3 -> Color.argb(22, 255, 235, 190)
                else -> Color.argb(30, 146, 91, 47)
            }
            line.strokeWidth = when (index % 6) {
                0 -> 1.7f * density
                1 -> 1.2f * density
                else -> 0.85f * density
            }
            // The dark fibre edge plus a tiny offset warm highlight gives each grain
            // a tactile raised/recessed feel, rather than a flat painted line.
            canvas.drawPath(grain, line)
            reliefPaint.color = Color.argb(34, 255, 239, 204)
            reliefPaint.strokeWidth = (line.strokeWidth * 0.68f).coerceAtLeast(0.45f * density)
            canvas.save()
            canvas.translate(0.9f * density, 0f)
            canvas.drawPath(grain, reliefPaint)
            canvas.restore()

            // A faint companion line creates the fine fibre detail found in veneer.
            if (index % 3 == 0) {
                line.color = Color.argb(22, 255, 239, 204)
                line.strokeWidth = 0.55f * density
                canvas.drawLine(
                    gx + 2.8f * density,
                    b.top + b.height() * 0.08f,
                    gx + 3.6f * density,
                    b.bottom - b.height() * 0.06f,
                    line,
                )
            }
        }

        // Small low-contrast natural pores/knots; avoid repeating seams or equal board widths.

        for (index in 0 until 8) {
            val yOffset = b.height() * (0.08f + (index * 0.113f))
            val gx = b.left + b.width() * (((index * 19) % 83) / 100f + 0.04f)
            val streak = Path().apply {
                moveTo(gx, b.top + yOffset)
                cubicTo(
                    gx + density * (4f + index % 3),
                    b.top + yOffset + density * 2f,
                    gx - density * (5f + index % 4),
                    b.top + yOffset + density * 4f,
                    gx + density * 2f,
                    b.top + yOffset + density * 6f,
                )
            }
            line.color = Color.argb(8, 119, 71, 36)
            line.strokeWidth = 0.55f * density
            canvas.drawPath(streak, line)
        }

        // Tiny low-contrast wood knots, spaced irregularly and without plank seams.
        if (b.width() > 150f * density && b.height() > 70f * density) {
            val knots = listOf(
                (b.left + b.width() * 0.24f) to (b.top + b.height() * 0.31f),
                (b.left + b.width() * 0.73f) to (b.top + b.height() * 0.72f),
            )
            knots.forEachIndexed { index, (knotX, knotY) ->
                line.color = Color.argb(11, 119, 65, 32)
                line.strokeWidth = 0.55f * density
                canvas.drawOval(
                    RectF(
                        knotX - 2.4f * density,
                        knotY - 4.8f * density,
                        knotX + 2.4f * density,
                        knotY + 4.8f * density,
                    ),
                    line,
                )
                line.color = Color.argb(10, 255, 232, 181)
                line.strokeWidth = 0.45f * density
                canvas.drawOval(
                    RectF(
                        knotX - 1.1f * density,
                        knotY - 2.4f * density,
                        knotX + 1.1f * density,
                        knotY + 2.4f * density,
                    ),
                    line,
                )
            }
        }

        canvas.restore()

        // Continuous 3D timber rim. Each bevel is a filled rounded-rect ring
        // with a full-perimeter gradient, so corners do not show colour breaks.
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val outerRim = RectF(b).apply { inset(0.6f * density, 0.6f * density) }
        val bronzeRim = RectF(b).apply { inset(1.5f * density, 1.5f * density) }
        val rimInset = rimThicknessDp * density
        val innerRim = RectF(b).apply { inset(rimInset, rimInset) }
        val glintRim = RectF(b).apply { inset(2.2f * density, 2.2f * density) }
        val innerGlintCutout = RectF(b).apply { inset(3.2f * density, 3.2f * density) }

        // Deep outside edge supplies thickness under the brighter bevel.
        rimPaint.shader = LinearGradient(
            b.left,
            b.top,
            b.left,
            b.bottom,
            if (gameplayFinish) intArrayOf(
                LudoPawsBoardPalette.maple, LudoPawsBoardPalette.wood,
                LudoPawsBoardPalette.woodShade, 0xFF896342.toInt(),
            ) else intArrayOf(
                0xFFB77A3D.toInt(),
                0xFF875027.toInt(),
                0xFF573018.toInt(),
                0xFF70401F.toInt(),
            ),
            floatArrayOf(0f, 0.36f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
        drawRoundedRing(
            canvas,
            outerRim,
            (radius - 0.6f * density).coerceAtLeast(1f),
            innerRim,
            (radius - rimInset).coerceAtLeast(1f),
            rimPaint,
        )

        // Warm bevel gradient runs continuously around the curved corners.
        rimPaint.shader = LinearGradient(
            bronzeRim.left,
            bronzeRim.top,
            bronzeRim.right,
            bronzeRim.bottom,
            if (gameplayFinish) intArrayOf(
                0xFFF4E3BB.toInt(), LudoPawsBoardPalette.gold,
                LudoPawsBoardPalette.maple, LudoPawsBoardPalette.woodShade,
            ) else intArrayOf(
                0xFFFFE7B0.toInt(),
                0xFFE9B96F.toInt(),
                0xFFC18649.toInt(),
                0xFF8A512C.toInt(),
            ),
            floatArrayOf(0f, 0.38f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
        drawRoundedRing(
            canvas,
            bronzeRim,
            (radius - 1.5f * density).coerceAtLeast(1f),
            innerRim,
            (radius - rimInset).coerceAtLeast(1f),
            rimPaint,
        )

        // Very narrow inner glint is also a complete ring, not separate top/side strokes.
        rimPaint.shader = LinearGradient(
            glintRim.left,
            glintRim.top,
            glintRim.left,
            glintRim.bottom,
            intArrayOf(
                0xFFFFF0CC.toInt(),
                0x88F6D18B.toInt(),
                0x55925A2C.toInt(),
                0x88F4CF89.toInt(),
            ),
            floatArrayOf(0f, 0.35f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
        drawRoundedRing(
            canvas,
            glintRim,
            (radius - 2.2f * density).coerceAtLeast(1f),
            innerGlintCutout,
            (radius - 3.2f * density).coerceAtLeast(1f),
            rimPaint,
        )
        rimPaint.shader = null

        canvas.restoreToCount(layer)
    }

    private fun drawRoundedRing(
        canvas: Canvas,
        outer: RectF,
        outerRadius: Float,
        inner: RectF,
        innerRadius: Float,
        paint: Paint,
    ) {
        val ringPath = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRoundRect(outer, outerRadius, outerRadius, Path.Direction.CW)
            addRoundRect(inner, innerRadius, innerRadius, Path.Direction.CW)
        }
        canvas.drawPath(ringPath, paint)
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Keep the requested plywood darkening and optionally compose a caller tint.
        fill.colorFilter = colorFilter ?: plywoodShade
        line.colorFilter = colorFilter ?: plywoodShade
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
