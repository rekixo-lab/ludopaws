package com.rekixo.ludopaws.feature.rewards

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** The lid rotates around its rear hinge. Gems rise only after a verified claim. */
internal class DailyGiftChestView(context: Context, opened: Boolean) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var progress = if (opened) 1f else 0f
    private var animator: ValueAnimator? = null

    fun open(animated: Boolean, done: () -> Unit) {
        animator?.cancel()
        if (!animated) { progress = 1f; invalidate(); done(); return }
        val running = ValueAnimator.ofFloat(progress, 1f).apply {
            duration = 640L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== animation) return
                    animator = null
                    done()
                }

                override fun onAnimationCancel(animation: Animator) {
                    if (animator === animation) animator = null
                }
            })
        }
        animator = running
        running.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 3.05f, height / 2.65f)
        if (scale <= 0f) return
        val saved = canvas.save()
        canvas.translate(width * .49f, height * .76f)
        canvas.scale(scale, scale)
        paint.color = 0x483A2420
        canvas.drawOval(-1.27f, .07f, 1.36f, .38f, paint)

        val hingeZ = -.56f
        val hingeY = .57f
        val angle = progress * 1.69f
        val tipY = hingeY + sin(angle) * 1.12f
        val tipZ = hingeZ + cos(angle) * 1.12f
        val lidHeight = cos(angle) * .13f
        val lidDepth = -sin(angle) * .13f

        quad(canvas, 0xFF3C241B.toInt(), -1f,.55f,-.55f, 1f,.55f,-.55f, 1f,.55f,.55f, -1f,.55f,.55f)
        quad(canvas, 0xFF57301E.toInt(),
            1f,tipY,tipZ, 1f,hingeY,hingeZ, 1f,hingeY+lidHeight,hingeZ+lidDepth, 1f,tipY+lidHeight,tipZ+lidDepth)
        quad(canvas, 0xFF9B6037.toInt(),
            -1f,hingeY+lidHeight,hingeZ+lidDepth, 1f,hingeY+lidHeight,hingeZ+lidDepth,
            1f,tipY+lidHeight,tipZ+lidDepth, -1f,tipY+lidHeight,tipZ+lidDepth)
        // The two brass bands follow the lid rather than floating above the chest.
        for (band in brassBands) {
            quad(canvas, 0xFFF0BC65.toInt(),
                band-.064f,hingeY+lidHeight,hingeZ+lidDepth, band+.064f,hingeY+lidHeight,hingeZ+lidDepth,
                band+.064f,tipY+lidHeight,tipZ+lidDepth, band-.064f,tipY+lidHeight,tipZ+lidDepth)
        }
        quad(canvas, 0xFFD2974E.toInt(),
            -1f,tipY,tipZ, 1f,tipY,tipZ, 1f,tipY+lidHeight,tipZ+lidDepth, -1f,tipY+lidHeight,tipZ+lidDepth)

        val lift = ((progress - .15f) / .85f).coerceIn(0f, 1f)
        if (lift > 0f) {
            jewel(canvas, -.50f, .50f + lift * .58f, -.01f, .17f * lift)
            jewel(canvas, .02f, .50f + lift * .92f, -.05f, .22f * lift)
            jewel(canvas, .52f, .50f + lift * .66f, .06f, .18f * lift)
        }
        quad(canvas, 0xFF714026.toInt(), 1f,0f,.55f, 1f,0f,-.55f, 1f,.57f,-.55f, 1f,.57f,.55f)
        quad(canvas, 0xFFA96B3C.toInt(), -1f,0f,.55f, 1f,0f,.55f, 1f,.57f,.55f, -1f,.57f,.55f)
        quad(canvas, 0xFFE3AA58.toInt(), -1f,.49f,.56f, 1f,.49f,.56f, 1f,.57f,.56f, -1f,.57f,.56f)
        quad(canvas, 0xFF694021.toInt(), -1f,0f,.56f, 1f,0f,.56f, 1f,.077f,.56f, -1f,.077f,.56f)
        for (band in brassBands) {
            quad(canvas, 0xFFDBA359.toInt(),
                band-.064f,.04f,.566f, band+.064f,.04f,.566f,
                band+.064f,.54f,.566f, band-.064f,.54f,.566f)
        }
        quad(canvas, 0xFFFFCD76.toInt(), -.16f,.20f,.573f, .16f,.20f,.573f, .16f,.42f,.573f, -.16f,.42f,.573f)
        paint.color = 0xFF5A311C.toInt()
        canvas.drawCircle(x(0f, .58f), y(0f, .31f, .58f), .036f, paint)
        canvas.restoreToCount(saved)
    }

    private fun jewel(canvas: Canvas, x: Float, y: Float, z: Float, radius: Float) {
        val top = y + radius * .54f
        val bottom = y - radius * .86f
        triangle(canvas, 0xFFB5F5FF.toInt(), x-radius,y,z, x,y+radius,z, x+radius,y,z)
        triangle(canvas, 0xFF4ABDDC.toInt(), x-radius,y,z, x,top,z+radius*.65f, x,bottom,z)
        triangle(canvas, 0xFF1587BE.toInt(), x+radius,y,z, x,top,z+radius*.65f, x,bottom,z)
        triangle(canvas, 0xFFEDFDFF.toInt(), x-radius,y,z, x,y+radius,z, x,top,z+radius*.65f)
    }

    private fun triangle(canvas: Canvas, color: Int,
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float) {
        path.rewind()
        path.moveTo(x(ax, az), y(ax, ay, az))
        path.lineTo(x(bx, bz), y(bx, by, bz))
        path.lineTo(x(cx, cz), y(cx, cy, cz))
        fill(canvas, color)
    }

    private fun quad(canvas: Canvas, color: Int,
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float) {
        path.rewind()
        path.moveTo(x(ax, az), y(ax, ay, az))
        path.lineTo(x(bx, bz), y(bx, by, bz))
        path.lineTo(x(cx, cz), y(cx, cy, cz))
        path.lineTo(x(dx, dz), y(dx, dy, dz))
        fill(canvas, color)
    }

    private fun fill(canvas: Canvas, color: Int) {
        path.close()
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun x(x: Float, z: Float) = x * .92f - z * .36f
    private fun y(x: Float, y: Float, z: Float) = x * .135f - y + z * .29f

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) animator?.cancel()
    }

    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }

    private companion object {
        val brassBands = floatArrayOf(-.70f, .70f)
    }
}
