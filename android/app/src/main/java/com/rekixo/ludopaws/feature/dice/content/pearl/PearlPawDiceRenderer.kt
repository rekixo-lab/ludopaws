package com.rekixo.ludopaws.feature.dice.content.pearl

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.rekixo.ludopaws.feature.dice.domain.DicePalette
import com.rekixo.ludopaws.feature.dice.rendering.DiceRenderFrame
import com.rekixo.ludopaws.feature.dice.rendering.DiceRenderer

internal class PearlPawDiceRenderer : DiceRenderer {
    private val face = RectF(10f, 25f, 79f, 92f)
    private val innerFace = RectF(12.6f, 27.6f, 76.4f, 89.4f)
    private val controlBounds = RectF()
    private val unitBounds = RectF(5f, 5f, 95f, 95f)
    private val shadowBounds = RectF(9f, 86f, 95f, 99f)
    private val reflectionBounds = RectF(17f, 29f, 73f, 51f)
    private val toeBounds = RectF()
    private val frontPath = Path().apply { addRoundRect(face, 14f, 14f, Path.Direction.CW) }
    private val topPath = Path().apply {
        moveTo(12f, 29f)
        quadTo(10f, 26f, 14f, 23f)
        lineTo(27f, 11f)
        quadTo(30f, 8f, 36f, 9f)
        lineTo(85f, 13f)
        quadTo(92f, 14f, 88f, 20f)
        lineTo(77f, 32f)
        close()
    }
    private val sidePath = Path().apply {
        moveTo(73f, 28f)
        lineTo(86f, 17f)
        quadTo(92f, 12f, 92f, 22f)
        lineTo(92f, 76f)
        quadTo(92f, 82f, 87f, 86f)
        lineTo(74f, 94f)
        close()
    }
    private val pawPath = Path().apply {
        moveTo(-4f, -1f)
        cubicTo(-8f, 1f, -14f, 7f, -12f, 12f)
        cubicTo(-10f, 18f, -4f, 12f, 0f, 13f)
        cubicTo(5f, 13f, 11f, 19f, 14f, 12f)
        cubicTo(17f, 6f, 9f, 1f, 6f, -2f)
        cubicTo(3f, -5f, -1f, -5f, -4f, -1f)
        close()
    }
    private val glintPath = Path().apply {
        moveTo(14f, 48f)
        cubicTo(13f, 34f, 19f, 29f, 29f, 29f)
    }
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(12f, 27f, 76f, 91f,
            intArrayOf(0xFFFFFDF2.toInt(), 0xFFFFF6EF.toInt(), 0xFFFDE7EE.toInt(), 0xFFEADDF3.toInt()),
            floatArrayOf(0f, .42f, .75f, 1f), Shader.TileMode.CLAMP)
    }
    private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(30f, 9f, 65f, 31f,
            intArrayOf(0xFFFFFDEB.toInt(), 0xFFFDF2F3.toInt(), 0xFFE7EAF7.toInt()), null, Shader.TileMode.CLAMP)
    }
    private val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(77f, 22f, 94f, 89f,
            intArrayOf(0xFFF5E8F0.toInt(), 0xFFDACCE8.toInt(), 0xFFC5B2CB.toInt()), null, Shader.TileMode.CLAMP)
    }
    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.2f
        strokeJoin = Paint.Join.ROUND
        shader = LinearGradient(16f, 8f, 86f, 95f,
            intArrayOf(0xFFFFF5CC.toInt(), 0xFFF7D793.toInt(), 0xFFC18B45.toInt(),
                0xFFFFEAB0.toInt(), 0xFFB88042.toInt()),
            floatArrayOf(0f, .25f, .5f, .72f, 1f), Shader.TileMode.CLAMP)
    }
    private val underRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB58656.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4.6f
        strokeJoin = Paint.Join.ROUND
    }
    private val innerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF9DE.toInt()
        style = Paint.Style.STROKE
        strokeWidth = .75f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blushGlow = RadialGradient(19f, 69f, 35f,
        intArrayOf(0x52F3A8C8, 0x00F3A8C8), null, Shader.TileMode.CLAMP)
    private val mintGlow = RadialGradient(62f, 39f, 22f,
        intArrayOf(0x4DCEF6E9, 0x00CEF6E9), null, Shader.TileMode.CLAMP)
    private val pearlGlow = RadialGradient(54f, 78f, 29f,
        intArrayOf(0x59E9D2FA, 0x00E9D2FA), null, Shader.TileMode.CLAMP)
    private val reflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(30f, 29f, 42f, 52f,
            intArrayOf(0xBFFFFFFF.toInt(), 0x08FFFFFF), null, Shader.TileMode.CLAMP)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(50f, 92f, 46f,
            intArrayOf(0x400D0B23, 0x000D0B23), null, Shader.TileMode.CLAMP)
    }
    private val pipRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-4f, -4f, 4f, 4f,
            intArrayOf(0xFFFFE6AA.toInt(), 0xFFAC703E.toInt(), 0xFFF3CB80.toInt()), null, Shader.TileMode.CLAMP)
    }
    private val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(-.8f, -1.2f, 4.5f,
            intArrayOf(0xFFF9B5CB.toInt(), 0xFFE389AA.toInt(), 0xFFAF4A76.toInt()), null, Shader.TileMode.CLAMP)
    }
    private val pawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(-4f, -8f, 28f,
            intArrayOf(0xFFFCC4D2.toInt(), 0xFFE38BAE.toInt(), 0xFFB85483.toInt()), null, Shader.TileMode.CLAMP)
    }
    private val pawRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        color = 0xFFD4A15D.toInt()
        strokeJoin = Paint.Join.ROUND
    }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xBFFFFFFF.toInt()
        strokeWidth = .8f
        strokeCap = Paint.Cap.ROUND
    }
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xBFFFFFFF.toInt()
        strokeWidth = 1.2f
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }
    private val attentionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val pipPositions = arrayOf(
        floatArrayOf(), floatArrayOf(),
        floatArrayOf(-14f, -14f, 14f, 14f),
        floatArrayOf(-14f, -14f, 0f, 0f, 14f, 14f),
        floatArrayOf(-14f, -14f, 14f, -14f, -14f, 14f, 14f, 14f),
        floatArrayOf(-14f, -14f, 14f, -14f, 0f, 0f, -14f, 14f, 14f, 14f),
        floatArrayOf(-14f, -14f, 14f, -14f, -14f, 0f, 14f, 0f, -14f, 14f, 14f, 14f),
    )
    private val toePositions = floatArrayOf(-13f, -7f, -5f, -14f, 5f, -14f, 14f, -6f)

    override fun draw(canvas: Canvas, size: Float, palette: DicePalette, frame: DiceRenderFrame): RectF {
        val saved = canvas.save()
        canvas.scale(size / 100f, size / 100f)
        canvas.drawOval(shadowBounds, shadowPaint)
        canvas.drawPath(topPath, topPaint)
        canvas.drawPath(sidePath, sidePaint)
        canvas.drawPath(topPath, underRimPaint)
        canvas.drawPath(sidePath, underRimPaint)
        canvas.drawPath(topPath, goldPaint)
        canvas.drawPath(sidePath, goldPaint)
        canvas.drawRoundRect(face, 14f, 14f, facePaint)
        val clipped = canvas.save()
        canvas.clipPath(frontPath)
        glowPaint.shader = blushGlow
        canvas.drawRect(face, glowPaint)
        glowPaint.shader = mintGlow
        canvas.drawRect(face, glowPaint)
        glowPaint.shader = pearlGlow
        canvas.drawRect(face, glowPaint)
        canvas.drawOval(reflectionBounds, reflectionPaint)
        canvas.restoreToCount(clipped)
        canvas.drawRoundRect(face, 14f, 14f, underRimPaint)
        canvas.drawRoundRect(face, 14f, 14f, goldPaint)
        canvas.drawRoundRect(innerFace, 11.4f, 11.4f, innerRimPaint)
        canvas.drawPath(glintPath, glintPaint)
        if (frame.face == 1) drawPaw(canvas) else {
            val positions = pipPositions.getOrNull(frame.face)
            if (positions != null) {
                var position = 0
                while (position < positions.size) {
                    drawPip(canvas, 44.5f + positions[position], 58.5f + positions[position + 1])
                    position += 2
                }
            }
        }
        canvas.drawLine(29f, 10f, 29f, 14f, shinePaint)
        canvas.drawLine(27f, 12f, 31f, 12f, shinePaint)
        if (frame.rolling || frame.settling || frame.attentionPulsing) {
            attentionPaint.color = palette.borderColor
            attentionPaint.alpha = if (frame.hasTurnDeadline && !frame.rolling && !frame.settling) 72 else
                (110 + frame.borderPulse.coerceIn(0f, 1f) * 145).toInt()
            attentionPaint.strokeWidth = if (frame.rolling || frame.settling) 2.4f else 1.7f
            canvas.drawRoundRect(unitBounds, 17f, 17f, attentionPaint)
        }
        canvas.restoreToCount(saved)
        controlBounds.set(size * .05f, size * .05f, size * .95f, size * .95f)
        return controlBounds
    }

    private fun drawPip(canvas: Canvas, centerX: Float, centerY: Float) {
        val saved = canvas.save()
        canvas.translate(centerX, centerY)
        canvas.drawCircle(0f, 0f, 4.1f, pipRimPaint)
        canvas.drawCircle(0f, 0f, 3.1f, pipPaint)
        canvas.drawCircle(-.8f, -1.2f, .85f, shinePaint)
        canvas.restoreToCount(saved)
    }

    private fun drawPaw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.translate(44.5f, 58f)
        canvas.drawPath(pawPath, pawRimPaint)
        canvas.drawPath(pawPath, pawPaint)
        var position = 0
        while (position < toePositions.size) {
            val centerX = toePositions[position]
            val centerY = toePositions[position + 1]
            toeBounds.set(centerX - 4f, centerY - 5.6f, centerX + 4f, centerY + 5.6f)
            canvas.drawOval(toeBounds, pawRimPaint)
            canvas.drawOval(toeBounds, pawPaint)
            canvas.drawLine(centerX - 1f, centerY - 3f, centerX + 1f, centerY - 3.5f, shinePaint)
            position += 2
        }
        canvas.drawLine(-6f, 6f, -2f, 2.5f, shinePaint)
        canvas.restoreToCount(saved)
    }
}
