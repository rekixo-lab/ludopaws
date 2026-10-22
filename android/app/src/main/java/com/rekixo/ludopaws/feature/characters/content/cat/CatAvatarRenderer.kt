package com.rekixo.ludopaws.feature.characters.content.cat

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.rekixo.ludopaws.feature.characters.rendering.*

/** Synchronous fallback until this character's shared 3D portrait is ready. */
internal object CatAvatarRenderer : CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext, canvas: Canvas) = with(context) {
        val brown = 0xFFB97D4B.toInt()
        val darkBrown = 0xFF7C4B2E.toInt()
        val cream = 0xFFFFE5BD.toInt()
        val pink = 0xFFE98B93.toInt()

        fill.color = brown
        triangle(canvas, 25f, 38f, 30f, 11f, 45f, 30f)
        triangle(canvas, 55f, 30f, 70f, 11f, 75f, 38f)

        fill.color = pink
        triangle(canvas, 30f, 30f, 33f, 18f, 40f, 29f)
        triangle(canvas, 60f, 29f, 67f, 18f, 70f, 30f)

        fill.color = brown
        canvas.drawOval(RectF(24f, 22f, 76f, 74f), fill)

        fill.color = cream
        canvas.drawOval(RectF(33f, 49f, 67f, 72f), fill)

        stroke.color = darkBrown
        stroke.strokeWidth = 3f
        canvas.drawLine(45f, 27f, 43f, 37f, stroke)
        canvas.drawLine(50f, 25f, 50f, 36f, stroke)
        canvas.drawLine(55f, 27f, 57f, 37f, stroke)

        drawEye(canvas, 40f, 44f)
        drawEye(canvas, 60f, 44f)

        fill.color = pink
        triangle(canvas, 46f, 55f, 54f, 55f, 50f, 61f)

        stroke.color = darkBrown
        stroke.strokeWidth = 1.7f
        canvas.drawLine(34f, 58f, 22f, 55f, stroke)
        canvas.drawLine(34f, 62f, 21f, 63f, stroke)
        canvas.drawLine(66f, 58f, 78f, 55f, stroke)
        canvas.drawLine(66f, 62f, 79f, 63f, stroke)

        fill.color = 0xFF2688E8.toInt()
        canvas.drawRoundRect(RectF(31f, 70f, 69f, 77f), 3.5f, 3.5f, fill)
        fill.color = 0xFFFFCC42.toInt()
        canvas.drawCircle(50f, 78f, 5f, fill)

    }
}
