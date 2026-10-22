package com.rekixo.ludopaws.feature.dice.content.classic

import android.graphics.*
import com.rekixo.ludopaws.feature.dice.domain.DicePalette
import com.rekixo.ludopaws.feature.dice.rendering.*

/** Per-view paint/gradient cache, shared by all palette-based dice skins. */
internal class ClassicDiceRenderer(private val density: Float) : DiceRenderer {
    private val facePaint =
        Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33000000
            setShadowLayer(dp(3f), 0f, dp(1.5f), 0x26000000)
        }
    private var cachedFaceShader: Shader? = null
    private var cachedFaceShaderSize = 0f
    private var cachedFaceShaderColors = intArrayOf()
    private val borderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2.4f)
            color = 0xFF8A8A8A.toInt()
        }
    private val pipPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF3A3A3A.toInt()
            style = Paint.Style.FILL
            setShadowLayer(
                dp(1.2f),
                0f,
                dp(.8f),
                0x26000000,
            )
        }
    private val rollingPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(5f)
            color = 0xFFB8B8B8.toInt()
            setShadowLayer(
                dp(4f),
                0f,
                0f,
                0x446B7280,
            )
        }
    // Attention strokes inherit the owning player's team color. The glow is
    // drawn inside the dice bounds, so the pulse stays unclipped while breathing.
    private val attentionPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2.8f)
            color = 0xFF8A8A8A.toInt()
            strokeJoin = Paint.Join.ROUND
        }
    private val attentionGlowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(5f)
            color = 0xFF8A8A8A.toInt()
            strokeJoin = Paint.Join.ROUND
        }


    private val shadowRect = RectF()
    private val faceRect = RectF()

    override fun draw(canvas: Canvas, size: Float, palette: DicePalette, frame: DiceRenderFrame): RectF {
        shadowRect.set(
            size * .09f,
            size * .11f,
            size * .91f,
            size * .92f,
        )
        val shadow = shadowRect
        facePaint.shader = null
        facePaint.color =
            0x33000000
        facePaint.setShadowLayer(
            dp(3f),
            0f,
            dp(1.5f),
            0x26000000,
        )
        canvas.drawRoundRect(
            shadow,
            size * .16f,
            size * .16f,
            shadowPaint,
        )

        val inset =
            size * .07f
        faceRect.set(
            inset,
            inset,
            size - inset,
            size - inset,
        )
        val rect = faceRect

        facePaint.alpha = 255
        if (
            cachedFaceShader == null ||
            cachedFaceShaderSize != size ||
            !cachedFaceShaderColors.contentEquals(palette.faceColors)
        ) {
            cachedFaceShader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                palette.faceColors,
                null,
                Shader.TileMode.CLAMP,
            )
            cachedFaceShaderSize = size
            cachedFaceShaderColors = palette.faceColors.copyOf()
        }
        facePaint.shader = cachedFaceShader
        canvas.drawRoundRect(
            rect,
            size * .17f,
            size * .17f,
            facePaint,
        )
        facePaint.shader = null
        facePaint.color =
            0x42FFFFFF
        canvas.drawRoundRect(
            RectF(
                rect.left + size * .035f,
                rect.top + size * .035f,
                rect.right - size * .035f,
                rect.top + rect.height() * .43f,
            ),
            size * .13f,
            size * .13f,
            facePaint,
        )

        borderPaint.color =
            palette.borderColor
        pipPaint.color =
            palette.pipColor
        rollingPaint.color =
            palette.borderColor
        val attentionColor =
            palette.borderColor
        attentionPaint.color = attentionColor
        attentionGlowPaint.color = attentionColor
        rollingPaint.alpha =
            (145f + frame.borderPulse.coerceIn(0f, 1f) * 110f)
                .toInt()
        if (frame.attentionPulsing) {
            // Keep the soft team-colored halo on the inside of the same rounded outline.
            // An external shadow would be clipped by this View's own drawing bounds.
            val glowInset = dp(2.6f)
            val glowRect =
                RectF(
                    rect.left + glowInset,
                    rect.top + glowInset,
                    rect.right - glowInset,
                    rect.bottom - glowInset,
                )
            attentionGlowPaint.alpha =
                (34f + frame.borderPulse.coerceIn(0f, 1f) * 42f)
                    .toInt()
            canvas.drawRoundRect(
                glowRect,
                (size * .17f - glowInset).coerceAtLeast(0f),
                (size * .17f - glowInset).coerceAtLeast(0f),
                attentionGlowPaint,
            )
            attentionPaint.alpha =
                (190f + frame.borderPulse.coerceIn(0f, 1f) * 65f)
                    .toInt()
        }
        val outlinePaint =
            when {
                frame.rolling || frame.settling -> rollingPaint
                frame.attentionPulsing -> attentionPaint
                else -> borderPaint
            }
        if (frame.hasTurnDeadline && !frame.rolling && !frame.settling) {
            outlinePaint.alpha = minOf(outlinePaint.alpha, 72)
        } else if (outlinePaint === borderPaint) {
            borderPaint.alpha = 255
        }
        canvas.drawRoundRect(
            rect,
            size * .17f,
            size * .17f,
            outlinePaint,
        )

        val left = size * .31f
        val pipCenter = size * .50f
        val right = size * .69f
        val top = size * .31f
        val middle = size * .50f
        val bottom = size * .69f
        val pipRadius =
            size * .055f

        fun pip(
            x: Float,
            y: Float,
        ) {
            canvas.drawCircle(
                x,
                y,
                pipRadius,
                pipPaint,
            )
        }

        when (frame.face) {
            1 -> pip(pipCenter, middle)
            2 -> {
                pip(left, top)
                pip(right, bottom)
            }
            3 -> {
                pip(left, top)
                pip(pipCenter, middle)
                pip(right, bottom)
            }
            4 -> {
                pip(left, top)
                pip(right, top)
                pip(left, bottom)
                pip(right, bottom)
            }
            5 -> {
                pip(left, top)
                pip(right, top)
                pip(pipCenter, middle)
                pip(left, bottom)
                pip(right, bottom)
            }
            6 -> {
                pip(left, top)
                pip(right, top)
                pip(left, middle)
                pip(right, middle)
                pip(left, bottom)
                pip(right, bottom)
            }
        }


        return faceRect
    }

    private fun dp(value: Float): Float = value * density
}
