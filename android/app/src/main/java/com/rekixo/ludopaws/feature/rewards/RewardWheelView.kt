package com.rekixo.ludopaws.feature.rewards

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator

/** Sector sizes match the disclosed probabilities. The server chooses the prize before animation. */
internal class RewardWheelView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val odds = listOf(50f, 30f, 15f, 5f)
    private val gems = listOf(1, 2, 3, 5)
    private val colors = intArrayOf(0xFF2D7F3D.toInt(), 0xFF216DA7.toInt(), 0xFF794A9F.toInt(), 0xFFF0A62C.toInt())
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
        super.onDraw(canvas)
        val radius = minOf(width, height) * .40f
        if (radius <= 0f) return
        val cx = width / 2f; val cy = height / 2f + radius * .025f
        val bounds = RectF(cx-radius, cy-radius, cx+radius, cy+radius)
        paint.style = Paint.Style.FILL
        paint.color = 0x443B2414
        canvas.drawOval(RectF(cx-radius*.90f, cy+radius*.90f, cx+radius*.90f, cy+radius*1.13f), paint)
        paint.shader = LinearGradient(0f, cy-radius, 0f, cy+radius,
            intArrayOf(0xFFFFF1A6.toInt(), 0xFFE5A934.toInt(), 0xFFB47A25.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius*1.105f, paint); paint.shader = null
        paint.color = 0xFF6F431D.toInt(); canvas.drawCircle(cx, cy, radius*1.025f, paint)
        canvas.save(); canvas.rotate(angle, cx, cy)
        var start = -90f
        odds.forEachIndexed { i, percent ->
            paint.style = Paint.Style.FILL; paint.color = colors[i]
            val sweep = percent * 3.6f
            canvas.drawArc(bounds, start, sweep, true, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = radius*.012f; paint.color = 0x55FFFFFF
            canvas.drawArc(bounds, start, sweep, true, paint); paint.style = Paint.Style.FILL
            val radians = Math.toRadians((start + sweep / 2).toDouble())
            paint.color = if (i == 3) 0xFF4C2D12.toInt() else 0xFFFFFFFF.toInt(); paint.textSize = radius*.18f
            paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD); paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${gems[i]}", cx + kotlin.math.cos(radians).toFloat()*radius*.73f,
                cy + kotlin.math.sin(radians).toFloat()*radius*.73f + paint.textSize*.35f, paint)
            start += sweep
        }
        canvas.restore()
        repeat(24) { index ->
            val a = Math.toRadians(index*15.0)
            paint.color = if (index % 2 == 0) 0xFFFFF6CF.toInt() else 0xFFC68826.toInt()
            canvas.drawCircle(cx+kotlin.math.cos(a).toFloat()*radius*1.064f,
                cy+kotlin.math.sin(a).toFloat()*radius*1.064f, radius*.017f, paint)
        }
        paint.color = 0xFFFFE39B.toInt(); canvas.drawCircle(cx, cy, radius*.29f, paint)
        paint.shader = LinearGradient(cx, cy-radius*.25f, cx, cy+radius*.25f,
            0xFF2784C4.toInt(), 0xFF143D76.toInt(), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius*.25f, paint); paint.shader = null
        paint.color = 0xFFFFE6AD.toInt()
        canvas.drawOval(RectF(cx-radius*.12f, cy-radius*.02f, cx+radius*.12f, cy+radius*.14f), paint)
        for ((x,y) in listOf(-.13f to -.08f, -.045f to -.15f, .045f to -.15f, .13f to -.08f)) {
            canvas.drawOval(RectF(cx+radius*(x-.035f), cy+radius*(y-.045f), cx+radius*(x+.035f), cy+radius*(y+.045f)), paint)
        }
        // The pointer is a shape, so its appearance never depends on an emoji/font.
        val tip = cy-radius*.85f
        val pointer = Path().apply {
            moveTo(cx-radius*.105f, cy-radius*1.19f); lineTo(cx+radius*.105f, cy-radius*1.19f)
            lineTo(cx, tip); close()
        }
        paint.color = 0xFF88501D.toInt(); canvas.drawPath(pointer, paint)
        canvas.save(); canvas.scale(.75f, .78f, cx, cy-radius*1.12f)
        paint.color = 0xFFFFE8A2.toInt(); canvas.drawPath(pointer, paint); canvas.restore()
    }
    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }
}
