package com.rekixo.ludopaws

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class OnlineLobbyArtView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style =
                Paint.Style.STROKE
        }

    override fun onDraw(
        canvas: Canvas,
    ) {
        super.onDraw(canvas)

        val w =
            width.toFloat()
        val h =
            height.toFloat()
        if (
            w <= 0f ||
            h <= 0f
        ) {
            return
        }

        // Unboxed Team Up artwork: keep the globe, shield, nodes and glow,
        // but draw no blue panel fill or border behind the components.
        val cx =
            w * .22f
        val cy =
            h * .50f
        val radius =
            min(
                w,
                h,
            ) * .27f

        paint.shader =
            RadialGradient(
                cx,
                cy,
                radius * 2.2f,
                intArrayOf(
                    0xAA00C8FF.toInt(),
                    0x220078FF,
                    Color.TRANSPARENT,
                ),
                null,
                Shader.TileMode.CLAMP,
            )
        canvas.drawCircle(
            cx,
            cy,
            radius * 2.1f,
            paint,
        )
        paint.shader =
            null

        paint.color =
            0xFF073B91.toInt()
        canvas.drawCircle(
            cx,
            cy,
            radius,
            paint,
        )

        stroke.color =
            0xFF9AF0FF.toInt()
        stroke.strokeWidth =
            dp(2f)
        canvas.drawCircle(
            cx,
            cy,
            radius,
            stroke,
        )
        canvas.drawOval(
            RectF(
                cx - radius * .48f,
                cy - radius,
                cx + radius * .48f,
                cy + radius,
            ),
            stroke,
        )
        canvas.drawOval(
            RectF(
                cx - radius,
                cy - radius * .42f,
                cx + radius,
                cy + radius * .42f,
            ),
            stroke,
        )

        drawNode(
            canvas,
            w * .08f,
            h * .24f,
            0xFF35C83D.toInt(),
        )
        drawNode(
            canvas,
            w * .36f,
            h * .22f,
            0xFFF22E35.toInt(),
        )
        drawNode(
            canvas,
            w * .08f,
            h * .76f,
            0xFF2A8CFF.toInt(),
        )
        drawNode(
            canvas,
            w * .37f,
            h * .76f,
            0xFFFFD324.toInt(),
        )

        stroke.color =
            0x665FEAFF
        stroke.strokeWidth =
            dp(1.2f)
        listOf(
            Pair(w * .08f, h * .24f),
            Pair(w * .36f, h * .22f),
            Pair(w * .08f, h * .76f),
            Pair(w * .37f, h * .76f),
        ).forEach {
                point ->
            canvas.drawLine(
                point.first,
                point.second,
                cx,
                cy,
                stroke,
            )
        }

        drawShield(
            canvas,
            cx,
            cy + radius * .10f,
            radius * .86f,
        )

        paint.color =
            0x221FD6FF
        val slash =
            Path().apply {
                moveTo(
                    w * .47f,
                    0f,
                )
                lineTo(
                    w * .59f,
                    0f,
                )
                lineTo(
                    w * .48f,
                    h,
                )
                lineTo(
                    w * .36f,
                    h,
                )
                close()
            }
        canvas.drawPath(
            slash,
            paint,
        )

        drawSpark(
            canvas,
            w * .88f,
            h * .21f,
            dp(7f),
        )
        drawSpark(
            canvas,
            w * .82f,
            h * .78f,
            dp(5f),
        )
    }

    private fun drawShield(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
    ) {
        val outer =
            Path().apply {
                moveTo(
                    cx,
                    cy - size * .60f,
                )
                lineTo(
                    cx + size * .50f,
                    cy - size * .36f,
                )
                lineTo(
                    cx + size * .40f,
                    cy + size * .25f,
                )
                quadTo(
                    cx,
                    cy + size * .70f,
                    cx,
                    cy + size * .70f,
                )
                quadTo(
                    cx,
                    cy + size * .70f,
                    cx - size * .40f,
                    cy + size * .25f,
                )
                lineTo(
                    cx - size * .50f,
                    cy - size * .36f,
                )
                close()
            }

        paint.color =
            0xFFFFC62E.toInt()
        canvas.drawPath(
            outer,
            paint,
        )

        val inner =
            Path().apply {
                moveTo(
                    cx,
                    cy - size * .45f,
                )
                lineTo(
                    cx + size * .36f,
                    cy - size * .28f,
                )
                lineTo(
                    cx + size * .28f,
                    cy + size * .17f,
                )
                quadTo(
                    cx,
                    cy + size * .51f,
                    cx,
                    cy + size * .51f,
                )
                quadTo(
                    cx,
                    cy + size * .51f,
                    cx - size * .28f,
                    cy + size * .17f,
                )
                lineTo(
                    cx - size * .36f,
                    cy - size * .28f,
                )
                close()
            }

        paint.color =
            0xFF0D7CE8.toInt()
        canvas.drawPath(
            inner,
            paint,
        )

        stroke.color =
            Color.WHITE
        stroke.strokeWidth =
            maxOf(
                dp(2f),
                size * .08f,
            )
        stroke.strokeCap =
            Paint.Cap.ROUND
        canvas.drawLine(
            cx - size * .18f,
            cy,
            cx - size * .03f,
            cy + size * .16f,
            stroke,
        )
        canvas.drawLine(
            cx - size * .03f,
            cy + size * .16f,
            cx + size * .24f,
            cy - size * .17f,
            stroke,
        )
        stroke.strokeCap =
            Paint.Cap.BUTT
    }

    private fun drawNode(
        canvas: Canvas,
        x: Float,
        y: Float,
        color: Int,
    ) {
        paint.color =
            Color.WHITE
        canvas.drawCircle(
            x,
            y,
            dp(9f),
            paint,
        )
        paint.color =
            color
        canvas.drawCircle(
            x,
            y,
            dp(6f),
            paint,
        )
    }

    private fun drawSpark(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
    ) {
        paint.color =
            0x9965E9FF.toInt()
        val path =
            Path().apply {
                moveTo(
                    cx,
                    cy - radius * 1.8f,
                )
                lineTo(
                    cx + radius * .33f,
                    cy - radius * .33f,
                )
                lineTo(
                    cx + radius * 1.8f,
                    cy,
                )
                lineTo(
                    cx + radius * .33f,
                    cy + radius * .33f,
                )
                lineTo(
                    cx,
                    cy + radius * 1.8f,
                )
                lineTo(
                    cx - radius * .33f,
                    cy + radius * .33f,
                )
                lineTo(
                    cx - radius * 1.8f,
                    cy,
                )
                lineTo(
                    cx - radius * .33f,
                    cy - radius * .33f,
                )
                close()
            }
        canvas.drawPath(
            path,
            paint,
        )
    }

    private fun dp(
        value: Float,
    ): Float =
        value *
            resources
                .displayMetrics
                .density
}
