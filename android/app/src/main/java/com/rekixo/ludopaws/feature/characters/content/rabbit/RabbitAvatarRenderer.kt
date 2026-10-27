package com.rekixo.ludopaws.feature.characters.content.rabbit

import android.graphics.Canvas
import android.graphics.RectF
import com.rekixo.ludopaws.feature.characters.rendering.*

internal object RabbitAvatarRenderer : CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext, canvas: Canvas) = with(context) {
        fill.color = 0xFFE8DBC2.toInt()
        canvas.drawOval(RectF(23f, 2f, 42f, 54f), fill)
        canvas.drawOval(RectF(58f, 2f, 77f, 54f), fill)
        fill.color = 0xFFD9959D.toInt()
        canvas.drawOval(RectF(29f, 9f, 37f, 44f), fill)
        canvas.drawOval(RectF(63f, 9f, 71f, 44f), fill)
        fill.color = 0xFFE8DBC2.toInt()
        canvas.drawOval(RectF(20f, 38f, 80f, 91f), fill)
        drawEye(canvas, 36f, 59f)
        drawEye(canvas, 64f, 59f)
        fill.color = 0xFFFFF7E8.toInt()
        canvas.drawOval(RectF(32f, 69f, 50f, 83f), fill)
        canvas.drawOval(RectF(50f, 69f, 68f, 83f), fill)
        fill.color = 0xFFBC5C6B.toInt()
        triangle(canvas, 44f, 70f, 56f, 70f, 50f, 77f)
        fill.color = 0xFFFFFFFF.toInt()
        canvas.drawRoundRect(RectF(45f, 81f, 49f, 88f), 1f, 1f, fill)
        canvas.drawRoundRect(RectF(51f, 81f, 55f, 88f), 1f, 1f, fill)
        stroke.color = 0xFF75695E.toInt()
        stroke.strokeWidth = 1f
        for (side in -1..1 step 2) {
            for (whiskerIndex in -1..1) {
                canvas.drawLine(50f + side * 14f, 76f + whiskerIndex * 3f,
                    50f + side * 36f, 76f + whiskerIndex * 7f, stroke)
            }
        }
    }
}
