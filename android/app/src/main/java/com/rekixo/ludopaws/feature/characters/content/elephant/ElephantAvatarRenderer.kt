package com.rekixo.ludopaws.feature.characters.content.elephant

import android.graphics.Canvas
import android.graphics.RectF
import com.rekixo.ludopaws.feature.characters.rendering.*

internal object ElephantAvatarRenderer : CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext, canvas: Canvas) = with(context) {
        fill.color = 0xFF8F9FAB.toInt()
        canvas.drawOval(RectF(5f, 22f, 40f, 75f), fill)
        canvas.drawOval(RectF(60f, 22f, 95f, 75f), fill)
        fill.color = 0xFFB39298.toInt()
        canvas.drawOval(RectF(11f, 29f, 34f, 68f), fill)
        canvas.drawOval(RectF(66f, 29f, 89f, 68f), fill)
        fill.color = 0xFFA0B0BA.toInt()
        canvas.drawOval(RectF(24f, 15f, 76f, 73f), fill)
        drawEye(canvas, 36f, 44f)
        drawEye(canvas, 64f, 44f)
        fill.color = 0xFFFAF0CF.toInt()
        triangle(canvas, 32f, 61f, 40f, 62f, 32f, 81f)
        triangle(canvas, 60f, 62f, 68f, 61f, 68f, 81f)
        stroke.color = 0xFF8F9FAB.toInt()
        stroke.strokeWidth = 15f
        stroke.strokeCap = android.graphics.Paint.Cap.ROUND
        canvas.drawLine(50f, 61f, 50f, 82f, stroke)
        canvas.drawArc(RectF(50f, 74f, 70f, 92f), 0f, 180f, false, stroke)
        stroke.strokeCap = android.graphics.Paint.Cap.BUTT
    }
}
