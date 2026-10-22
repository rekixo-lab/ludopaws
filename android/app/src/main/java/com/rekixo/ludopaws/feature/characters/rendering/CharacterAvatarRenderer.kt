package com.rekixo.ludopaws.feature.characters.rendering

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/** Reused per avatar view; no Paint/cache state lives in singleton content renderers. */
internal class CharacterAvatarContext(val fill: Paint, val stroke: Paint) {
    fun drawEye(
        canvas: Canvas,
        x: Float,
        y: Float,
    ) {
        fill.color = 0xFF22252A.toInt()
        canvas.drawCircle(x, y, 5.5f, fill)
        fill.color = Color.WHITE
        canvas.drawCircle(x - 1.8f, y - 1.8f, 1.7f, fill)
    }

    fun triangle(
        canvas: Canvas,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
    ) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
            lineTo(x3, y3)
            close()
        }
        canvas.drawPath(path, fill)
    }
}

internal interface CharacterAvatarRenderer {
    fun draw(context: CharacterAvatarContext, canvas: Canvas)
}
