package com.rekixo.ludopaws.feature.characters.content.horse

import android.graphics.Canvas
import android.graphics.RectF
import com.rekixo.ludopaws.feature.characters.rendering.*

internal object HorseAvatarRenderer : CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext, canvas: Canvas) = with(context) {
        fill.color = 0xFFA64A21.toInt()
        triangle(canvas, 27f, 35f, 25f, 6f, 42f, 28f)
        triangle(canvas, 58f, 28f, 75f, 6f, 73f, 35f)
        canvas.drawOval(RectF(25f, 23f, 75f, 81f), fill)
        fill.color = 0xFF382017.toInt()
        canvas.drawOval(RectF(42f, 16f, 60f, 40f), fill)
        fill.color = 0xFFFAF0D4.toInt()
        canvas.drawOval(RectF(46f, 34f, 54f, 66f), fill)
        drawEye(canvas, 37f, 46f)
        drawEye(canvas, 63f, 46f)
        fill.color = 0xFF654335.toInt()
        canvas.drawOval(RectF(26f, 61f, 74f, 87f), fill)
        fill.color = 0xFFFAF0D4.toInt()
        canvas.drawOval(RectF(45f, 63f, 55f, 75f), fill)
        fill.color = 0xFF241C18.toInt()
        canvas.drawOval(RectF(33f, 70f, 41f, 75f), fill)
        canvas.drawOval(RectF(59f, 70f, 67f, 75f), fill)
        stroke.color = 0xFF241C18.toInt()
        stroke.strokeWidth = 1.5f
        canvas.drawArc(RectF(38f, 76f, 62f, 83f), 10f, 160f, false, stroke)
    }
}
