package com.rekixo.ludopaws.feature.characters.content.monkey

import android.graphics.Canvas
import android.graphics.RectF
import com.rekixo.ludopaws.feature.characters.rendering.*

internal object MonkeyAvatarRenderer : CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext, canvas: Canvas) = with(context) {
        fill.color = 0xFF613015.toInt()
        canvas.drawCircle(17f, 54f, 15f, fill)
        canvas.drawCircle(83f, 54f, 15f, fill)
        fill.color = 0xFFAB6440.toInt()
        canvas.drawCircle(17f, 54f, 9f, fill)
        canvas.drawCircle(83f, 54f, 9f, fill)
        fill.color = 0xFF613015.toInt()
        canvas.drawOval(RectF(20f, 16f, 80f, 87f), fill)
        canvas.drawOval(RectF(43f, 7f, 58f, 30f), fill)
        fill.color = 0xFFE0A866.toInt()
        canvas.drawOval(RectF(25f, 34f, 52f, 75f), fill)
        canvas.drawOval(RectF(48f, 34f, 75f, 75f), fill)
        canvas.drawOval(RectF(29f, 60f, 71f, 88f), fill)
        drawEye(canvas, 37f, 53f)
        drawEye(canvas, 63f, 53f)
        fill.color = 0xFF241109.toInt()
        canvas.drawOval(RectF(45f, 66f, 55f, 72f), fill)
        stroke.color = 0xFF241109.toInt()
        stroke.strokeWidth = 2f
        canvas.drawArc(RectF(36f, 69f, 64f, 82f), 10f, 160f, false, stroke)
    }
}
