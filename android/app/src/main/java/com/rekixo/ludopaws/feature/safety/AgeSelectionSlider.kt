package com.rekixo.ludopaws.feature.safety

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.view.View
import android.widget.SeekBar

/** Native SeekBar keeps touch, keyboard and accessibility age selection intact. */
internal class AgeSelectionSlider(context: Context) : SeekBar(context) {
    private val density = resources.displayMetrics.density
    private val arrows = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.6f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arrowOutline = Paint(arrows).apply {
        color = 0x99503522.toInt()
        strokeWidth = 5f * density
    }
    private val arrow = Path()

    init {
        max = 100
        minimumHeight = (72 * density).toInt()
        setPadding((40 * density).toInt(), 0, (40 * density).toInt(), 0)
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        splitTrack = false
        progressTintList = null
        progressBackgroundTintList = null
        thumbTintList = null
        tickMark = null
        progressDrawable = LayerDrawable(arrayOf(AgeTrackDrawable(density, false), AgeTrackDrawable(density, true))).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
        thumb = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFE4EAF0.toInt())).apply {
            shape = GradientDrawable.OVAL
            setSize((34 * density).toInt(), (34 * density).toInt())
            setStroke(density.toInt().coerceAtLeast(1), 0xFFCCD5DE.toInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val knob = thumb ?: return
        val x = paddingLeft - thumbOffset + knob.bounds.exactCenterX()
        val y = paddingTop + knob.bounds.exactCenterY()
        val phase = (SystemClock.uptimeMillis() % 1_200L).toDouble() / 1_200.0
        val travel = ((1.0 - kotlin.math.cos(phase * Math.PI * 2.0)) * 2.5).toFloat() * density
        val offset = 26f * density + travel
        val half = 4f * density
        arrow.reset()
        arrow.moveTo(x - offset + half, y - half)
        arrow.lineTo(x - offset, y)
        arrow.lineTo(x - offset + half, y + half)
        arrow.moveTo(x + offset - half, y - half)
        arrow.lineTo(x + offset, y)
        arrow.lineTo(x + offset - half, y + half)
        canvas.drawPath(arrow, arrowOutline)
        canvas.drawPath(arrow, arrows)
        if (isAttachedToWindow && isShown && windowVisibility == View.VISIBLE) postInvalidateOnAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow && isShown) postInvalidateOnAnimation()
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow && visibility == View.VISIBLE) postInvalidateOnAnimation()
    }
}

/** Same RGB palette as the splash. The progress layer uses the platform level. */
private class AgeTrackDrawable(private val density: Float, private val filled: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private val clipping = Path()
    private var rgb: Shader? = null
    private var lighting: Shader? = null
    private var opacity = 255
    private var filter: ColorFilter? = null

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        val halfHeight = 12f * density
        track.set(bounds.left.toFloat(), bounds.exactCenterY() - halfHeight, bounds.right.toFloat(), bounds.exactCenterY() + halfHeight)
        clipping.reset()
        clipping.addRoundRect(track, halfHeight, halfHeight, Path.Direction.CW)
        rgb = LinearGradient(track.left, 0f, track.right.coerceAtLeast(track.left + 1f), 0f,
            intArrayOf(0xFFFF655B.toInt(), 0xFFFFD35F.toInt(), 0xFF5BDF9F.toInt(), 0xFF45A8FF.toInt()), null, Shader.TileMode.CLAMP)
        lighting = LinearGradient(0f, track.top, 0f, track.bottom,
            intArrayOf(0x77FFFFFF, 0x00FFFFFF, 0x44001D3F), floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        if (track.width() <= 0f || (filled && level == 0)) return
        paint.alpha = opacity
        paint.colorFilter = filter
        paint.style = Paint.Style.FILL
        if (!filled) {
            paint.shader = null
            paint.color = 0xFF614532.toInt()
            paint.alpha = opacity
            canvas.drawRoundRect(track, track.height() / 2f, track.height() / 2f, paint)
            return
        }
        val checkpoint = canvas.save()
        canvas.clipPath(clipping)
        canvas.clipRect(track.left, track.top, track.left + track.width() * level.coerceIn(0, 10000) / 10000f, track.bottom)
        paint.shader = rgb
        canvas.drawRoundRect(track, track.height() / 2f, track.height() / 2f, paint)
        paint.shader = lighting
        canvas.drawRoundRect(track, track.height() / 2f, track.height() / 2f, paint)
        canvas.restoreToCount(checkpoint)
    }

    override fun onLevelChange(level: Int): Boolean { invalidateSelf(); return true }
    override fun setAlpha(alpha: Int) { opacity = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { filter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
