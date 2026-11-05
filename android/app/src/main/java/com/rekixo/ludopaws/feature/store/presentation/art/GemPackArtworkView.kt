package com.rekixo.ludopaws.feature.store.presentation.art

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.rekixo.ludopaws.core.ui.art.RareGemArtwork

/** An open chest with progressively fuller, hand-placed piles for the six gem packs. */
internal class GemPackArtworkView(context: Context, tier: Int) : View(context) {
    private val gem = RareGemArtwork.gem(resources)
    private val pile = piles[tier.coerceIn(piles.indices)]

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 128f, height / 88f)
        if (scale <= 0f) return

        val frame = canvas.save()
        canvas.translate((width - 128f * scale) / 2f, (height - 88f * scale) / 2f)
        canvas.scale(scale, scale)

        val chest = canvas.save()
        canvas.scale(pile.chestScale, pile.chestScale, 64f, 78f)
        canvas.drawOval(15f, 72f, 116f, 84f, groundShadow)
        canvas.drawPath(lidFrame, metal)
        canvas.drawPath(lidInset, lidWood)
        canvas.drawPath(opening, darkWood)
        canvas.drawCircle(65f, 42f, 39f, gemLight)
        pile.inside.forEach { drawGem(canvas, it) }
        canvas.drawPath(side, sideWood)
        canvas.drawPath(front, frontWood)
        canvas.drawPath(rim, metal)
        canvas.drawPath(baseTrim, lowerMetal)
        canvas.drawRoundRect(58f, 60f, 69f, 70f, 2f, 2f, metal)
        canvas.drawCircle(63.5f, 64f, 1.4f, darkWood)
        canvas.restoreToCount(chest)

        pile.spill.forEach { drawGem(canvas, it) }
        canvas.restoreToCount(frame)
    }

    private fun drawGem(canvas: Canvas, piece: Gem) {
        val saved = canvas.save()
        canvas.translate(piece.x, piece.y)
        canvas.rotate(piece.angle)
        canvas.scale(piece.size, piece.size)
        canvas.drawBitmap(gem, null, unitGem, gemPaint)
        canvas.restoreToCount(saved)
    }

    private data class Gem(val x: Float, val y: Float, val size: Float, val angle: Float)
    private data class Pile(val chestScale: Float, val inside: List<Gem>, val spill: List<Gem> = emptyList())

    private companion object {
        private val piles = listOf(
            Pile(.79f, listOf(
                Gem(57f, 47f, 26f, -18f), Gem(76f, 43f, 29f, 13f),
            )),
            Pile(.84f, listOf(
                Gem(61f, 36f, 27f, -12f), Gem(82f, 45f, 24f, 18f),
                Gem(48f, 48f, 23f, -24f), Gem(69f, 51f, 27f, 8f),
            )),
            Pile(.88f, listOf(
                Gem(71f, 31f, 28f, 13f), Gem(52f, 42f, 25f, -22f),
                Gem(89f, 44f, 23f, 27f), Gem(69f, 49f, 29f, -7f),
            ), listOf(Gem(106f, 75f, 18f, 25f))),
            Pile(.93f, listOf(
                Gem(59f, 32f, 25f, -18f), Gem(78f, 28f, 27f, 15f),
                Gem(93f, 43f, 23f, 28f), Gem(43f, 46f, 24f, -21f),
                Gem(68f, 45f, 30f, 5f), Gem(83f, 53f, 22f, -15f),
            ), listOf(Gem(107f, 74f, 20f, 23f), Gem(21f, 76f, 16f, -24f))),
            Pile(.97f, listOf(
                Gem(73f, 21f, 25f, 12f), Gem(51f, 33f, 23f, -27f),
                Gem(90f, 34f, 26f, 24f), Gem(44f, 47f, 23f, -20f),
                Gem(64f, 40f, 29f, -9f), Gem(82f, 49f, 28f, 13f),
                Gem(99f, 46f, 19f, 32f),
            ), listOf(Gem(108f, 73f, 21f, 23f), Gem(21f, 73f, 19f, -24f), Gem(36f, 79f, 15f, 12f))),
            Pile(1f, listOf(
                Gem(65f, 17f, 24f, -13f), Gem(83f, 24f, 26f, 21f),
                Gem(44f, 33f, 23f, -25f), Gem(98f, 34f, 22f, 26f),
                Gem(55f, 48f, 27f, -19f), Gem(72f, 38f, 30f, 7f),
                Gem(91f, 48f, 27f, 20f), Gem(36f, 47f, 20f, -28f),
            ), listOf(
                Gem(107f, 67f, 23f, 21f), Gem(22f, 72f, 21f, -27f),
                Gem(38f, 78f, 16f, 8f), Gem(117f, 79f, 14f, -16f),
            )),
        )

        private val unitGem = RectF(-.5f, -.5f, .5f, .5f)
        private val gemPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val groundShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66311C11 }
        private val darkWood = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF402016.toInt() }
        private val gemLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(65f, 42f, 39f, 0x885BE5FA.toInt(), 0x005BE5FA, Shader.TileMode.CLAMP)
        }
        private val lidFrame = polygon(29f, 50f, 19f, 25f, 36f, 12f, 88f, 12f, 104f, 39f, 96f, 51f)
        private val lidInset = polygon(32f, 45f, 24f, 26f, 38f, 16f, 85f, 16f, 98f, 39f, 93f, 46f)
        private val opening = polygon(28f, 48f, 88f, 41f, 111f, 51f, 98f, 61f, 26f, 59f)
        private val front = polygon(26f, 55f, 96f, 58f, 95f, 75f, 28f, 72f)
        private val side = polygon(96f, 58f, 111f, 50f, 109f, 67f, 95f, 75f)
        private val rim = polygon(25f, 54f, 96f, 57f, 112f, 49f, 111f, 54f, 96f, 62f, 26f, 59f)
        private val baseTrim = polygon(27f, 69f, 95f, 72f, 110f, 64f, 109f, 69f, 95f, 77f, 28f, 74f)

        private val metal = gradient(12f, 70f, 0xFFFFDE94.toInt(), 0xFFB46B2D.toInt())
        private val lowerMetal = gradient(64f, 78f, 0xFFE8AC5D.toInt(), 0xFF7B421D.toInt())
        private val lidWood = gradient(15f, 47f, 0xFF9F5A32.toInt(), 0xFF4E291D.toInt())
        private val frontWood = gradient(56f, 76f, 0xFFBD7742.toInt(), 0xFF754021.toInt())
        private val sideWood = gradient(51f, 74f, 0xFF895027.toInt(), 0xFF4A2919.toInt())

        private fun gradient(top: Float, bottom: Float, light: Int, dark: Int): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, top, 0f, bottom, light, dark, Shader.TileMode.CLAMP)
            }

        private fun polygon(vararg points: Float): Path = Path().apply {
            moveTo(points[0], points[1])
            for (index in 2 until points.size step 2) lineTo(points[index], points[index + 1])
            close()
        }
    }
}
