package com.rekixo.ludopaws.ui.dialogs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import kotlin.math.min

internal enum class SettingsAudioIconKind {
    MUSIC,
    SOUND,
}

internal class SettingsAudioIconView(
    context: Context,
) : View(context) {
    var kind: SettingsAudioIconKind =
        SettingsAudioIconKind.MUSIC
        set(value) {
            field = value
            invalidate()
        }

    var isOn: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val fill =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val stroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
        AppFocusRing.apply(this, cornerRadiusDp = 999f, strokeWidthDp = 2.5f)
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val desired = settingsDp(context, 50)
        val width = resolveSize(desired, widthMeasureSpec)
        val height = resolveSize(desired, heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return

        val center = size / 2f
        // Shared Profile Classic / Rating Stars transparent navy and blue rim.
        fill.color = 0x33104483
        canvas.drawCircle(center, center, size * .45f, fill)
        stroke.color = 0xFF247FCB.toInt()
        stroke.strokeWidth = size * .045f
        canvas.drawCircle(center, center, size * .45f, stroke)

        stroke.color = 0xFF17385D.toInt()
        fill.color = 0xFF17385D.toInt()
        stroke.strokeWidth = size * .075f

        when (kind) {
            SettingsAudioIconKind.MUSIC -> drawMusic(canvas, size)
            SettingsAudioIconKind.SOUND -> drawSound(canvas, size)
        }

        if (!isOn) {
            stroke.color = 0xFFFF694A.toInt()
            stroke.strokeWidth = size * .095f
            canvas.drawLine(
                size * .23f,
                size * .23f,
                size * .77f,
                size * .77f,
                stroke,
            )
        }
    }

    private fun drawMusic(
        canvas: Canvas,
        size: Float,
    ) {
        stroke.strokeWidth = size * .065f
        canvas.drawLine(size * .48f, size * .29f, size * .48f, size * .64f, stroke)
        canvas.drawLine(size * .48f, size * .29f, size * .70f, size * .24f, stroke)
        canvas.drawLine(size * .70f, size * .24f, size * .70f, size * .57f, stroke)
        canvas.drawCircle(size * .39f, size * .67f, size * .105f, fill)
        canvas.drawCircle(size * .61f, size * .60f, size * .105f, fill)
    }

    private fun drawSound(
        canvas: Canvas,
        size: Float,
    ) {
        val speaker =
            Path().apply {
                moveTo(size * .27f, size * .43f)
                lineTo(size * .40f, size * .43f)
                lineTo(size * .56f, size * .30f)
                lineTo(size * .56f, size * .70f)
                lineTo(size * .40f, size * .57f)
                lineTo(size * .27f, size * .57f)
                close()
            }
        canvas.drawPath(speaker, fill)
        stroke.strokeWidth = size * .055f
        canvas.drawArc(
            RectF(size * .48f, size * .35f, size * .72f, size * .65f),
            -55f,
            110f,
            false,
            stroke,
        )
        canvas.drawArc(
            RectF(size * .43f, size * .27f, size * .82f, size * .73f),
            -48f,
            96f,
            false,
            stroke,
        )
    }
}
