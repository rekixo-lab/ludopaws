package com.rekixo.ludopaws

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class GameResultArtView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    enum class Mode {
        ONLINE,
        OFFLINE,
    }

    var mode: Mode =
        Mode.ONLINE
        set(value) {
            field = value
            invalidate()
        }

    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isDither = true
        }

    private val stroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            isDither = true
        }

    private var phase = 0f

    private val animator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800L
            repeatCount = 0
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
        }

    private val confetti =
        listOf(
            Triple(.10f, .02f, .00f),
            Triple(.22f, .15f, .18f),
            Triple(.34f, -.04f, .37f),
            Triple(.48f, .11f, .56f),
            Triple(.62f, -.06f, .73f),
            Triple(.76f, .16f, .91f),
            Triple(.88f, .01f, .28f),
            Triple(.95f, .24f, .64f),
        )

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Result panels are mounted hidden during play: celebrate only when
        // the match ends, never while the screen is still being built.
        if (isShown) startCelebration()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow && isShown) {
            startCelebration()
        } else if (visibility != VISIBLE) {
            animator.cancel()
        }
    }

    private fun startCelebration() {
        animator.cancel()
        phase = 0f
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val accent =
            if (mode == Mode.ONLINE) {
                0xFF24D9FF.toInt()
            } else {
                0xFF46E8A4.toInt()
            }

        // Two-second celebration ends cleanly; no permanent motion behind text.
        if (phase < 0.99f) {
            drawBurst(canvas, w, h, accent)
            drawConfetti(canvas, w, h, accent)
        }
    }

    private fun drawBurst(
        canvas: Canvas,
        w: Float,
        h: Float,
        accent: Int,
    ) {
        val cx = w * .5f
        val cy = h * .48f
        val pulse =
            ((sin(phase * 2f * PI) + 1f) * .5f)
                .toFloat()
        val inner = min(w, h) * (.18f + pulse * .04f)
        val outer = inner + min(w, h) * .08f

        stroke.strokeWidth = dp(2f)
        stroke.color = 0xB6FFFFFF.toInt()

        repeat(10) { index ->
            val angle =
                (-PI / 2.0) +
                    (index * 2.0 * PI / 10.0) +
                    phase * .32
            val startX = cx + cos(angle).toFloat() * inner
            val startY = cy + sin(angle).toFloat() * inner
            val endX = cx + cos(angle).toFloat() * outer
            val endY = cy + sin(angle).toFloat() * outer
            canvas.drawLine(
                startX,
                startY,
                endX,
                endY,
                stroke,
            )
        }

    }

    private fun drawConfetti(
        canvas: Canvas,
        w: Float,
        h: Float,
        accent: Int,
    ) {
        confetti.forEachIndexed { index, piece ->
            val travel =
                ((phase + piece.third) % 1f)
                    .coerceIn(0f, 1f)
            val x =
                (w * piece.first) +
                    sin(
                        (travel * 2f * PI) +
                            index * .9f,
                    ).toFloat() *
                    dp(18f)
            val y =
                -dp(18f) +
                    travel *
                    (h + dp(36f))
            val fade =
                when {
                    travel < .10f -> travel / .10f
                    travel > .88f -> (1f - travel) / .12f
                    else -> 1f
                }.coerceIn(0f, 1f)

            val color =
                when (index % 4) {
                    0 -> accent
                    1 -> 0xFFFFD45E.toInt()
                    2 -> 0xFFFF5D72.toInt()
                    else -> Color.WHITE
                }

            paint.color = color
            paint.alpha = (fade * 210f).toInt()

            canvas.save()
            canvas.rotate(
                (travel * 270f) +
                    index * 22f,
                x,
                y,
            )
            canvas.drawRoundRect(
                x - dp(2.4f),
                y - dp(7f),
                x + dp(2.4f),
                y + dp(7f),
                dp(2f),
                dp(2f),
                paint,
            )
            canvas.restore()
        }

        paint.alpha = 255
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}
