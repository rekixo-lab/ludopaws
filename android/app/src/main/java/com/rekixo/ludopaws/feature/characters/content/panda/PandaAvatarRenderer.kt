package com.rekixo.ludopaws.feature.characters.content.panda

import android.graphics.Canvas
import android.graphics.RectF
import com.rekixo.ludopaws.feature.characters.rendering.*

internal object PandaAvatarRenderer : CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext, canvas: Canvas) = with(context) {
        fill.color = 0xFF151922.toInt()
        canvas.drawCircle(26f, 25f, 14f, fill)
        canvas.drawCircle(74f, 25f, 14f, fill)
        fill.color = 0xFFF0EEE4.toInt()
        canvas.drawOval(RectF(15f, 22f, 85f, 91f), fill)
        fill.color = 0xFF151922.toInt()
        for (side in -1..1 step 2) {
            val x = 50f + side * 16f
            canvas.save()
            canvas.rotate(-side * 23f, x, 55f)
            canvas.drawOval(RectF(x - 12f, 41f, x + 12f, 70f), fill)
            canvas.restore()
        }
        drawEye(canvas, 34f, 54f)
        drawEye(canvas, 66f, 54f)
        fill.color = 0xFF151922.toInt()
        canvas.drawOval(RectF(43f, 66f, 57f, 75f), fill)
        stroke.color = 0xFF151922.toInt()
        stroke.strokeWidth = 2f
        canvas.drawLine(50f, 74f, 50f, 79f, stroke)
        canvas.drawArc(RectF(40f, 73f, 50f, 84f), 0f, 130f, false, stroke)
        canvas.drawArc(RectF(50f, 73f, 60f, 84f), 50f, 130f, false, stroke)
    }
}
