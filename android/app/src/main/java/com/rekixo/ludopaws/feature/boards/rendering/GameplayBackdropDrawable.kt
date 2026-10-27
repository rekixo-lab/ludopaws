package com.rekixo.ludopaws.feature.boards.rendering

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable

internal class GameplayBackdropDrawable(environment: GameplayBoardEnvironment, private val cornerRadius: Float) : Drawable() {
    private val habitat = GameplayEnvironmentPainter(environment)
    private val outline = Path()
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = (environment.accent and 0x00FFFFFF) or 0xA0000000.toInt()
    }
    private val compositing = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scene: Picture? = null
    private var paintAlpha = 255
    private var filter: ColorFilter? = null

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        scene = null
        outline.reset()
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        val area = RectF(bounds)
        outline.addRoundRect(area, cornerRadius, cornerRadius, Path.Direction.CW)
        scene = Picture().also { picture ->
            val recorded = picture.beginRecording(bounds.width(), bounds.height())
            habitat.draw(recorded, bounds.width().toFloat(), bounds.height().toFloat())
            picture.endRecording()
        }
    }

    override fun draw(canvas: Canvas) {
        val picture = scene ?: return
        val saved = canvas.save()
        canvas.clipPath(outline)
        compositing.alpha = paintAlpha
        compositing.colorFilter = filter
        val layer = if (paintAlpha != 255 || filter != null) canvas.saveLayer(RectF(bounds), compositing) else -1
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.drawPicture(picture)
        canvas.restore()
        val inset = border.strokeWidth * .5f
        canvas.drawRoundRect(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset,
            cornerRadius, cornerRadius, border)
        if (layer >= 0) canvas.restoreToCount(layer)
        canvas.restoreToCount(saved)
    }

    override fun setAlpha(alpha: Int) {
        val next = alpha.coerceIn(0, 255)
        if (paintAlpha == next) return
        paintAlpha = next
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        if (filter == colorFilter) return
        filter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
