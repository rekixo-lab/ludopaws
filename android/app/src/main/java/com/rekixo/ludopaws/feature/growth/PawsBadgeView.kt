package com.rekixo.ludopaws.feature.growth

import android.content.Context
import android.graphics.*
import android.view.View

/** Static, lightweight medal; completion is supplied by the real challenge state. */
internal class PawsBadgeView(context: Context, private val earned: Boolean) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        canvas.save(); canvas.translate((width-side)/2f, (height-side)/2f); canvas.scale(side/100f, side/100f)
        paint.color = if (earned) 0xFF2972A7.toInt() else 0xFF7E8996.toInt()
        val ribbons = Path().apply {
            moveTo(28f, 54f); lineTo(13f, 95f); lineTo(31f, 90f); lineTo(40f, 99f); lineTo(52f, 62f); close()
            moveTo(72f, 54f); lineTo(87f, 95f); lineTo(69f, 90f); lineTo(60f, 99f); lineTo(48f, 62f); close()
        }
        canvas.drawPath(ribbons, paint)
        paint.shader = LinearGradient(0f, 10f, 0f, 80f,
            if (earned) 0xFFFFEEA0.toInt() else 0xFFE9D9B7.toInt(), if (earned) 0xFFD6972D.toInt() else 0xFFB8A080.toInt(), Shader.TileMode.CLAMP)
        canvas.drawCircle(50f, 43f, 35f, paint); paint.shader = null
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = 0xFF9A6929.toInt()
        canvas.drawCircle(50f, 43f, 28f, paint); paint.style = Paint.Style.FILL
        paint.color = if (earned) 0xFF3C7E50.toInt() else 0xFF8A775D.toInt()
        canvas.drawOval(RectF(35f, 41f, 65f, 60f), paint)
        for ((x,y) in listOf(30f to 33f, 43f to 27f, 57f to 27f, 70f to 33f)) {
            canvas.drawOval(RectF(x-5f, y-7f, x+5f, y+7f), paint)
        }
        canvas.restore()
    }
}
