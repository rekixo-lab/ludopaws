package com.rekixo.ludopaws.feature.characters.content.dog

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.rekixo.ludopaws.feature.characters.rendering.*

/** Synchronous fallback until this character's shared 3D portrait is ready. */
internal object DogAvatarRenderer : CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext, canvas: Canvas) = with(context) {
        val tan = 0xFFD89558.toInt()
        val dark = 0xFF8B5535.toInt()
        val cream = 0xFFFFE4BB.toInt()

        fill.color = dark
        canvas.drawOval(RectF(18f, 29f, 36f, 66f), fill)
        canvas.drawOval(RectF(64f, 29f, 82f, 66f), fill)

        fill.color = tan
        canvas.drawOval(RectF(25f, 20f, 75f, 73f), fill)

        fill.color = cream
        canvas.drawOval(RectF(33f, 48f, 67f, 72f), fill)

        drawEye(canvas, 40f, 43f)
        drawEye(canvas, 60f, 43f)

        fill.color = 0xFF31251F.toInt()
        canvas.drawOval(RectF(45f, 54f, 55f, 62f), fill)

        stroke.color = 0xFF5B3A2A.toInt()
        stroke.strokeWidth = 2.4f
        canvas.drawLine(50f, 62f, 50f, 66f, stroke)
        canvas.drawArc(RectF(42f, 61f, 50f, 69f), 15f, 70f, false, stroke)
        canvas.drawArc(RectF(50f, 61f, 58f, 69f), 95f, 70f, false, stroke)


    }
}
