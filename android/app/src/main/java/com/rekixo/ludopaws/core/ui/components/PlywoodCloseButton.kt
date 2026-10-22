package com.rekixo.ludopaws.core.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground

/**
 * Identical three-layer cherry-red X to the Rate & Review popup:
 * warm dark outline, vivid red centre, and subtle lighter specular line.
 */
internal class PlywoodCloseButton(
    context: Context,
    contentDescription: String,
    onClose: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF63351E.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 7.4f * density
    }
    private val red = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0463B.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 5.1f * density
    }
    private val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFC4A1.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.3f * density
    }

    init {
        this.contentDescription = contentDescription
        isClickable = true
        isFocusable = true
        minimumWidth = (42f * density).toInt()
        minimumHeight = (42f * density).toInt()
        background = recessedPlywoodBackground(context, outerCornerRadiusDp = 24f)
        elevation = 0f
        setOnClickListener { onClose() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width * .5f
        val cy = height * .5f
        val arm = 7.5f * density
        fun cross(paint: Paint) {
            canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, paint)
            canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, paint)
        }
        cross(outline)
        cross(red)
        cross(glint)
    }
}
