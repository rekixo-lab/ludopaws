package com.rekixo.ludopaws.feature.characters.content.giraffe

import android.graphics.Canvas
import android.graphics.RectF
import com.rekixo.ludopaws.feature.characters.rendering.CharacterAvatarContext
import com.rekixo.ludopaws.feature.characters.rendering.CharacterAvatarRenderer

internal object GiraffeAvatarRenderer : CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext, canvas: Canvas) = with(context) {
        fill.color = 0xFFF2AE4A.toInt()
        canvas.drawRoundRect(RectF(39f, 47f, 61f, 96f), 10f, 10f, fill)
        triangle(canvas, 32f, 44f, 13f, 30f, 36f, 31f)
        triangle(canvas, 68f, 44f, 87f, 30f, 64f, 31f)
        canvas.drawRoundRect(RectF(38f, 12f, 45f, 36f), 4f, 4f, fill)
        canvas.drawRoundRect(RectF(55f, 12f, 62f, 36f), 4f, 4f, fill)
        canvas.drawOval(RectF(28f, 25f, 72f, 68f), fill)
        fill.color = 0xFF965023.toInt()
        canvas.drawCircle(41f, 13f, 5.5f, fill)
        canvas.drawCircle(59f, 13f, 5.5f, fill)
        canvas.drawOval(RectF(41f, 69f, 49f, 78f), fill)
        canvas.drawOval(RectF(52f, 79f, 59f, 89f), fill)
        drawEye(canvas, 39f, 45f)
        drawEye(canvas, 61f, 45f)
        fill.color = 0xFFFFDE9D.toInt()
        canvas.drawOval(RectF(33f, 51f, 67f, 69f), fill)
        fill.color = 0xFF593720.toInt()
        canvas.drawCircle(44f, 58f, 1.6f, fill)
        canvas.drawCircle(56f, 58f, 1.6f, fill)
        stroke.color = 0xFF593720.toInt()
        stroke.strokeWidth = 1.4f
        canvas.drawArc(RectF(43f, 58f, 57f, 65f), 10f, 160f, false, stroke)
    }
}
