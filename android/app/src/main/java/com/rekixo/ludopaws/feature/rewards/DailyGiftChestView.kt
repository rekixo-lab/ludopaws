package com.rekixo.ludopaws.feature.rewards

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** The lid rotates around its rear hinge. Gems rise only after a verified claim. */
internal class DailyGiftChestView(context: Context, opened: Boolean) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val lidY = FloatArray(13)
    private val lidZ = FloatArray(13)
    private val wood = LinearGradient(-.9f, -.63f, .92f, .24f,
        intArrayOf(0xFFD49859.toInt(), 0xFF8B4B2B.toInt(), 0xFF593322.toInt()),
        null, Shader.TileMode.CLAMP)
    private val brass = LinearGradient(0f, -.67f, 0f, .17f,
        intArrayOf(0xFFFFE9A6.toInt(), 0xFFCA8845.toInt(), 0xFFFFD58B.toInt(), 0xFF905326.toInt()),
        floatArrayOf(0f, .33f, .61f, 1f), Shader.TileMode.CLAMP)
    private val ground = RadialGradient(0f, 0f, 1f, 0x74321914, 0x00321914, Shader.TileMode.CLAMP)
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
        val scale = minOf(width / 2.66f, height / 2.76f)
        if (scale <= 0f) return
        val saved = canvas.save()
        canvas.translate(width * .49f, height * .77f)
        canvas.scale(scale, scale)
        val shadow = canvas.save()
        canvas.translate(.04f, .18f)
        canvas.scale(1.30f, .28f)
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = ground
        canvas.drawCircle(0f, 0f, 1f, paint)
        paint.shader = null
        canvas.restoreToCount(shadow)

        quad(canvas, 0xFF231B21.toInt(), -1f,.55f,-.55f, 1f,.55f,-.55f, 1f,.55f,.55f, -1f,.55f,.55f)
        quad(canvas, 0xFF50342B.toInt(), -.86f,.52f,-.46f, .86f,.52f,-.46f, .71f,.30f,.36f, -.71f,.30f,.36f)
        drawLid(canvas)
        val lift = ((progress - .16f) / .84f).coerceIn(0f, 1f)
        if (lift > 0f) {
            jewel(canvas, -.49f, .44f + lift * .52f, -.02f, .18f * lift, .2f + lift * 1.5f)
            jewel(canvas, .00f, .45f + lift * .87f, .02f, .24f * lift, .7f + lift * 2f)
            jewel(canvas, .47f, .43f + lift * .62f, .07f, .17f * lift, .3f - lift * 1.8f)
        }
        drawBase(canvas)
        canvas.restoreToCount(saved)
    }

    private fun drawLid(canvas: Canvas) {
        val angle = progress * 1.54f
        val sine = sin(angle)
        val cosine = cos(angle)
        for (index in lidY.indices) {
            val t = index / 12f
            val rise = .065f + sin(t * 3.141593f) * .20f
            val depth = t * 1.10f
            lidY[index] = .575f + depth * sine + rise * cosine
            lidZ[index] = -.55f + depth * cosine - rise * sine
        }
        val endY = .575f + 1.10f * sine
        val endZ = -.55f + 1.10f * cosine
        path.rewind()
        point(1f, .575f, -.55f, true)
        for (index in lidY.indices) point(1f, lidY[index], lidZ[index])
        point(1f, endY, endZ)
        fill(canvas, 0xFF523222.toInt())

        val undersideVisible = projectY(0f,endY,endZ) < projectY(0f,.575f,-.55f)
        if (undersideVisible) {
            // A raised lid shows the lined underside and a brass frame.
            quad(canvas, 0xFF9B6137.toInt(),
                -1f,.575f,-.55f, 1f,.575f,-.55f, 1f,endY,endZ, -1f,endY,endZ)
            val innerY = endY - sine * .12f
            val innerZ = endZ - cosine * .12f
            quad(canvas, 0xFF3A2827.toInt(),
                -.86f,.575f+sine*.11f,-.55f+cosine*.11f, .86f,.575f+sine*.11f,-.55f+cosine*.11f,
                .86f,innerY,innerZ, -.86f,innerY,innerZ)
            paint.strokeWidth = .018f
            paint.color = 0x7BCF9B65
            for (seam in lidSeams) {
                val y = .575f + sine * seam
                val z = -.55f + cosine * seam
                canvas.drawLine(projectX(-.85f,z), projectY(-.85f,y,z), projectX(.85f,z), projectY(.85f,y,z), paint)
            }
        }
        for (index in 0 until lidY.lastIndex) {
            val next = index + 1
            val highlight = sin(index * .27f + angle * 1.5f) * .5f + .5f
            val shade = (highlight * 51f).toInt()
            quad(canvas, android.graphics.Color.rgb(101 + shade, 52 + shade / 2, 31 + shade / 4),
                -1f,lidY[index],lidZ[index], 1f,lidY[index],lidZ[index],
                1f,lidY[next],lidZ[next], -1f,lidY[next],lidZ[next], frontOnly = true)
            for (band in brassBands) {
                quad(canvas, android.graphics.Color.rgb(157 + shade, 93 + shade, 43 + shade / 2),
                    band-.07f,lidY[index],lidZ[index], band+.07f,lidY[index],lidZ[index],
                    band+.07f,lidY[next],lidZ[next], band-.07f,lidY[next],lidZ[next], frontOnly = true)
            }
        }
        quad(canvas, 0xFFDAA05B.toInt(), -1f,endY,endZ, 1f,endY,endZ,
            1f,lidY.last(),lidZ.last(), -1f,lidY.last(),lidZ.last())
    }

    private fun drawBase(canvas: Canvas) {
        quad(canvas, 0xFF633F29.toInt(), 1f,0f,.55f, 1f,0f,-.55f, 1f,.57f,-.55f, 1f,.57f,.55f)
        quad(canvas, 0xFFFFFFFF.toInt(), -1f,0f,.55f, 1f,0f,.55f, 1f,.57f,.55f, -1f,.57f,.55f, shader = wood)
        paint.strokeWidth = .011f
        for (line in 0 until 9) {
            val y = .082f + line * .047f
            paint.color = if (line % 3 == 0) 0x46904D2C else 0x31F9C287
            path.rewind()
            point(-.98f, y, .553f, true)
            path.quadTo(projectX(-.36f,.553f), projectY(-.36f,y+.03f,.553f), projectX(.14f,.553f), projectY(.14f,y,.553f))
            path.quadTo(projectX(.62f,.553f), projectY(.62f,y-.028f,.553f), projectX(.98f,.553f), projectY(.98f,y,.553f))
            paint.style = Paint.Style.STROKE
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
        }
        quad(canvas, 0xFFFFFFFF.toInt(), -1f,.495f,.56f, 1f,.495f,.56f, 1f,.57f,.56f, -1f,.57f,.56f, shader = brass)
        quad(canvas, 0xFF543022.toInt(), -1f,0f,.56f, 1f,0f,.56f, 1f,.065f,.56f, -1f,.065f,.56f)
        for (band in brassBands) {
            quad(canvas, 0xFFFFFFFF.toInt(),
                band-.07f,.04f,.566f, band+.07f,.04f,.566f,
                band+.07f,.54f,.566f, band-.07f,.54f,.566f, shader = brass)
            for (height in rivetHeights) {
                paint.color = 0xFF814823.toInt()
                canvas.drawCircle(projectX(band,.57f), projectY(band,height,.57f), .030f, paint)
                paint.color = 0xFFFFE2A0.toInt()
                canvas.drawCircle(projectX(band-.008f,.57f), projectY(band,height+.011f,.57f), .018f, paint)
            }
        }
        val lockX = projectX(0f,.57f)
        val lockY = projectY(0f,.32f,.57f)
        paint.color = 0xFF5D331F.toInt()
        canvas.drawOval(lockX-.17f, lockY-.14f, lockX+.17f, lockY+.16f, paint)
        paint.color = 0xFFEDB865.toInt()
        canvas.drawOval(lockX-.146f, lockY-.137f, lockX+.146f, lockY+.129f, paint)
        paint.color = 0xFF552D22.toInt()
        canvas.drawCircle(lockX, lockY-.020f, .032f, paint)
        canvas.drawRoundRect(lockX-.017f, lockY, lockX+.017f, lockY+.060f, .01f, .01f, paint)
    }

    private fun jewel(canvas: Canvas, cx: Float, cy: Float, cz: Float, radius: Float, turn: Float) {
        for (side in 0 until 8) {
            val a = side * .7853982f + turn
            val b = a + .7853982f
            val ax = cos(a) * radius
            val az = sin(a) * radius
            val bx = cos(b) * radius
            val bz = sin(b) * radius
            val crown = cy + radius * .49f
            quad(canvas, crystalFaces[side % crystalFaces.size],
                cx+ax,cy,cz+az, cx+bx,cy,cz+bz,
                cx+bx*.56f,crown,cz+bz*.56f, cx+ax*.56f,crown,cz+az*.56f, frontOnly = true)
            quad(canvas, crystalFaces[(side+2) % crystalFaces.size],
                cx+bx,cy,cz+bz, cx+ax,cy,cz+az,
                cx,cy-radius*.78f,cz, cx,cy-radius*.78f,cz, frontOnly = true)
        }
        path.rewind()
        for (index in 0 until 8) {
            val angle = index * .7853982f + turn
            point(cx + cos(angle) * radius * .56f, cy + radius * .49f,
                cz + sin(angle) * radius * .56f, index == 0)
        }
        fill(canvas, 0xFFC7F7FF.toInt())
    }

    private fun point(x: Float, y: Float, z: Float, move: Boolean = false) {
        if (move) path.moveTo(projectX(x,z), projectY(x,y,z)) else path.lineTo(projectX(x,z), projectY(x,y,z))
    }

    private fun quad(canvas: Canvas, color: Int,
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float,
        shader: Shader? = null, frontOnly: Boolean = false) {
        if (frontOnly) {
            val abX = projectX(bx,bz) - projectX(ax,az)
            val abY = projectY(bx,by,bz) - projectY(ax,ay,az)
            val acX = projectX(cx,cz) - projectX(ax,az)
            val acY = projectY(cx,cy,cz) - projectY(ax,ay,az)
            if (abX * acY - abY * acX <= 0f) return
        }
        path.rewind()
        point(ax,ay,az,true)
        point(bx,by,bz)
        point(cx,cy,cz)
        point(dx,dy,dz)
        fill(canvas, color, shader)
    }

    private fun fill(canvas: Canvas, color: Int, shader: Shader? = null) {
        path.close()
        paint.color = color
        paint.shader = shader
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    private fun projectX(x: Float, z: Float) = x * .92f - z * .36f
    private fun projectY(x: Float, y: Float, z: Float) = x * .135f - y + z * .29f

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) animator?.cancel()
    }

    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }

    private companion object {
        val brassBands = floatArrayOf(-.71f, .71f)
        val rivetHeights = floatArrayOf(.15f, .43f)
        val lidSeams = floatArrayOf(.30f, .50f, .71f, .91f)
        val crystalFaces = intArrayOf(0xFF7DE9FC.toInt(), 0xFF1B729E.toInt(), 0xFF43B4DD.toInt(), 0xFFD2F9FF.toInt())
    }
}
