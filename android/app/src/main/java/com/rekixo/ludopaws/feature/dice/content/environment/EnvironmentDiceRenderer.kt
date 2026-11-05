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
    FOREST("forest_dew", 0xFFFFE8B3.toInt(), 0xFFD9A568.toInt(), 0xFF805037.toInt(),
        0xFF476844.toInt(), 0xFF422A1D.toInt(), 0xFF59834B.toInt()),
    LAGOON("lagoon_pearl", 0xFFF0FFFD.toInt(), 0xFF9FE5DD.toInt(), 0xFF2B829C.toInt(),
        0xFFFFD9AF.toInt(), 0xFF175B76.toInt(), 0xFF4DADB6.toInt()),
    FROST("frost_crystal", 0xFFFFFFFF.toInt(), 0xFFD7EDFF.toInt(), 0xFF779EDE.toInt(),
        0xFFB9EAFE.toInt(), 0xFF294C92.toInt(), 0xFF739ECD.toInt()),
    STARLIGHT("starlight", 0xFF595384.toInt(), 0xFF30294F.toInt(), 0xFF14172F.toInt(),
        0xFFEBC976.toInt(), 0xFFFFE8A6.toInt(), 0xFFB9AFE7.toInt()),
}

private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
private fun stroke(color: Int, width: Float) = fill(color).apply {
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}
private fun gradient(vararg colors: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    shader = LinearGradient(15f, 12f, 83f, 92f, colors, null, Shader.TileMode.CLAMP)
}

/** Shared only: resolved pip layout and existing team/turn feedback. Each body is independent. */
internal abstract class EnvironmentDiceRenderer : DiceRenderer {
    private val outline = RectF(4.5f, 4.5f, 95.5f, 95.5f)
    private val shadowBounds = RectF(9f, 84f, 94f, 98f)
    private val controlBounds = RectF()
    private val teamPaint = stroke(0, 1.2f)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(50f, 91f, 44f, intArrayOf(0x440E2035, 0x000E2035), null, Shader.TileMode.CLAMP)
    }
    private val positions = arrayOf(
        floatArrayOf(), floatArrayOf(0f, 0f),
        floatArrayOf(-17f, -17f, 17f, 17f),
        floatArrayOf(-17f, -17f, 0f, 0f, 17f, 17f),
        floatArrayOf(-17f, -17f, 17f, -17f, -17f, 17f, 17f, 17f),
        floatArrayOf(-17f, -17f, 17f, -17f, 0f, 0f, -17f, 17f, 17f, 17f),
        floatArrayOf(-17f, -17f, 17f, -17f, -17f, 0f, 17f, 0f, -17f, 17f, 17f, 17f),
    )

    protected abstract fun drawBody(canvas: Canvas)
    protected abstract fun drawPip(canvas: Canvas, x: Float, y: Float)

    final override fun draw(canvas: Canvas, size: Float, palette: DicePalette, frame: DiceRenderFrame): RectF {
        val saved = canvas.save()
        canvas.scale(size / 100f, size / 100f)
        canvas.drawOval(shadowBounds, shadow)
        drawBody(canvas)
        val pips = positions.getOrNull(frame.face)
        if (pips != null) {
            var index = 0
            while (index < pips.size) {
                drawPip(canvas, 50f + pips[index], 53f + pips[index + 1])
                index += 2
            }
        }
        teamPaint.color = palette.borderColor
        val active = frame.rolling || frame.settling || frame.attentionPulsing
        teamPaint.strokeWidth = if (active) 2.4f else 1.2f
        teamPaint.alpha = when {
            frame.hasTurnDeadline && !frame.rolling && !frame.settling -> 72
            active -> (145f + frame.borderPulse.coerceIn(0f, 1f) * 110f).toInt()
            else -> 150
        }
        canvas.drawRoundRect(outline, 17f, 17f, teamPaint)
        canvas.restoreToCount(saved)
        controlBounds.set(size * .045f, size * .045f, size * .955f, size * .955f)
        return controlBounds
    }
}

/** A chunky, front-facing wooden block with bark edges, grain and a leaf sprig. */
internal class ForestDiceRenderer : EnvironmentDiceRenderer() {
    private val body = RectF(11f, 15f, 89f, 90f)
    private val face = RectF(15f, 16f, 85f, 83f)
    private val wood = gradient(0xFFFFE8B3.toInt(), 0xFFE4B87B.toInt(), 0xFFBB804C.toInt())
    private val bark = fill(0xFF805037.toInt())
    private val edge = stroke(0xFF64402B.toInt(), 2f)
    private val grainPaint = stroke(0x557D4C2B, .9f)
    private val grain = Path().apply {
        moveTo(18f, 26f); cubicTo(33f, 20f, 52f, 29f, 82f, 23f)
        moveTo(17f, 45f); cubicTo(36f, 40f, 55f, 49f, 83f, 43f)
        moveTo(17f, 62f); cubicTo(32f, 67f, 60f, 59f, 82f, 63f)
        moveTo(20f, 77f); cubicTo(40f, 73f, 65f, 81f, 80f, 76f)
    }
    private val leaf = Path().apply {
        moveTo(19f, 26f); cubicTo(12f, 19f, 16f, 10f, 27f, 11f)
        cubicTo(28f, 19f, 25f, 23f, 19f, 26f)
        moveTo(21f, 25f); cubicTo(23f, 13f, 33f, 13f, 36f, 18f)
        cubicTo(32f, 26f, 26f, 27f, 21f, 25f)
    }
    private val foliage = gradient(0xFF9BCB68.toInt(), 0xFF3C693A.toInt())
    private val vein = stroke(0xFFDBEAB2.toInt(), .8f)
    private val pipLip = fill(0xFFF8D7A1.toInt())
    private val pip = fill(0xFF422A1D.toInt())
    private val pipInner = fill(0xFF65432B.toInt())

    override fun drawBody(canvas: Canvas) {
        canvas.drawRoundRect(body, 9f, 9f, bark)
        canvas.drawRoundRect(face, 8f, 8f, wood)
        canvas.drawPath(grain, grainPaint)
        canvas.drawRoundRect(face, 8f, 8f, edge)
        canvas.drawLine(24f, 87f, 76f, 87f, grainPaint)
        canvas.drawPath(leaf, foliage)
        canvas.drawLine(19f, 26f, 24f, 15f, vein)
        canvas.drawLine(21f, 25f, 32f, 19f, vein)
    }

    override fun drawPip(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y + .8f, 5.1f, pipLip)
        canvas.drawCircle(x, y, 4.4f, pip)
        canvas.drawCircle(x + .7f, y + .8f, 2.6f, pipInner)
    }
}

/** An organic scalloped shell. Ribs stay at the perimeter, leaving its pearl pip field clear. */
internal class LagoonDiceRenderer : EnvironmentDiceRenderer() {
    private val shell = Path().apply {
        moveTo(50f, 13f)
        cubicTo(60f, 5f, 70f, 11f, 72f, 17f)
        cubicTo(85f, 9f, 94f, 23f, 88f, 34f)
        cubicTo(99f, 42f, 95f, 56f, 88f, 59f)
        cubicTo(94f, 71f, 85f, 84f, 76f, 82f)
        quadTo(65f, 93f, 50f, 91f)
        quadTo(35f, 93f, 24f, 82f)
        cubicTo(15f, 84f, 6f, 71f, 12f, 59f)
        cubicTo(5f, 56f, 1f, 42f, 12f, 34f)
        cubicTo(6f, 23f, 15f, 9f, 28f, 17f)
        cubicTo(30f, 11f, 40f, 5f, 50f, 13f); close()
    }
    private val ribs = Path().apply {
        moveTo(50f, 15f); lineTo(50f, 23f)
        moveTo(31f, 18f); quadTo(35f, 20f, 38f, 25f)
        moveTo(69f, 18f); quadTo(65f, 20f, 62f, 25f)
        moveTo(16f, 29f); quadTo(20f, 31f, 23f, 35f)
        moveTo(84f, 29f); quadTo(80f, 31f, 77f, 35f)
        moveTo(12f, 46f); lineTo(22f, 48f)
        moveTo(88f, 46f); lineTo(78f, 48f)
        moveTo(16f, 67f); quadTo(20f, 66f, 23f, 63f)
        moveTo(84f, 67f); quadTo(80f, 66f, 77f, 63f)
    }
    private val shellFill = gradient(0xFFF0FFFD.toInt(), 0xFFB4EADF.toInt(), 0xFF46A7BA.toInt())
    private val lip = stroke(0xFFFFD9AF.toInt(), 3.4f)
    private val ribPaint = stroke(0xAA318B9C.toInt(), 1.6f)
    private val field = RectF(24f, 26f, 76f, 79f)
    private val fieldPaint = gradient(0xBFFFFFFF.toInt(), 0x10FFFFFF)
    private val pipRim = fill(0xFF175B76.toInt())
    private val pearl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(-1.2f, -1.5f, 5f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFD7F4F2.toInt(), 0xFF65B2C5.toInt()), null, Shader.TileMode.CLAMP)
    }
    private val shine = fill(0xEFFFFFFF.toInt())

    override fun drawBody(canvas: Canvas) {
        canvas.drawPath(shell, shellFill)
        canvas.drawPath(shell, lip)
        canvas.drawPath(ribs, ribPaint)
        canvas.drawRoundRect(field, 19f, 19f, fieldPaint)
    }

    override fun drawPip(canvas: Canvas, x: Float, y: Float) {
        val saved = canvas.save()
        canvas.translate(x, y)
        canvas.drawCircle(0f, 0f, 5f, pipRim)
        canvas.drawCircle(0f, 0f, 4f, pearl)
        canvas.drawCircle(-1.2f, -1.6f, 1f, shine)
        canvas.restoreToCount(saved)
    }
}

/** A cut octagonal ice crystal: flat facets instead of the rounded enamel cube. */
internal class FrostDiceRenderer : EnvironmentDiceRenderer() {
    private val body = Path().apply {
        moveTo(30f, 9f); lineTo(70f, 9f); lineTo(91f, 30f); lineTo(91f, 72f)
        lineTo(70f, 93f); lineTo(30f, 93f); lineTo(9f, 72f); lineTo(9f, 30f); close()
    }
    private val face = Path().apply {
        moveTo(32f, 24f); lineTo(68f, 24f); lineTo(77f, 33f); lineTo(77f, 71f)
        lineTo(67f, 80f); lineTo(33f, 80f); lineTo(23f, 70f); lineTo(23f, 33f); close()
    }
    private val facets = Path().apply {
        moveTo(30f, 9f); lineTo(32f, 24f); lineTo(23f, 33f); lineTo(9f, 30f)
        moveTo(70f, 9f); lineTo(68f, 24f); lineTo(77f, 33f); lineTo(91f, 30f)
        moveTo(91f, 72f); lineTo(77f, 71f); lineTo(67f, 80f); lineTo(70f, 93f)
        moveTo(9f, 72f); lineTo(23f, 70f); lineTo(33f, 80f); lineTo(30f, 93f)
    }
    private val darkFacet = Path().apply {
        moveTo(77f, 33f); lineTo(91f, 30f); lineTo(91f, 72f); lineTo(70f, 93f)
        lineTo(67f, 80f); lineTo(77f, 71f); close()
    }
    private val ice = gradient(0xFFFFFFFF.toInt(), 0xFFB9E7FB.toInt(), 0xFF8BADE7.toInt())
    private val clearFace = gradient(0xFFFFFFFF.toInt(), 0xFFE3F5FF.toInt())
    private val facetFill = fill(0x667492CD)
    private val cut = stroke(0xCFFFFFFF.toInt(), 1.2f)
    private val edge = stroke(0xFF6C99CD.toInt(), 1.8f)
    private val diamond = Path().apply {
        moveTo(0f, -5.2f); lineTo(4.5f, 0f); lineTo(0f, 5.2f); lineTo(-4.5f, 0f); close()
    }
    private val pip = fill(0xFF294C92.toInt())
    private val pipLight = stroke(0xFF9CCFEF.toInt(), .7f)

    override fun drawBody(canvas: Canvas) {
        canvas.drawPath(body, ice)
        canvas.drawPath(darkFacet, facetFill)
        canvas.drawPath(face, clearFace)
        canvas.drawPath(facets, cut)
        canvas.drawPath(face, cut)
        canvas.drawPath(body, edge)
        canvas.drawLine(44f, 16f, 58f, 16f, cut)
    }

    override fun drawPip(canvas: Canvas, x: Float, y: Float) {
        val saved = canvas.save()
        canvas.translate(x, y)
        canvas.drawPath(diamond, pip)
        canvas.drawLine(-2f, 0f, 0f, -3.2f, pipLight)
        canvas.restoreToCount(saved)
    }
}

/** A soft moon cushion with a deep domed face, gold bezel and crescent on its outer crown. */
internal class StarlightDiceRenderer : EnvironmentDiceRenderer() {
    private val back = RectF(13f, 17f, 87f, 91f)
    private val face = RectF(13f, 11f, 87f, 85f)
    private val bezel = RectF(17f, 15f, 83f, 81f)
    private val inner = RectF(21f, 19f, 79f, 77f)
    private val dome = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(33f, 26f, 71f,
            intArrayOf(0xFF595384.toInt(), 0xFF30294F.toInt(), 0xFF14172F.toInt()), null, Shader.TileMode.CLAMP)
    }
    private val backPaint = fill(0xFF17152E.toInt())
    private val rim = stroke(0xFFEBC976.toInt(), 2.3f)
    private val innerRim = stroke(0x668F83BC, .8f)
    private val moon = Path().apply {
        moveTo(59f, 11f); cubicTo(49f, 3f, 37f, 10f, 40f, 18f)
        cubicTo(43f, 25f, 55f, 24f, 59f, 17f)
        cubicTo(48f, 21f, 46f, 10f, 59f, 11f); close()
    }
    private val moonPaint = gradient(0xFFFFEDB6.toInt(), 0xFFD6A755.toInt())
    private val pipShadow = fill(0xFF100F24.toInt())
    private val pip = gradient(0xFFFFEFB5.toInt(), 0xFFEAC477.toInt())
    private val glint = fill(0xFFFFF8DA.toInt())

    override fun drawBody(canvas: Canvas) {
        canvas.drawRoundRect(back, 26f, 26f, backPaint)
        canvas.drawRoundRect(face, 26f, 26f, dome)
        canvas.drawRoundRect(bezel, 22f, 22f, rim)
        canvas.drawRoundRect(inner, 18f, 18f, innerRim)
        canvas.drawPath(moon, moonPaint)
        canvas.drawLine(29f, 85f, 71f, 85f, innerRim)
    }

    override fun drawPip(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y + 1.2f, 5.2f, pipShadow)
        canvas.drawCircle(x, y, 4.6f, pip)
        canvas.drawCircle(x - 1f, y - 1.2f, 1f, glint)
    }
}
