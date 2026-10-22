package com.rekixo.ludopaws.feature.boards.rendering

import android.graphics.Canvas
import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition

/** Draws visual finish only; footprint/rank overlays and input stay with the board host. */
internal interface BoardRenderer {
    fun draw(canvas: Canvas, size: Float, density: Float, definition: BoardDefinition)
}
