package com.rekixo.ludopaws.feature.characters.content.pig

import android.graphics.Canvas
import android.graphics.RectF
import com.rekixo.ludopaws.feature.characters.rendering.*

internal object PigAvatarRenderer : CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext, canvas: Canvas) = with(context) {
        fill.color = 0xFFFFA3B0.toInt()
        triangle(canvas, 21f, 39f, 17f, 9f, 44f, 25f)
        triangle(canvas, 56f, 25f, 83f, 9f, 79f, 39f)
        fill.color = 0xFFE95977.toInt()
        triangle(canvas, 25f, 30f, 22f, 17f, 36f, 27f)
        triangle(canvas, 64f, 27f, 78f, 17f, 75f, 30f)
        fill.color = 0xFFFFB4C0.toInt()
        canvas.drawOval(RectF(16f, 23f, 84f, 86f), fill)
        drawEye(canvas, 35f, 47f)
        drawEye(canvas, 65f, 47f)
        fill.color = 0xFFFF8198.toInt()
        canvas.drawOval(RectF(31f, 57f, 69f, 78f), fill)
        fill.color = 0xFF703B4E.toInt()
        canvas.drawOval(RectF(39f, 63f, 45f, 72f), fill)
        canvas.drawOval(RectF(55f, 63f, 61f, 72f), fill)
        stroke.color = 0xFFB54A68.toInt()
        stroke.strokeWidth = 1.5f
        canvas.drawArc(RectF(41f, 74f, 59f, 82f), 10f, 160f, false, stroke)
    }
}
