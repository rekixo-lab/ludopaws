package com.rekixo.ludopaws.feature.boards.content.dawn

import android.graphics.Canvas
import android.graphics.Paint
import com.rekixo.ludopaws.feature.boards.content.classic.ClassicBoardRenderer
import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.boards.rendering.BoardRenderer

/** Static decorative finish with exact classic team colors, geometry and hit targets. */
internal class MeadowGroveBoardRenderer : BoardRenderer {
    private val classic = ClassicBoardRenderer()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun prepare(onReady: () -> Unit) = classic.prepare(onReady)
    override fun draw(canvas: Canvas, size: Float, density: Float, definition: BoardDefinition) {
        classic.draw(canvas, size, density, definition)
        val dark = definition.id == "board_moonlit_grove"
        paint.color = if (dark) 0x668AC9D6 else 0x66D99945
        paint.style = Paint.Style.STROKE; paint.strokeWidth = density.coerceAtLeast(1f)
        val inset = size * .014f
        canvas.drawRoundRect(inset, inset, size-inset, size-inset, size*.025f, size*.025f, paint)
        paint.style = Paint.Style.FILL
        for (i in 1..9) {
            val x = size * (i / 10f)
            canvas.drawCircle(x, size * .025f, size * (if (dark) .003f else .004f), paint)
            canvas.drawCircle(size-x, size * .975f, size * .003f, paint)
        }
    }
}
