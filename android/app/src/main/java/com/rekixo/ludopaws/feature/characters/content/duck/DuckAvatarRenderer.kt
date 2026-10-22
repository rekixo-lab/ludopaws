package com.rekixo.ludopaws.feature.characters.content.duck

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.rekixo.ludopaws.feature.characters.rendering.*

/** Synchronous fallback until this character's shared 3D portrait is ready. */
internal object DuckAvatarRenderer : CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext, canvas: Canvas) = with(context) {
        val yellow = 0xFFFFD94D.toInt()
        val yellowDark = 0xFFF3BB2D.toInt()
        val orange = 0xFFF18A24.toInt()

        fill.color = yellowDark
        triangle(canvas, 43f, 23f, 49f, 8f, 54f, 24f)
        triangle(canvas, 49f, 23f, 58f, 11f, 59f, 28f)

        fill.color = yellow
        canvas.drawOval(RectF(23f, 18f, 77f, 72f), fill)

        drawEye(canvas, 39f, 42f)
        drawEye(canvas, 61f, 42f)

        fill.color = orange
        canvas.drawRoundRect(RectF(31f, 53f, 69f, 69f), 8f, 8f, fill)
        stroke.color = 0xFFD96D16.toInt()
        stroke.strokeWidth = 2f
        canvas.drawLine(35f, 61f, 65f, 61f, stroke)

        fill.color = 0xFF4DB6E9.toInt()
        canvas.drawArc(RectF(35f, 67f, 65f, 84f), 0f, 180f, true, fill)
    
    }
}
