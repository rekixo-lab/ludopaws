package com.rekixo.ludopaws.feature.dice.content.environment

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

internal enum class EnvironmentDiceTheme(
    val key: String, val light: Int, val base: Int, val dark: Int,
    val rim: Int, val pip: Int, val accent: Int,
) {
    FOREST("forest_dew", 0xFFE8F8B8.toInt(), 0xFFB7DC83.toInt(), 0xFF43895D.toInt(),
        0xFFE7BD70.toInt(), 0xFF164B37.toInt(), 0xFF397A45.toInt()),
    LAGOON("lagoon_pearl", 0xFFE0FFF9.toInt(), 0xFF91E3E1.toInt(), 0xFF227FA4.toInt(),
        0xFFFFD59B.toInt(), 0xFF104C6F.toInt(), 0xFF278DA5.toInt()),
    FROST("frost_crystal", 0xFFFFFFFF.toInt(), 0xFFD7EDFF.toInt(), 0xFF779EDE.toInt(),
        0xFFB9EAFE.toInt(), 0xFF294C92.toInt(), 0xFF739ECD.toInt()),
    STARLIGHT("starlight", 0xFF67619E.toInt(), 0xFF3D396F.toInt(), 0xFF211E49.toInt(),
        0xFFEBC976.toInt(), 0xFFFFE8A6.toInt(), 0xFFB9AFE7.toInt()),
}

internal class ForestDiceRenderer : EnvironmentDiceRenderer(EnvironmentDiceTheme.FOREST)
internal class LagoonDiceRenderer : EnvironmentDiceRenderer(EnvironmentDiceTheme.LAGOON)
internal class FrostDiceRenderer : EnvironmentDiceRenderer(EnvironmentDiceTheme.FROST)
internal class StarlightDiceRenderer : EnvironmentDiceRenderer(EnvironmentDiceTheme.STARLIGHT)

/** Cached, resolution-independent artwork. Only the resolved face supplied by DiceView is drawn. */
internal open class EnvironmentDiceRenderer(private val theme: EnvironmentDiceTheme) : DiceRenderer {
    private val front = RectF(10f, 25f, 79f, 92f)
    private val inset = RectF(13f, 28f, 76f, 89f)
    private val outline = RectF(4.5f, 4.5f, 95.5f, 95.5f)
    private val controlBounds = RectF()
    private val shadowBounds = RectF(8f, 85f, 96f, 99f)
    private val frontPath = Path().apply { addRoundRect(front, 13f, 13f, Path.Direction.CW) }
    private val top = Path().apply {
        moveTo(12f, 29f); lineTo(26f, 12f); quadTo(29f, 9f, 34f, 9f)
        lineTo(83f, 12f); quadTo(90f, 12f, 88f, 19f); lineTo(77f, 32f); close()
    }
    private val side = Path().apply {
        moveTo(75f, 29f); lineTo(88f, 17f); quadTo(92f, 14f, 92f, 22f)
        lineTo(92f, 75f); quadTo(92f, 82f, 87f, 86f); lineTo(74f, 94f); close()
    }
    private val leaf = Path().apply {
        moveTo(0f, 0f); cubicTo(-6f, -7f, -4f, -13f, 2f, -15f)
        cubicTo(8f, -9f, 7f, -3f, 0f, 0f); close()
    }
    private val wave = Path().apply {
        moveTo(16f, 83f); cubicTo(28f, 76f, 36f, 90f, 49f, 83f)
        cubicTo(59f, 77f, 65f, 87f, 74f, 82f)
    }
    private val facet = Path().apply {
        moveTo(11f, 43f); lineTo(22f, 28f); lineTo(16f, 68f); close()
        moveTo(62f, 88f); lineTo(76f, 65f); lineTo(74f, 86f); close()
    }
    private val crescent = Path().apply {
        moveTo(60f, 14f); cubicTo(48f, 13f, 45f, 24f, 58f, 26f)
        cubicTo(49f, 20f, 57f, 16f, 60f, 14f); close()
    }
    private val diamond = Path().apply {
        moveTo(0f, -4.6f); lineTo(4.6f, 0f); lineTo(0f, 4.6f); lineTo(-4.6f, 0f); close()
    }
    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun gradient(colors: IntArray, x1: Float = 12f, y1: Float = 25f, x2: Float = 79f, y2: Float = 92f) =
        LinearGradient(x1, y1, x2, y2, colors, null, Shader.TileMode.CLAMP)
    private val frontPaint = paint().apply {
        shader = gradient(intArrayOf(theme.light, theme.base, theme.dark))
    }
    private val topPaint = paint().apply {
        shader = gradient(intArrayOf(theme.light, theme.base), 26f, 9f, 70f, 32f)
    }
    private val sidePaint = paint().apply {
        shader = gradient(intArrayOf(theme.base, theme.dark), 76f, 24f, 93f, 76f)
    }
    private val shadowPaint = paint().apply {
        shader = RadialGradient(50f, 92f, 45f, intArrayOf(0x440E2035, 0x000E2035), null, Shader.TileMode.CLAMP)
    }
    private val rimPaint = paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 2.4f; strokeJoin = Paint.Join.ROUND
        shader = gradient(intArrayOf(theme.rim, theme.light, theme.rim, theme.dark))
    }
    private val bevelPaint = paint().apply {
        color = 0x77FFFFFF; style = Paint.Style.STROKE; strokeWidth = .75f
    }
    private val glossPaint = paint().apply {
        shader = gradient(intArrayOf(0x55FFFFFF, 0x00FFFFFF), 15f, 27f, 35f, 54f)
    }
    private val detailPaint = paint().apply {
        color = theme.accent; strokeWidth = 1.25f; strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shinePaint = paint().apply { color = 0xBFFFFFFF.toInt(); strokeWidth = .8f }
    private val pipRimPaint = paint().apply { color = theme.rim }
    private val pipPaint = paint().apply {
        shader = RadialGradient(-1f, -1.4f, 5f, intArrayOf(theme.pip, theme.pip, theme.dark), null, Shader.TileMode.CLAMP)
    }
    private val teamPaint = paint().apply { style = Paint.Style.STROKE; strokeWidth = 1.2f }
    private val positions = arrayOf(
        floatArrayOf(), floatArrayOf(0f, 0f),
        floatArrayOf(-17f, -17f, 17f, 17f),
        floatArrayOf(-17f, -17f, 0f, 0f, 17f, 17f),
        floatArrayOf(-17f, -17f, 17f, -17f, -17f, 17f, 17f, 17f),
        floatArrayOf(-17f, -17f, 17f, -17f, 0f, 0f, -17f, 17f, 17f, 17f),
        floatArrayOf(-17f, -17f, 17f, -17f, -17f, 0f, 17f, 0f, -17f, 17f, 17f, 17f),
    )

    override fun draw(canvas: Canvas, size: Float, palette: DicePalette, frame: DiceRenderFrame): RectF {
        val saved = canvas.save()
        canvas.scale(size / 100f, size / 100f)
        canvas.drawOval(shadowBounds, shadowPaint)
        canvas.drawPath(top, topPaint); canvas.drawPath(side, sidePaint)
        canvas.drawPath(top, rimPaint); canvas.drawPath(side, rimPaint)
        canvas.drawRoundRect(front, 13f, 13f, frontPaint)
        val clipped = canvas.save()
        canvas.clipPath(frontPath)
        canvas.drawRoundRect(14f, 28f, 75f, 53f, 10f, 10f, glossPaint)
        drawFrontDecoration(canvas)
        canvas.restoreToCount(clipped)
        canvas.drawRoundRect(front, 13f, 13f, rimPaint)
        canvas.drawRoundRect(inset, 10f, 10f, bevelPaint)
        drawCrownDecoration(canvas)

        val pips = positions.getOrNull(frame.face)
        if (pips != null) {
            var index = 0
            while (index < pips.size) {
                drawPip(canvas, 44.5f + pips[index], 58f + pips[index + 1])
                index += 2
            }
        }
        // Team identity and turn/deadline feedback follow the same frame as existing skins.
        teamPaint.color = palette.borderColor
        val active = frame.rolling || frame.settling || frame.attentionPulsing
        teamPaint.strokeWidth = if (active) 2.4f else 1.2f
        teamPaint.alpha = when {
            frame.hasTurnDeadline && !frame.rolling && !frame.settling -> 72
            active -> (145f + frame.borderPulse.coerceIn(0f, 1f) * 110f).toInt()
            else -> 180
        }
        canvas.drawRoundRect(outline, 17f, 17f, teamPaint)
        canvas.restoreToCount(saved)
        controlBounds.set(size * .045f, size * .045f, size * .955f, size * .955f)
        return controlBounds
    }

    private fun drawFrontDecoration(canvas: Canvas) {
        detailPaint.style = Paint.Style.STROKE
        detailPaint.alpha = 160
        when (theme) {
            EnvironmentDiceTheme.FOREST -> {
                canvas.drawLine(16f, 38f, 16f, 75f, detailPaint)
                canvas.drawLine(16f, 63f, 20f, 57f, detailPaint)
                drawLeaf(canvas, 17f, 48f, .45f, -25f)
                drawLeaf(canvas, 73f, 85f, .5f, 25f)
            }
            EnvironmentDiceTheme.LAGOON -> {
                canvas.drawPath(wave, detailPaint)
                canvas.drawCircle(18f, 63f, 2.4f, detailPaint)
                canvas.drawCircle(19f, 53f, 1.5f, detailPaint)
                canvas.drawCircle(71f, 34f, 1.7f, detailPaint)
            }
            EnvironmentDiceTheme.FROST -> {
                detailPaint.style = Paint.Style.FILL; detailPaint.alpha = 75
                canvas.drawPath(facet, detailPaint)
                detailPaint.style = Paint.Style.STROKE; detailPaint.alpha = 200
                snowflake(canvas, 19f, 34f, 3.2f)
                snowflake(canvas, 69f, 83f, 3.2f)
            }
            EnvironmentDiceTheme.STARLIGHT -> {
                star(canvas, 18f, 37f, 2.8f)
                star(canvas, 70f, 81f, 2.8f)
                canvas.drawLine(19f, 82f, 31f, 85f, detailPaint)
                canvas.drawLine(31f, 85f, 47f, 82f, detailPaint)
                canvas.drawCircle(19f, 82f, 1f, shinePaint)
                canvas.drawCircle(31f, 85f, 1f, shinePaint)
                canvas.drawCircle(47f, 82f, 1f, shinePaint)
            }
        }
    }

    private fun drawCrownDecoration(canvas: Canvas) {
        detailPaint.alpha = 220; detailPaint.style = Paint.Style.STROKE
        when (theme) {
            EnvironmentDiceTheme.FOREST -> {
                drawLeaf(canvas, 48f, 25f, .65f, 60f)
                canvas.drawCircle(64f, 20f, 2f, shinePaint)
            }
            EnvironmentDiceTheme.LAGOON -> {
                canvas.drawArc(40f, 14f, 66f, 26f, 195f, 150f, false, detailPaint)
                canvas.drawArc(44f, 17f, 62f, 25f, 195f, 150f, false, detailPaint)
                canvas.drawCircle(53f, 23f, 1.6f, shinePaint)
            }
            EnvironmentDiceTheme.FROST -> snowflake(canvas, 54f, 20f, 6f)
            EnvironmentDiceTheme.STARLIGHT -> {
                detailPaint.style = Paint.Style.FILL; detailPaint.color = theme.rim
                canvas.drawPath(crescent, detailPaint)
                detailPaint.style = Paint.Style.STROKE; detailPaint.color = theme.accent
                star(canvas, 70f, 21f, 2.5f)
            }
        }
    }

    private fun drawLeaf(canvas: Canvas, x: Float, y: Float, scale: Float, rotation: Float) {
        val saved = canvas.save()
        canvas.translate(x, y); canvas.rotate(rotation); canvas.scale(scale, scale)
        detailPaint.style = Paint.Style.FILL
        canvas.drawPath(leaf, detailPaint)
        canvas.drawLine(0f, -1f, 2f, -11f, shinePaint)
        detailPaint.style = Paint.Style.STROKE
        canvas.restoreToCount(saved)
    }

    private fun snowflake(canvas: Canvas, x: Float, y: Float, radius: Float) {
        val saved = canvas.save()
        canvas.translate(x, y)
        repeat(6) {
            canvas.drawLine(0f, 0f, 0f, -radius, detailPaint)
            canvas.drawLine(0f, -radius * .6f, -radius * .25f, -radius * .8f, detailPaint)
            canvas.drawLine(0f, -radius * .6f, radius * .25f, -radius * .8f, detailPaint)
            canvas.rotate(60f)
        }
        canvas.restoreToCount(saved)
    }

    private fun star(canvas: Canvas, x: Float, y: Float, radius: Float) {
        canvas.drawLine(x - radius, y, x + radius, y, detailPaint)
        canvas.drawLine(x, y - radius, x, y + radius, detailPaint)
    }

    private fun drawPip(canvas: Canvas, x: Float, y: Float) {
        val saved = canvas.save()
        canvas.translate(x, y)
        if (theme == EnvironmentDiceTheme.FROST) {
            canvas.drawPath(diamond, pipRimPaint)
            canvas.scale(.78f, .78f)
            canvas.drawPath(diamond, pipPaint)
        } else {
            canvas.drawCircle(0f, 0f, 4.7f, pipRimPaint)
            canvas.drawCircle(0f, 0f, 3.6f, pipPaint)
        }
        canvas.drawCircle(-1f, -1.3f, .75f, shinePaint)
        canvas.restoreToCount(saved)
    }
}
