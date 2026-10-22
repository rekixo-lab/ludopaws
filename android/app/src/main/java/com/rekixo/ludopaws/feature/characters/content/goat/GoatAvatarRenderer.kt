package com.rekixo.ludopaws.feature.characters.content.goat

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.rekixo.ludopaws.feature.characters.rendering.*

/** Synchronous fallback until this character's shared 3D portrait is ready. */
internal object GoatAvatarRenderer : CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext, canvas: Canvas) = with(context) {
        val warmGray = 0xFFD8D2C7.toInt()
        val ivory = 0xFFF6E8CE.toInt()
        val horn = 0xFFC99958.toInt()

        fill.color = horn
        triangle(canvas, 31f, 31f, 35f, 9f, 43f, 31f)
        triangle(canvas, 57f, 31f, 65f, 9f, 69f, 31f)

        fill.color = warmGray
        canvas.drawOval(RectF(17f, 34f, 38f, 53f), fill)
        canvas.drawOval(RectF(62f, 34f, 83f, 53f), fill)

        fill.color = ivory
        canvas.drawOval(RectF(28f, 18f, 72f, 72f), fill)

        fill.color = 0xFFECC8A4.toInt()
        canvas.drawOval(RectF(34f, 49f, 66f, 70f), fill)

        drawEye(canvas, 40f, 42f)
        drawEye(canvas, 60f, 42f)

        fill.color = 0xFF6B5545.toInt()
        canvas.drawOval(RectF(42f, 56f, 47f, 60f), fill)
        canvas.drawOval(RectF(53f, 56f, 58f, 60f), fill)

        fill.color = 0xFFA8957F.toInt()
        val beard = Path().apply {
            moveTo(41f, 68f)
            lineTo(59f, 68f)
            lineTo(50f, 85f)
            close()
        }
        canvas.drawPath(beard, fill)

    }
}
