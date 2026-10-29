package com.rekixo.ludopaws.feature.store.presentation.art

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import kotlin.math.min

class GemClusterView(
    context: Context,
    private val gemCount: Int = 1,
) : View(context) {
    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)
    private val facetPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)
    private val facetLinePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(0.65f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = 0x68C5FFFF
        }

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


    private var raster: android.graphics.Bitmap? = null
    private val rasterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        raster = if (w > 0 && h > 0) android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).also {
            drawCluster(Canvas(it))
        } else null
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        raster?.let { canvas.drawBitmap(it, 0f, 0f, rasterPaint) }
    }
    private fun drawCluster(canvas: Canvas) {
        val side = min(width, height).toFloat()
        if (side <= 0f) return

        val gemSize = when (gemCount.coerceIn(1, 5)) {
            1 -> side * 0.70f
            2 -> side * 0.61f
            3 -> side * 0.52f
            4 -> side * 0.46f
            else -> side * 0.42f
        }
        val centerX = width / 2f
        val centerY = height / 2f

        placements.forEach { placement ->
            drawGem(
                canvas = canvas,
                centerX = centerX + placement.x * side,
                centerY = centerY + placement.y * side,
                size = gemSize * placement.scale,
            )
        }
    }

    private fun drawGem(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        size: Float,
    ) {
        val halfWidth = size * 0.46f
        val crownY = centerY - size * 0.24f
        val top = centerY - size * 0.48f
        val bottom = centerY + size * 0.50f
        val left = centerX - halfWidth
        val right = centerX + halfWidth
        val shoulderLeft = centerX - size * 0.31f
        val shoulderRight = centerX + size * 0.31f
        val depthX = size * 0.025f
        val depthY = size * 0.045f

        val body =
            Path().apply {
                moveTo(left, crownY)
                lineTo(shoulderLeft, top)
                lineTo(shoulderRight, top)
                lineTo(right, crownY)
                lineTo(centerX, bottom)
                close()
            }
        val depth =
            Path().apply {
                moveTo(left + depthX, crownY + depthY)
                lineTo(shoulderLeft + depthX, top + depthY)
                lineTo(shoulderRight + depthX, top + depthY)
                lineTo(right + depthX, crownY + depthY)
                lineTo(centerX + depthX, bottom + depthY)
                close()
            }

        // A darker offset edge gives each stone visible thickness.
        fillPaint.shader =
            LinearGradient(
                left,
                top,
                right,
                bottom + depthY,
                intArrayOf(
                    0xFF1687AD.toInt(),
                    0xFF07517F.toInt(),
                    0xFF031D50.toInt(),
                ),
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawPath(depth, fillPaint)

        // Rich, light-facing enamel beneath the individual cut facets.
        fillPaint.shader =
            LinearGradient(
                left,
                top,
                right,
                bottom,
                intArrayOf(
                    0xFFE9FFFF.toInt(),
                    0xFF65F4EA.toInt(),
                    0xFF16BFD9.toInt(),
                    0xFF07569E.toInt(),
                ),
                floatArrayOf(0f, 0.22f, 0.58f, 1f),
                Shader.TileMode.CLAMP,
            )
        canvas.drawPath(body, fillPaint)
        fillPaint.shader = null

        val crown =
            Path().apply {
                moveTo(shoulderLeft, top)
                lineTo(shoulderRight, top)
                lineTo(centerX, crownY)
                close()
            }
        val leftCrown =
            Path().apply {
                moveTo(left, crownY)
                lineTo(shoulderLeft, top)
                lineTo(centerX, crownY)
                close()
            }
        val leftPavilion =
            Path().apply {
                moveTo(left, crownY)
                lineTo(centerX, crownY)
                lineTo(centerX, bottom)
                close()
            }
        val rightCrown =
            Path().apply {
                moveTo(shoulderRight, top)
                lineTo(right, crownY)
                lineTo(centerX, crownY)
                close()
            }
        val rightPavilion =
            Path().apply {
                moveTo(centerX, crownY)
                lineTo(right, crownY)
                lineTo(centerX, bottom)
                close()
            }

        drawFacet(
            canvas,
            leftCrown,
            left,
            top,
            centerX,
            crownY,
            0xFFD9FFFF.toInt(),
            0xFF8DFFF6.toInt(),
            0xFF19C6DB.toInt(),
        )
        drawFacet(
            canvas,
            rightCrown,
            shoulderRight,
            top,
            right,
            crownY,
            0xFFD1FBFF.toInt(),
            0xFF42DDEB.toInt(),
            0xFF0874B8.toInt(),
        )
        drawFacet(
            canvas,
            crown,
            centerX,
            top,
            centerX,
            crownY + size * 0.04f,
            0xFFE6FFFF.toInt(),
            0xFF91F8F0.toInt(),
            0xFF22B9DB.toInt(),
        )
        drawFacet(
            canvas,
            leftPavilion,
            left,
            crownY,
            centerX,
            bottom,
            0xFF5AF3E8.toInt(),
            0xFF19C7DD.toInt(),
            0xFF0863A8.toInt(),
        )
        drawFacet(
            canvas,
            rightPavilion,
            centerX,
            crownY,
            right,
            bottom,
            0xFF159CCF.toInt(),
            0xFF075CA8.toInt(),
            0xFF032B68.toInt(),
        )

        // Fine facet junctions mimic the crisp edges of a cut gemstone.
        canvas.drawLine(left, crownY, centerX, crownY, facetLinePaint)
        canvas.drawLine(shoulderLeft, top, centerX, crownY, facetLinePaint)
        canvas.drawLine(shoulderRight, top, centerX, crownY, facetLinePaint)

        // Broad, soft reflection across the upper-left crown.
        val reflection =
            Path().apply {
                moveTo(left + size * 0.10f, crownY - size * 0.015f)
                lineTo(shoulderLeft + size * 0.07f, top + size * 0.055f)
                quadTo(
                    shoulderLeft + size * 0.17f,
                    top + size * 0.015f,
                    centerX - size * 0.11f,
                    crownY - size * 0.075f,
                )
                quadTo(
                    centerX - size * 0.08f,
                    crownY - size * 0.015f,
                    left + size * 0.19f,
                    crownY + size * 0.045f,
                )
                close()
            }
        highlightPaint.shader =
            LinearGradient(
                left,
                top,
                centerX,
                crownY + size * 0.08f,
                intArrayOf(
                    0xD8FFFFFF.toInt(),
                    0x78DFFFFF.toInt(),
                    0x06FFFFFF,
                ),
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawPath(reflection, highlightPaint)
        highlightPaint.shader = null

        // Keep reflections on the gem surfaces; don't draw a hard outer stroke.
        canvas.drawLine(
            left + size * 0.09f,
            crownY - size * 0.005f,
            shoulderLeft + size * 0.06f,
            top + size * 0.045f,
            facetLinePaint,
        )
        fillPaint.color = Color.WHITE
        canvas.drawCircle(
            centerX - size * 0.17f,
            centerY - size * 0.23f,
            size * 0.023f,
            fillPaint,
        )
        fillPaint.color = Color.TRANSPARENT
    }

    private fun drawFacet(
        canvas: Canvas,
        path: Path,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        vararg colors: Int,
    ) {
        facetPaint.shader =
            LinearGradient(
                startX,
                startY,
                endX,
                endY,
                colors,
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawPath(path, facetPaint)
        facetPaint.shader = null
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    private data class GemPlacement(
        val x: Float,
        val y: Float,
        val scale: Float,
    )
}
