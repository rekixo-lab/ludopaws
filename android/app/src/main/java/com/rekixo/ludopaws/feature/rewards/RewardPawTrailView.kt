package com.rekixo.ludopaws.feature.rewards

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

internal class RewardPawTrailView(context: Context, private val collected: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        contentDescription = "$collected of 7 gifts toward the Garden Friend badge"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val spacing = width / 7f
        val size = minOf(spacing * .66f, height * .8f)
        if (size <= 0f) return
        repeat(7) { index ->
            val saved = canvas.save()
            canvas.translate(spacing * (index + .5f), height * .5f)
            canvas.scale(size, size)
            canvas.rotate(if (index % 2 == 0) -12f else 12f)
            paint.color = if (index < collected) 0xFF31754B.toInt() else 0xFF936F48.toInt()
            canvas.drawOval(-.26f, -.02f, .26f, .31f, paint)
            canvas.drawCircle(-.30f, -.10f, .095f, paint)
            canvas.drawCircle(-.11f, -.25f, .105f, paint)
            canvas.drawCircle(.11f, -.25f, .105f, paint)
            canvas.drawCircle(.30f, -.10f, .095f, paint)
            canvas.restoreToCount(saved)
        }
    }
}
