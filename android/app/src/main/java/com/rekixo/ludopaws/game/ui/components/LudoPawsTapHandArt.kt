package com.rekixo.ludopaws

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/** Shared, shaded tapping-hand art for legal-pawn and Save-button hints. */
internal class LudoPawsTapHandArt(context: Context) {
    private val density = context.resources.displayMetrics.density
    // Unit-sized hand paths/materials are reused throughout the pulse animation.
    private val handPath = Path().apply {
        moveTo(0f, .38f)
        lineTo(-.16f, .12f)
        quadTo(-.22f, -.02f, -.08f, 0f)
        lineTo(0f, .10f)
        lineTo(0f, -.33f)
        cubicTo(.01f, -.44f, .14f, -.44f, .15f, -.30f)
        lineTo(.15f, -.02f)
        quadTo(.21f, -.05f, .23f, .015f)
        quadTo(.30f, -.015f, .31f, .065f)
        quadTo(.39f, .04f, .39f, .14f)
        lineTo(.33f, .38f)
        close()
    }
    private val handFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-.16f, -.36f, .36f, .36f,
            intArrayOf(0xFFFFE6C7.toInt(), 0xFFF4BC86.toInt(), 0xFFC67F50.toInt()),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
    }
    private val handShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x60382214 }
    private val handEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF865235.toInt()
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val handHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF1DC.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val handShine = Path().apply {
        moveTo(.035f, -.08f)
        lineTo(.035f, -.30f)
        quadTo(.065f, -.385f, .105f, -.31f)
    }
    private val handCreases = Path().apply {
        moveTo(.185f, .10f); lineTo(.18f, .19f)
        moveTo(.265f, .12f); lineTo(.255f, .21f)
        moveTo(.02f, .18f); quadTo(.10f, .15f, .15f, .25f)
    }


    fun draw(canvas: Canvas, x: Float, y: Float, radius: Float, alpha: Int) {
        if (radius <= 0f || alpha <= 0) return
        val opacity = alpha.coerceIn(0, 255)
        val saved = canvas.save()
        canvas.translate(x, y)
        canvas.scale(radius, radius)
        handShadow.alpha = opacity * 3 / 10
        canvas.save()
        canvas.translate(.035f, .045f)
        canvas.drawPath(handPath, handShadow)
        canvas.restore()
        handFill.alpha = opacity
        canvas.drawPath(handPath, handFill)
        handEdge.alpha = opacity
        handEdge.strokeWidth = maxOf(.025f, .65f * density / radius)
        canvas.drawPath(handPath, handEdge)
        handHighlight.alpha = opacity * 3 / 4
        handHighlight.strokeWidth = maxOf(.025f, .55f * density / radius)
        canvas.drawPath(handShine, handHighlight)
        handEdge.alpha = opacity / 2
        handEdge.strokeWidth = maxOf(.018f, .40f * density / radius)
        canvas.drawPath(handCreases, handEdge)
        canvas.restoreToCount(saved)
    }
}
