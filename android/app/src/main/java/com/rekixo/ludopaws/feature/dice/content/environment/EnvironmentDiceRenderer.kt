package com.rekixo.ludopaws.feature.dice.content.environment

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.feature.dice.domain.DicePalette
import com.rekixo.ludopaws.feature.dice.rendering.DiceRenderFrame
import com.rekixo.ludopaws.feature.dice.rendering.DiceRenderer

internal enum class EnvironmentDiceTheme(
    val key: String, val artwork: Int, val faceCorners: FloatArray,
    val light: Int, val base: Int, val dark: Int,
    val rim: Int, val pip: Int, val accent: Int,
) {
    FOREST("forest_dew", R.drawable.dice_forest_dew_3d,
        floatArrayOf(14f, 21f, 79f, 20f, 79f, 86f, 14f, 87f),
        0xFFFFE8B3.toInt(), 0xFFD9A568.toInt(), 0xFF805037.toInt(),
        0xFFC18C50.toInt(), 0xFF382217.toInt(), 0xFF59834B.toInt()),
    LAGOON("lagoon_pearl", R.drawable.dice_lagoon_pearl_3d,
        floatArrayOf(18f, 27f, 77f, 24f, 77f, 83f, 18f, 86f),
        0xFFF0FFFD.toInt(), 0xFF9FE5DD.toInt(), 0xFF2B829C.toInt(),
        0xFF25617A.toInt(), 0xFFE0F9F5.toInt(), 0xFF4DADB6.toInt()),
    FROST("frost_crystal", R.drawable.dice_frost_crystal_3d,
        floatArrayOf(17f, 24f, 76f, 24f, 76f, 82f, 17f, 82f),
        0xFFFFFFFF.toInt(), 0xFFD7EDFF.toInt(), 0xFF779EDE.toInt(),
        0xFF9ACCEB.toInt(), 0xFF23457D.toInt(), 0xFF739ECD.toInt()),
    STARLIGHT("starlight", R.drawable.dice_starlight_3d,
        floatArrayOf(18f, 27f, 78f, 25f, 78f, 87f, 18f, 89f),
        0xFF595384.toInt(), 0xFF30294F.toInt(), 0xFF14172F.toInt(),
        0xFF9B6930.toInt(), 0xFFFFE8A6.toInt(), 0xFFB9AFE7.toInt()),
}

internal class ForestDiceRenderer : EnvironmentDiceRenderer(EnvironmentDiceTheme.FOREST)
internal class LagoonDiceRenderer : EnvironmentDiceRenderer(EnvironmentDiceTheme.LAGOON)
internal class FrostDiceRenderer : EnvironmentDiceRenderer(EnvironmentDiceTheme.FROST)
internal class StarlightDiceRenderer : EnvironmentDiceRenderer(EnvironmentDiceTheme.STARLIGHT)

/** Shared immutable artwork, sampled once to about 314px; all four total roughly 1.5 MiB decoded. */
private object EnvironmentDiceArtwork {
    private val cache = LruCache<Int, Bitmap>(4)

    @Synchronized
    fun load(resources: Resources, id: Int): Bitmap {
        cache.get(id)?.let { return it }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 4
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return requireNotNull(BitmapFactory.decodeResource(resources, id, options)) {
            "Missing environment dice artwork: $id"
        }.also { cache.put(id, it) }
    }
}

/** Photorealistic body + material-aware pips projected onto its actual front face. */
internal open class EnvironmentDiceRenderer(private val theme: EnvironmentDiceTheme) : DiceRenderer {
    private var artwork: Bitmap? = null
    private val artworkBounds = RectF(0f, 0f, 100f, 100f)
    private val outline = RectF(4.5f, 4.5f, 95.5f, 95.5f)
    private val controlBounds = RectF()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val faceProjection = Matrix().apply {
        require(setPolyToPoly(
            floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f), 0,
            theme.faceCorners, 0, 4,
        )) { "Invalid dice face projection: ${theme.key}" }
    }
    private val teamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pipShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 1.5f, 8f,
            intArrayOf(0x66091120, 0x00091120), null, Shader.TileMode.CLAMP)
    }
    private val pipRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-5f, -6f, 5f, 6f,
            intArrayOf(theme.dark, theme.rim, theme.light), null, Shader.TileMode.CLAMP)
    }
    private val pipBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = when (theme) {
            EnvironmentDiceTheme.FOREST -> RadialGradient(-1.5f, -2f, 8f,
                intArrayOf(0xFF24150E.toInt(), theme.pip, 0xFF8D5E38.toInt()), null, Shader.TileMode.CLAMP)
            EnvironmentDiceTheme.LAGOON -> RadialGradient(-2f, -2.5f, 8f,
                intArrayOf(0xFFFFFFFF.toInt(), theme.pip, 0xFF68A8B6.toInt()), null, Shader.TileMode.CLAMP)
            EnvironmentDiceTheme.FROST -> LinearGradient(-4f, -5f, 4f, 5f,
                intArrayOf(0xFF476D9F.toInt(), theme.pip, 0xFF142E58.toInt()), null, Shader.TileMode.CLAMP)
            EnvironmentDiceTheme.STARLIGHT -> LinearGradient(-5f, -6f, 5f, 6f,
                intArrayOf(0xFFFFF5CE.toInt(), theme.pip, 0xFFC68F3E.toInt()), null, Shader.TileMode.CLAMP)
        }
    }
    private val pipGlint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xDFFFFFFF.toInt() }
    private val cavityLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = .75f
        color = 0x99E6C08B.toInt()
    }
    private val pipBounds = RectF(-6.2f, -6.2f, 6.2f, 6.2f)
    private val diamond = Path().apply {
        moveTo(0f, -7.1f); lineTo(6.3f, 0f); lineTo(0f, 7.1f); lineTo(-6.3f, 0f); close()
    }
    private val positions = arrayOf(
        floatArrayOf(), floatArrayOf(50f, 50f),
        floatArrayOf(27f, 27f, 73f, 73f),
        floatArrayOf(27f, 27f, 50f, 50f, 73f, 73f),
        floatArrayOf(27f, 27f, 73f, 27f, 27f, 73f, 73f, 73f),
        floatArrayOf(27f, 27f, 73f, 27f, 50f, 50f, 27f, 73f, 73f, 73f),
        floatArrayOf(27f, 27f, 73f, 27f, 27f, 50f, 73f, 50f, 27f, 73f, 73f, 73f),
    )

    override fun prepare(resources: Resources) {
        artwork = EnvironmentDiceArtwork.load(resources, theme.artwork)
    }

    override fun draw(canvas: Canvas, size: Float, palette: DicePalette, frame: DiceRenderFrame): RectF {
        val saved = canvas.save()
        canvas.scale(size / 100f, size / 100f)
        val body = canvas.save()
        // Leave space around the moon/leaf, so team feedback does not cross the ornament.
        canvas.translate(5f, 5f)
        canvas.scale(.9f, .9f)
        artwork?.let { canvas.drawBitmap(it, null, artworkBounds, imagePaint) }
        val projected = canvas.save()
        canvas.concat(faceProjection)
        val pips = positions.getOrNull(frame.face)
        if (pips != null) {
            var index = 0
            while (index < pips.size) {
                drawPip(canvas, pips[index], pips[index + 1])
                index += 2
            }
        }
        canvas.restoreToCount(projected)
        canvas.restoreToCount(body)

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

    private fun drawPip(canvas: Canvas, x: Float, y: Float) {
        val saved = canvas.save()
        canvas.translate(x, y)
        if (theme == EnvironmentDiceTheme.FROST) {
            canvas.drawPath(diamond, pipRim)
            canvas.scale(.8f, .8f)
            canvas.drawPath(diamond, pipBody)
            canvas.drawCircle(-1.3f, -2.5f, .7f, pipGlint)
        } else {
            if (theme != EnvironmentDiceTheme.FOREST) canvas.drawCircle(0f, 1.5f, 8f, pipShadow)
            canvas.drawCircle(0f, 0f, 6.7f, pipRim)
            canvas.drawCircle(0f, 0f, 5.5f, pipBody)
            if (theme == EnvironmentDiceTheme.FOREST) {
                canvas.drawArc(pipBounds, 25f, 120f, false, cavityLight)
            } else {
                canvas.drawCircle(-1.7f, -2.3f, 1.1f, pipGlint)
                canvas.drawCircle(1.8f, 2f, .45f, pipGlint)
            }
        }
        canvas.restoreToCount(saved)
    }
}
