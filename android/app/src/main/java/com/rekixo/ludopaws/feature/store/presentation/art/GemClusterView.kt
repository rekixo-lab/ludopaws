package com.rekixo.ludopaws.feature.store.presentation.art

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.rekixo.ludopaws.core.ui.art.RareGemArtwork
import kotlin.math.min

/** Shared photo-style gem material, retaining the existing 1–5 pack silhouettes. */
class GemClusterView(
    context: Context,
    private val gemCount: Int = 1,
) : View(context) {
    private val gem = RareGemArtwork.gem(resources)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placements =
            when (gemCount.coerceIn(1, 5)) {
                1 -> listOf(GemPlacement(0f, 0f, 1f))
                2 -> listOf(
                    GemPlacement(-0.19f, 0.08f, 0.92f),
                    GemPlacement(0.19f, -0.08f, 0.92f),
                )
                3 -> listOf(
                    GemPlacement(0f, -0.18f, 0.88f),
                    GemPlacement(-0.20f, 0.14f, 0.76f),
                    GemPlacement(0.20f, 0.14f, 0.76f),
                )
                4 -> listOf(
                    GemPlacement(-0.18f, -0.16f, 0.72f),
                    GemPlacement(0.18f, -0.16f, 0.72f),
                    GemPlacement(-0.18f, 0.16f, 0.72f),
                    GemPlacement(0.18f, 0.16f, 0.72f),
                )
                else -> listOf(
                    GemPlacement(0f, -0.20f, 0.70f),
                    GemPlacement(-0.21f, -0.02f, 0.70f),
                    GemPlacement(0.21f, -0.02f, 0.70f),
                    GemPlacement(-0.12f, 0.20f, 0.70f),
                    GemPlacement(0.12f, 0.20f, 0.70f),
                )
            }

    private val gemBounds = placements.map { RectF() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val side = min(w, h).toFloat()
        val gemSize = side * when (gemCount.coerceIn(1, 5)) {
            1 -> .70f
            2 -> .61f
            3 -> .52f
            4 -> .46f
            else -> .42f
        }
        placements.forEachIndexed { index, placement ->
            val half = gemSize * placement.scale * .5f
            val x = w * .5f + placement.x * side
            val y = h * .5f + placement.y * side
            gemBounds[index].set(x - half, y - half, x + half, y + half)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (bounds in gemBounds) canvas.drawBitmap(gem, null, bounds, paint)
    }

    private data class GemPlacement(val x: Float, val y: Float, val scale: Float)
}
