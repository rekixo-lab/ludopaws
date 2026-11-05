package com.rekixo.ludopaws.feature.rewards

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Thick, tilted wheel. A server receipt determines the final sector. */
internal class RewardWheelView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
    private val gems = intArrayOf(1, 2, 3, 5)
    private val labels = arrayOf("1", "2", "3", "5")
    private val odds = intArrayOf(50, 30, 15, 5)
    private val colors = intArrayOf(0xFF28644C.toInt(), 0xFF204A83.toInt(), 0xFF643D89.toInt(), 0xFFD99131.toInt())
    private val rim = LinearGradient(-1f, -.72f, .85f, .65f,
        intArrayOf(0xFFFFE3A0.toInt(), 0xFFBD773A.toInt(), 0xFFFFDC7B.toInt(), 0xFF8D4D26.toInt()),
        floatArrayOf(0f, .43f, .65f, 1f), Shader.TileMode.CLAMP)
    private val wood = LinearGradient(-1f, .72f, 1f, 1.04f,
        0xFF9B623D.toInt(), 0xFF442621.toInt(), Shader.TileMode.CLAMP)
    private val sectorLight = LinearGradient(-.7f, -.6f, .66f, .66f,
        0x46FFF3BF, 0x38101C30, Shader.TileMode.CLAMP)
    private var angle = 0f
    private var animator: ValueAnimator? = null

    fun stopAt(prize: Int, animated: Boolean, done: () -> Unit) {
        val index = gems.indexOf(prize)
        if (index < 0) { done(); return }
        animator?.cancel()
        val midpoint = (0 until index).sumOf { odds[it] } * 3.6f + odds[index] * 1.8f
        val target = (360f - midpoint) % 360f
        if (!animated) { angle = target; invalidate(); done(); return }
        val end = angle + 720f + ((target - angle % 360f + 360f) % 360f)
        val running = ValueAnimator.ofFloat(angle, end).apply {
            duration = 1650L
            interpolator = android.animation.TimeInterpolator { it * it * (3f - 2f * it) }
            addUpdateListener { angle = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (animator !== animation) return
                    animator = null
                    angle = target
                    invalidate()
                    done()
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    if (animator === animation) animator = null
                }
            })
        }
        animator = running
        running.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 2.42f, height / 2.10f)
        if (scale <= 0f) return
        val saved = canvas.save()
        canvas.translate(width * .5f, height * .47f)
        canvas.scale(scale, scale)

        paint.color = 0x403B261F
        canvas.drawOval(-.94f, .73f, .94f, .98f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.shader = wood
        canvas.drawRoundRect(-.83f, .71f, .83f, .92f, .09f, .09f, paint)
        paint.shader = null
        paint.color = 0xFFD29A55.toInt()
        canvas.drawRoundRect(-.77f, .71f, .77f, .747f, .015f, .015f, paint)

        // Near edge of the 3D disc joins the front rim to the rear rim.
        for (segment in 0 until 24) {
            val a = segment * 7.5f
            val b = a + 7.5f
            path.rewind()
            circlePoint(a, 1.06f, FRONT, move = true)
            circlePoint(b, 1.06f, FRONT)
            circlePoint(b, 1.06f, -.23f)
            circlePoint(a, 1.06f, -.23f)
            path.close()
            val light = ((24 - segment) * 2).coerceAtMost(42)
            paint.color = android.graphics.Color.rgb(103 + light, 56 + light / 2, 30 + light / 3)
            canvas.drawPath(path, paint)
        }
        disc(canvas, 1.06f, 0xFF9B5D30.toInt(), rim)
        disc(canvas, .965f, 0xFF603D2F.toInt())
        var start = angle - 90f
        for (index in gems.indices) {
            val sweep = odds[index] * 3.6f
            sector(.936f, start, sweep)
            paint.color = colors[index]
            canvas.drawPath(path, paint)
            paint.shader = sectorLight
            canvas.drawPath(path, paint)
            paint.shader = null
            paint.color = 0x70F9E4AD
            paint.strokeWidth = .015f
            val radians = radians(start)
            val edgeX = cos(radians) * .936f
            val edgeY = sin(radians) * .936f
            canvas.drawLine(0f, y(0f, FRONT), x(edgeX, edgeY, FRONT), y(edgeY, FRONT), paint)

            val midpoint = radians(start + sweep * .5f)
            val labelX = cos(midpoint) * .73f
            val labelY = sin(midpoint) * .73f
            paint.typeface = typeface
            paint.textSize = .19f
            paint.textAlign = Paint.Align.CENTER
            paint.color = if (index == 3) 0xFF362216.toInt() else 0xFFFFF6DB.toInt()
            canvas.drawText(labels[index], x(labelX, labelY, FRONT),
                y(labelY, FRONT) - (paint.ascent() + paint.descent()) * .5f, paint)
            start += sweep
        }

        for (index in 0 until 24) {
            val r = radians(index * 15f + angle)
            val px = cos(r) * 1.005f
            val py = sin(r) * 1.005f
            paint.color = 0xFF764221.toInt()
            canvas.drawCircle(x(px, py, FRONT), y(py, FRONT) + .012f, .025f, paint)
            paint.color = 0xFFFFE2A2.toInt()
            canvas.drawCircle(x(px, py, FRONT), y(py, FRONT), .019f, paint)
        }
        disc(canvas, .282f, 0xFFAD7139.toInt(), rim)
        disc(canvas, .227f, 0xFF153E4C.toInt())
        val paw = canvas.save()
        canvas.translate(0f, y(0f, FRONT))
        canvas.scale(1f, COS_TILT)
        canvas.rotate(angle)
        paint.color = 0xFFFFD88B.toInt()
        canvas.drawOval(-.104f, -.012f, .104f, .113f, paint)
        canvas.drawCircle(-.122f, -.061f, .032f, paint)
        canvas.drawCircle(-.046f, -.117f, .035f, paint)
        canvas.drawCircle(.046f, -.117f, .035f, paint)
        canvas.drawCircle(.122f, -.061f, .032f, paint)
        canvas.restoreToCount(paw)

        val tip = y(-.84f, FRONT)
        path.rewind()
        path.moveTo(-.113f, tip - .25f)
        path.lineTo(.113f, tip - .25f)
        path.lineTo(0f, tip)
        path.close()
        paint.color = 0xFF8B4728.toInt()
        canvas.drawPath(path, paint)
        val pointer = canvas.save()
        canvas.scale(.72f, .76f, 0f, tip - .18f)
        paint.color = 0xFFFFE29A.toInt()
        canvas.drawPath(path, paint)
        canvas.restoreToCount(pointer)
        canvas.restoreToCount(saved)
    }

    private fun disc(canvas: Canvas, radius: Float, color: Int, shader: Shader? = null) {
        path.rewind()
        for (index in 0..64) circlePoint(index * (360f / 64f), radius, FRONT, move = index == 0)
        path.close()
        paint.color = color
        paint.shader = shader
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    private fun sector(radius: Float, start: Float, sweep: Float) {
        path.rewind()
        path.moveTo(0f, y(0f, FRONT))
        val segments = (sweep / 5f).toInt().coerceAtLeast(2)
        for (index in 0..segments) circlePoint(start + sweep * index / segments, radius, FRONT)
        path.close()
    }

    private fun circlePoint(degrees: Float, radius: Float, depth: Float, move: Boolean = false) {
        val angle = radians(degrees)
        val py = sin(angle) * radius
        val px = x(cos(angle) * radius, py, depth)
        if (move) path.moveTo(px, y(py, depth)) else path.lineTo(px, y(py, depth))
    }

    private fun radians(degrees: Float) = degrees * .017453292f
    private fun perspective(y: Float, z: Float) = 5f / (5f - y * SIN_TILT - z * COS_TILT)
    private fun x(x: Float, y: Float, z: Float) = x * perspective(y, z)
    private fun y(y: Float, z: Float) = (y * COS_TILT - z * SIN_TILT) * perspective(y, z)

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) animator?.cancel()
    }

    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }

    private companion object {
        const val FRONT = .08f
        const val SIN_TILT = .54f
        const val COS_TILT = .8417f
    }
}
