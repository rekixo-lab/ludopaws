package com.rekixo.ludopaws.feature.rewards

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
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
    private val wood = LinearGradient(-.90f, .60f, .88f, .94f,
        intArrayOf(0xFFD49859.toInt(), 0xFF8B4E33.toInt(), 0xFF3D2525.toInt()), null, Shader.TileMode.CLAMP)
    private val sectorLight = LinearGradient(-.8f, -.62f, .80f, .65f,
        intArrayOf(0x5EFFF3CE, 0x00FFF3CE, 0x47101C30),
        floatArrayOf(0f, .44f, 1f), Shader.TileMode.CLAMP)
    private val shadow = RadialGradient(0f, 0f, 1f, 0x68321914, 0x00321914, Shader.TileMode.CLAMP)
    private val enamel = RadialGradient(-.11f, -.12f, .34f,
        0xFF397C84.toInt(), 0xFF112F43.toInt(), Shader.TileMode.CLAMP)
    private val sideWood = rimBandPath(FRONT, -.23f)
    private val lowerBevel = rimBandPath(-.16f, -.21f)
    private val upperBevel = rimBandPath(FRONT, .035f)
    private val depthLight = LinearGradient(-1.08f, 0f, 1.08f, 0f,
        intArrayOf(0xFF543021.toInt(), 0xFF9A5833.toInt(), 0xFF492821.toInt()),
        floatArrayOf(0f, .25f, 1f), Shader.TileMode.CLAMP)
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
        val scale = minOf(width / 2.34f, height / 2.18f)
        if (scale <= 0f) return
        val saved = canvas.save()
        canvas.translate(width * .5f, height * .47f)
        canvas.scale(scale, scale)

        drawStand(canvas)
        drawRimDepth(canvas)
        disc(canvas, 1.06f, 0xFF9B5D30.toInt(), rim)
        disc(canvas, 1.005f, 0xFF593323.toInt())
        disc(canvas, .980f, 0xFFCC9858.toInt())
        disc(canvas, .952f, 0xFF39272D.toInt())
        var start = angle - 90f
        for (index in gems.indices) {
            val sweep = odds[index] * 3.6f
            sector(.933f, start + .42f, sweep - .84f)
            paint.color = colors[index]
            canvas.drawPath(path, paint)
            paint.shader = sectorLight
            canvas.drawPath(path, paint)
            paint.shader = null
            paint.color = 0x60F9E4AD
            paint.strokeWidth = .010f
            val radians = radians(start + .64f)
            val edgeX = cos(radians) * .928f
            val edgeY = sin(radians) * .928f
            canvas.drawLine(0f, projectY(0f, FRONT), projectX(edgeX, edgeY, FRONT), projectY(edgeY, FRONT), paint)

            val midpoint = radians(start + sweep * .5f)
            val labelX = cos(midpoint) * .73f
            val labelY = sin(midpoint) * .73f
            paint.typeface = typeface
            paint.textSize = .19f
            paint.textAlign = Paint.Align.CENTER
            val tx = projectX(labelX, labelY, FRONT)
            val ty = projectY(labelY, FRONT) - (paint.ascent() + paint.descent()) * .5f
            paint.color = if (index == 3) 0x80FFE6A9.toInt() else 0xA0221925.toInt()
            canvas.drawText(labels[index], tx, ty + .018f, paint)
            paint.color = if (index == 3) 0xFF352117.toInt() else 0xFFFFF6DB.toInt()
            canvas.drawText(labels[index], tx, ty, paint)
            start += sweep
        }

        for (index in 0 until 24) {
            val r = radians(index * 15f + angle)
            val px = cos(r) * 1.030f
            val py = sin(r) * 1.030f
            val shine = ((-.7f * cos(r) - .7f * sin(r) + 1f) * 42f).toInt().coerceIn(0, 85)
            paint.color = 0xFF724020.toInt()
            canvas.drawCircle(projectX(px, py, FRONT), projectY(py, FRONT) + .011f, .021f, paint)
            paint.color = Color.rgb(169 + shine, 101 + shine, 52 + shine)
            canvas.drawCircle(projectX(px, py, FRONT), projectY(py, FRONT), .017f, paint)
        }
        disc(canvas, .308f, 0xFF633527.toInt())
        disc(canvas, .282f, 0xFFAD7139.toInt(), rim)
        disc(canvas, .227f, 0xFFFFFFFF.toInt(), enamel)
        val paw = canvas.save()
        canvas.translate(0f, projectY(0f, FRONT))
        canvas.scale(1f, COS_TILT)
        canvas.rotate(angle)
        paint.color = 0xFFFFD88B.toInt()
        canvas.drawOval(-.104f, -.012f, .104f, .113f, paint)
        canvas.drawCircle(-.122f, -.061f, .032f, paint)
        canvas.drawCircle(-.046f, -.117f, .035f, paint)
        canvas.drawCircle(.046f, -.117f, .035f, paint)
        canvas.drawCircle(.122f, -.061f, .032f, paint)
        canvas.restoreToCount(paw)

        drawPointer(canvas)
        canvas.restoreToCount(saved)
    }

    private fun drawStand(canvas: Canvas) {
        val shade = canvas.save()
        canvas.translate(.03f, .91f)
        canvas.scale(.97f, .18f)
        paint.color = Color.WHITE
        paint.shader = shadow
        canvas.drawCircle(0f, 0f, 1f, paint)
        paint.shader = null
        canvas.restoreToCount(shade)

        paint.shader = wood
        canvas.drawRoundRect(-.88f, .66f, .88f, .94f, .07f, .07f, paint)
        paint.shader = null
        paint.color = 0xFF5D3326.toInt()
        canvas.drawRoundRect(-.86f, .86f, .86f, .94f, .035f, .035f, paint)
        paint.color = 0xFFD9A766.toInt()
        canvas.drawRoundRect(-.85f, .845f, .85f, .868f, .01f, .01f, paint)
        paint.strokeWidth = .013f
        paint.color = 0x4CF6BE7D
        canvas.drawLine(-.83f, .795f, .83f, .795f, paint)
    }

    private fun drawRimDepth(canvas: Canvas) {
        paint.color = Color.WHITE
        paint.shader = depthLight
        canvas.drawPath(sideWood, paint)
        paint.shader = rim
        canvas.drawPath(lowerBevel, paint)
        canvas.drawPath(upperBevel, paint)
        paint.shader = null
    }

    private fun rimBandPath(front: Float, rear: Float): Path = Path().apply {
        fun addPoint(index: Int, depth: Float, move: Boolean = false) {
            val direction = radians(index * (180f / 32f))
            val localY = sin(direction) * 1.06f
            val px = projectX(cos(direction) * 1.06f, localY, depth)
            val py = projectY(localY, depth)
            if (move) moveTo(px, py) else lineTo(px, py)
        }
        for (index in 0..32) addPoint(index, front, move = index == 0)
        for (index in 32 downTo 0) addPoint(index, rear)
        close()
    }

    private fun drawPointer(canvas: Canvas) {
        val tip = projectY(-.855f, FRONT)
        val top = tip - .27f
        path.rewind()
        path.moveTo(0f, top)
        path.lineTo(.129f, top + .075f)
        path.lineTo(0f, tip)
        path.lineTo(-.129f, top + .075f)
        path.close()
        paint.color = 0xFF623723.toInt()
        canvas.drawPath(path, paint)
        path.rewind()
        path.moveTo(0f, top + .025f)
        path.lineTo(0f, tip - .035f)
        path.lineTo(-.095f, top + .085f)
        path.close()
        paint.color = 0xFFFFE2A2.toInt()
        canvas.drawPath(path, paint)
        path.rewind()
        path.moveTo(0f, top + .025f)
        path.lineTo(.095f, top + .085f)
        path.lineTo(0f, tip - .035f)
        path.close()
        paint.color = 0xFFC5813F.toInt()
        canvas.drawPath(path, paint)
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
        path.moveTo(0f, projectY(0f, FRONT))
        val segments = (sweep / 5f).toInt().coerceAtLeast(2)
        for (index in 0..segments) circlePoint(start + sweep * index / segments, radius, FRONT)
        path.close()
    }

    private fun circlePoint(degrees: Float, radius: Float, depth: Float, move: Boolean = false) {
        val angle = radians(degrees)
        val py = sin(angle) * radius
        val px = projectX(cos(angle) * radius, py, depth)
        if (move) path.moveTo(px, projectY(py, depth)) else path.lineTo(px, projectY(py, depth))
    }

    private fun radians(degrees: Float) = degrees * .017453292f
    private fun perspective(y: Float, z: Float) = 5f / (5f - y * SIN_TILT - z * COS_TILT)
    private fun projectX(x: Float, y: Float, z: Float) = x * perspective(y, z)
    private fun projectY(y: Float, z: Float) = (y * COS_TILT - z * SIN_TILT) * perspective(y, z)

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) animator?.cancel()
    }

    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }

    private companion object {
        const val FRONT = .08f
        const val SIN_TILT = .65f
        const val COS_TILT = .7599f
    }
}
