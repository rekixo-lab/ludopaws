package com.rekixo.ludopaws.feature.characters.content.cock

import android.graphics.Canvas
import android.graphics.RectF
import com.rekixo.ludopaws.feature.characters.rendering.*

internal object CockAvatarRenderer : CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext, canvas: Canvas) = with(context) {
        fill.color = 0xFFD41818.toInt()
        canvas.drawOval(RectF(41f, 5f, 53f, 29f), fill)
        canvas.drawOval(RectF(49f, 2f, 61f, 28f), fill)
        canvas.drawOval(RectF(57f, 9f, 68f, 31f), fill)
        fill.color = 0xFFFFBA36.toInt()
        canvas.drawOval(RectF(24f, 23f, 76f, 75f), fill)
        fill.color = 0xFFD41818.toInt()
        canvas.drawOval(RectF(40f, 65f, 49f, 85f), fill)
        canvas.drawOval(RectF(51f, 65f, 60f, 85f), fill)
        drawEye(canvas, 38f, 45f)
        drawEye(canvas, 62f, 45f)
        fill.color = 0xFFF18A24.toInt()
        triangle(canvas, 39f, 56f, 61f, 56f, 50f, 72f)
        fill.color = 0xFF08765E.toInt()
        canvas.drawArc(RectF(31f, 77f, 69f, 98f), 180f, 180f, true, fill)
    }
}
