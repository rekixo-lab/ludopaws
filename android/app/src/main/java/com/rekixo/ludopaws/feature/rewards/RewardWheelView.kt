package com.rekixo.ludopaws.feature.rewards

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Sector sizes match the disclosed probabilities. The server chooses the prize before animation. */
internal class RewardWheelView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val odds = listOf(50f, 30f, 15f, 5f)
    private val gems = listOf(1, 2, 3, 5)
    private val colors = intArrayOf(0xFF287F74.toInt(), 0xFF315C9F.toInt(), 0xFF8457A1.toInt(), 0xFFC59A35.toInt())
    private var angle = 0f
    private var animator: ValueAnimator? = null
    fun stopAt(prize: Int, animated: Boolean, done: () -> Unit) {
        val index = gems.indexOf(prize)
        if (index < 0) { done(); return }
        animator?.cancel()
        val midpoint = odds.take(index).sum() * 3.6f + odds[index] * 1.8f
        val target = (360f - midpoint) % 360f
        val end = angle + 1080f + ((target - angle % 360f + 360f) % 360f)
        if (!animated) { angle = target; invalidate(); done(); return }
        animator = ValueAnimator.ofFloat(angle, end).apply {
            duration = 1500L
            interpolator = DecelerateInterpolator()
            addUpdateListener { angle = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) done() }
            })
            start()
        }
    }
    override fun onDraw(canvas: Canvas) {
        val radius = minOf(width, height) * .43f
        val cx = width / 2f; val cy = height / 2f
        val bounds = RectF(cx-radius, cy-radius, cx+radius, cy+radius)
        canvas.save(); canvas.rotate(angle, cx, cy)
        var start = -90f
        odds.forEachIndexed { i, percent ->
            paint.color = colors[i]
            val sweep = percent * 3.6f
            canvas.drawArc(bounds, start, sweep, true, paint)
            val radians = Math.toRadians((start + sweep / 2).toDouble())
            paint.color = 0xFFFFFFFF.toInt(); paint.textSize = radius * .15f; paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${gems[i]}", cx + kotlin.math.cos(radians).toFloat() * radius * .7f,
                cy + kotlin.math.sin(radians).toFloat() * radius * .7f + paint.textSize / 3, paint)
            start += sweep
        }
        canvas.restore()
        paint.color = 0xFFFFE6AA.toInt(); paint.textSize = radius * .25f; paint.textAlign = Paint.Align.CENTER
        canvas.drawText("▼", cx, cy-radius+paint.textSize/3, paint)
    }
    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }
}
