package com.rekixo.ludopaws

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.sqrt

/** A feathered wave in seat coordinates, independent of the board art's rotation. */
internal class TurnBoardReveal {
    private val boardMatrix = Matrix()
    private val edgeMatrix = Matrix()
    private val mask = LinearGradient(
        0f, 0f, 1f, 0f, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP,
    )
    private val incomingPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val highlight = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 1f, 0f, intArrayOf(0x00FFF1C4, 0x44FFF1C4, 0x00FFF1C4),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP,
        )
    }
    private var bitmapShader: BitmapShader? = null
    private var bitmapSize = 0

    fun setIncoming(bitmap: Bitmap) {
        bitmapShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        bitmapSize = bitmap.width
    }

    fun clear() {
        bitmapShader = null
        incomingPaint.shader = null
    }

    fun draw(
        canvas: Canvas,
        size: Float,
        rotation: Int,
        slot: OfflinePlayerLayout.Slot,
        fraction: Float,
    ) {
        val board = bitmapShader ?: return
        val fromLeft = slot == OfflinePlayerLayout.Slot.TOP_LEFT || slot == OfflinePlayerLayout.Slot.BOTTOM_LEFT
        val fromBottom = slot == OfflinePlayerLayout.Slot.BOTTOM_LEFT || slot == OfflinePlayerLayout.Slot.BOTTOM_RIGHT
        // The wave moves mostly vertically with a slight diagonal from the player's corner.
        var dx = if (fromLeft) .28f else -.28f
        var dy = if (fromBottom) -1f else 1f
        repeat(((rotation / 90) % 4 + 4) % 4) {
            val previousX = dx
            dx = dy
            dy = -previousX
        }
        val length = sqrt(dx * dx + dy * dy)
        dx /= length
        dy /= length

        val band = size * .075f
        val extent = (abs(dx) + abs(dy)) * size * .5f
        val front = fraction * (2f * extent + band) - extent - band
        val x = size * .5f + dx * front
        val y = size * .5f + dy * front
        edgeMatrix.setSinCos(dy, dx)
        edgeMatrix.preScale(band, band)
        edgeMatrix.postTranslate(x, y)
        mask.setLocalMatrix(edgeMatrix)
        highlight.shader?.setLocalMatrix(edgeMatrix)

        val scale = size / bitmapSize
        boardMatrix.setScale(scale, scale)
        board.setLocalMatrix(boardMatrix)
        // Older Android releases can retain the child shader's previous native matrix.
        incomingPaint.shader = ComposeShader(board, mask, PorterDuff.Mode.DST_IN)
        canvas.drawRect(0f, 0f, size, size, incomingPaint)
        highlight.alpha = (255f * (1f - abs(fraction * 2f - 1f))).toInt()
        canvas.drawRect(0f, 0f, size, size, highlight)
    }
}
