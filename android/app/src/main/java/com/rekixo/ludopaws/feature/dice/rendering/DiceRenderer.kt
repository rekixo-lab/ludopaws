package com.rekixo.ludopaws.feature.dice.rendering

import android.graphics.Canvas
import android.graphics.RectF
import com.rekixo.ludopaws.feature.dice.domain.DicePalette

internal data class DiceRenderFrame(
    var face: Int, var rolling: Boolean, var settling: Boolean,
    var attentionPulsing: Boolean, var borderPulse: Float, var hasTurnDeadline: Boolean,
)

/** Appearance receives a resolved face; it cannot select or alter the dice outcome. */
internal interface DiceRenderer {
    fun draw(canvas: Canvas, size: Float, palette: DicePalette, frame: DiceRenderFrame): RectF
}
