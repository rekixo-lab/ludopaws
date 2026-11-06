package com.rekixo.ludopaws.feature.growth

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/** A raised medal drawn in the same coordinates at every screen density. */
internal class PawsBadgeView(context: Context, earned: Boolean) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ribbon = Path().apply {
        moveTo(26f, 53f)
        lineTo(14f, 92f)
        lineTo(29f, 87f)
        lineTo(39f, 96f)
        lineTo(50f, 65f)
        lineTo(61f, 96f)
        lineTo(71f, 87f)
        lineTo(86f, 92f)
        lineTo(74f, 53f)
        close()
    }
    private val paw = Path().apply {
        addOval(36f, 40f, 64f, 60f, Path.Direction.CW)
        addOval(28f, 33f, 38f, 43f, Path.Direction.CW)
        addOval(39f, 26f, 49f, 38f, Path.Direction.CW)
        addOval(51f, 26f, 61f, 38f, Path.Direction.CW)
        addOval(62f, 33f, 72f, 43f, Path.Direction.CW)
    }
    private val ribbonShade = gradient(48f, 96f,
        if (earned) 0xFF55A799.toInt() else 0xFF76968C.toInt(), 0xFF153E39.toInt())
    private val rim = gradient(9f, 78f,
        if (earned) 0xFFFFEE9D.toInt() else 0xFFE8DFCE.toInt(),
        if (earned) 0xFFB8752A.toInt() else 0xFF7D7363.toInt())
    private val face = gradient(17f, 70f,
        if (earned) 0xFF317F6D.toInt() else 0xFF546F66.toInt(), 0xFF142F2C.toInt())
    private val stamp = gradient(26f, 61f,
        if (earned) 0xFFFFF7C0.toInt() else 0xFFF0E8D8.toInt(),
        if (earned) 0xFFC98835.toInt() else 0xFFA89A81.toInt())
    private val bevelHighlight = RectF(19f, 13f, 81f, 75f)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat()
        if (side <= 0f) return
        val saved = canvas.save()
        canvas.translate((width - side) / 2f, (height - side) / 2f)
        canvas.scale(side / 100f, side / 100f)

        paint.color = 0x66301D10
        canvas.drawOval(20f, 76f, 82f, 92f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = ribbonShade
        canvas.drawPath(ribbon, paint)
        paint.shader = null

        // Offset the rim and center stamp to give both surfaces visible thickness.
        paint.color = 0xFF4F3828.toInt()
        canvas.drawCircle(50f, 48f, 35f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = rim
        canvas.drawCircle(50f, 44f, 35f, paint)
        paint.shader = null

        paint.color = 0xFF3E2E23.toInt()
        canvas.drawCircle(50f, 45f, 28f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = face
        canvas.drawCircle(50f, 44f, 26f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0xAAFFF9DE.toInt()
        canvas.drawArc(bevelHighlight, 203f, 93f, false, paint)
        paint.style = Paint.Style.FILL

        canvas.translate(0f, 2.5f)
        paint.color = 0xFF172421.toInt()
        canvas.drawPath(paw, paint)
        canvas.translate(0f, -2.5f)
        paint.color = 0xFFFFFFFF.toInt()
        paint.shader = stamp
        canvas.drawPath(paw, paint)
        paint.shader = null
        canvas.restoreToCount(saved)
    }

    private fun gradient(top: Float, bottom: Float, light: Int, dark: Int) =
        LinearGradient(0f, top, 0f, bottom, light, dark, Shader.TileMode.CLAMP)
}
