package com.rekixo.ludopaws.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * The star field is drawn directly behind the home carousel's promotional
 * content. No bitmap, video, RenderEffect, software layer or new dependency:
 * paints/shaders/stars are cached and refreshed only when the view is resized.
 */
internal class HomeSpaceBackgroundView(context: Context) : View(context) {
    private data class Star(
        val x: Float,
        val y: Float,
        val radius: Float,
        val drift: Float,
        val pulse: Float,
        val phase: Float,
        val color: Int,
        val sparkle: Boolean,
    )

    private val density = resources.displayMetrics.density
    private val clipPath = Path()
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starsPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val meteorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val nebulae = arrayOfNulls<Shader>(2)
    private var stars: List<Star> = emptyList()
    private var active = false
    private var phaseSeconds = 0f
    private var lastFrameAt = 0L
    private val motion = object : Runnable {
        override fun run() {
            if (!canAnimate()) return
            val now = SystemClock.uptimeMillis()
            // Do not simulate all frames skipped during backgrounding.
            phaseSeconds = (phaseSeconds +
                ((now - lastFrameAt).coerceIn(0L, 80L) / 1000f)) % 780f
            lastFrameAt = now
            invalidate()
            postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        updateMotion()
    }

    private fun canAnimate(): Boolean =
        active && isAttachedToWindow && isShown && hasWindowFocus() &&
            windowVisibility == VISIBLE &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled())

    private fun updateMotion() {
        removeCallbacks(motion)
        if (canAnimate()) {
            lastFrameAt = SystemClock.uptimeMillis()
            post(motion)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateMotion()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) updateMotion()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) updateMotion()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (isAttachedToWindow) updateMotion()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(motion)
        lastFrameAt = 0L
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipPath.reset()
        if (w <= 0 || h <= 0) {
            stars = emptyList()
            return
        }
        val width = w.toFloat()
        val height = h.toFloat()
        clipPath.addRoundRect(0f, 0f, width, height, dp(18f), dp(18f), Path.Direction.CW)
        backgroundPaint.shader = LinearGradient(
            0f, 0f, width, height,
            intArrayOf(0xFF070A24.toInt(), 0xFF130E39.toInt(), 0xFF06152C.toInt(), 0xFF020916.toInt()),
            floatArrayOf(0f, .35f, .7f, 1f), Shader.TileMode.CLAMP,
        )
        nebulae[0] = RadialGradient(
            width * .23f, height * .30f, width * .65f,
            intArrayOf(0xA44B328E.toInt(), 0x443D44AD, Color.TRANSPARENT),
            floatArrayOf(0f, .46f, 1f), Shader.TileMode.CLAMP,
        )
        nebulae[1] = RadialGradient(
            width * .84f, height * .70f, width * .58f,
            intArrayOf(0x9B087E9D.toInt(), 0x33235DA2, Color.TRANSPARENT),
            floatArrayOf(0f, .49f, 1f), Shader.TileMode.CLAMP,
        )
        // Deterministic stars do not jump around on every resume or redraw.
        val random = Random(0x5EED2026)
        val palette = intArrayOf(0xFFFFFFFF.toInt(), 0xFFD1E8FF.toInt(),
            0xFFA9D9FF.toInt(), 0xFFB2F5FF.toInt(), 0xFFFFDCE7.toInt())
        stars = List(HomeSpaceBackgroundPolicy.starCount(w, h, density)) { index ->
            Star(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = dp(.45f + random.nextFloat() * 1.10f),
                drift = .0003f + random.nextFloat() * .0019f,
                pulse = .45f + random.nextFloat() * 1.3f,
                phase = random.nextFloat() * 6.28318f,
                color = palette[random.nextInt(palette.size)],
                sparkle = index % 17 == 0,
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val checkpoint = canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)

        for (shader in nebulae) {
            if (shader == null) continue
            nebulaPaint.shader = shader
            canvas.drawRect(0f, 0f, w, h, nebulaPaint)
        }
        nebulaPaint.shader = null

        for (star in stars) {
            val x = ((star.x + phaseSeconds * star.drift) % 1f) * w
            val y = ((star.y + phaseSeconds * star.drift * .32f) % 1f) * h
            val light = HomeSpaceBackgroundPolicy.starAlpha(phaseSeconds, star.pulse, star.phase)
            starsPaint.color = star.color
            starsPaint.alpha = light
            canvas.drawCircle(x, y, star.radius, starsPaint)
            if (star.sparkle) {
                haloPaint.color = star.color
                haloPaint.alpha = (light * .20f).toInt()
                canvas.drawCircle(x, y, star.radius * 3.2f, haloPaint)
                meteorPaint.color = star.color
                meteorPaint.alpha = (light * .42f).toInt()
                meteorPaint.strokeWidth = dp(.65f)
                val reach = star.radius * 3.2f
                canvas.drawLine(x - reach, y, x + reach, y, meteorPaint)
                canvas.drawLine(x, y - reach, x, y + reach, meteorPaint)
            }
        }

        // One subtle shooting star roughly every 13 seconds, not a particle
        // emitter. This stays inactive when Android animations are disabled.
        if (canAnimate()) {
            val cycle = (phaseSeconds + 3f) % 13f
            if (cycle < 1.1f) {
                val progress = cycle / 1.1f
                val x = w * (.08f + .82f * progress)
                val y = h * (.14f + .48f * progress)
                meteorPaint.color = 0xFF9EDCFF.toInt()
                meteorPaint.alpha = ((1f - progress) * 190f).toInt()
                meteorPaint.strokeWidth = dp(1.5f)
                canvas.drawLine(x - dp(44f), y - dp(26f), x, y, meteorPaint)
                starsPaint.color = Color.WHITE
                starsPaint.alpha = ((1f - progress) * 235f).toInt()
                canvas.drawCircle(x, y, dp(1.9f), starsPaint)
            }
        }
        canvas.restoreToCount(checkpoint)
    }

    private fun dp(value: Float): Float = value * density

    private companion object {
        // 20 FPS is enough for drifting starlight and leaves the UI/GPU budget
        // to the existing live 3D character, carousel swipe and controls.
        const val FRAME_INTERVAL_MS = 50L
    }
}

internal object HomeSpaceBackgroundPolicy {
    fun starCount(widthPx: Int, heightPx: Int, density: Float): Int {
        val safeDensity = density.coerceAtLeast(.5f)
        val areaDp = (widthPx.coerceAtLeast(0) / safeDensity) *
            (heightPx.coerceAtLeast(0) / safeDensity)
        return (areaDp / 900f).toInt().coerceIn(36, 120)
    }

    fun starAlpha(seconds: Float, speed: Float, seed: Float): Int {
        val pulse = (.5f + .5f * sin(seconds * speed + seed)).coerceIn(0f, 1f)
        return (85f + pulse * 160f).toInt().coerceIn(85, 245)
    }
}
