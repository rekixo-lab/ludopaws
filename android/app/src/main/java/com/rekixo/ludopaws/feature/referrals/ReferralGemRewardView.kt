package com.rekixo.ludopaws.feature.referrals

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.feature.store.presentation.art.GemClusterView

/** Reuses the Store's real faceted gem artwork, overflowing a wooden bucket. */
internal class ReferralGemRewardView(context: Context, compact: Boolean) : LinearLayout(context) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val size = dp(if (compact) 58 else 96)
        val bucket = FrameLayout(context).apply {
            addView(GemBucketView(context), FrameLayout.LayoutParams(-1, -1))
            addView(GemClusterView(context, 5), FrameLayout.LayoutParams((size * .8f).toInt(), (size * .7f).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            addView(GemClusterView(context, 3), FrameLayout.LayoutParams((size * .4f).toInt(), (size * .4f).toInt(), Gravity.BOTTOM or Gravity.END))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        addView(bucket, LayoutParams(size, size))
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(8), 0, 0, 0)
            addView(TextView(context).apply {
                text = "FREE 100 Gems"
                textSize = if (compact) 17f else 23f
                typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
                setTextColor(0xFF28651D.toInt())
                setShadowLayer(1f, 0f, 1f, 0x66FFFFFF)
            })
            addView(TextView(context).apply {
                text = if (compact) "Earn with a qualifying invite" else "After your friend’s qualifying match"
                textSize = if (compact) 10f else 12f
                setTextColor(0xFF523B28.toInt())
            })
        }, LayoutParams(0, -2, 1f))
        contentDescription = "Free 100 Gems for you after a new invited friend completes a qualifying match. No reward for sharing alone."
    }
}

private class GemBucketView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val body = Path()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val checkpoint = canvas.save()
        canvas.scale(width / 100f, height / 100f)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = 0x33000000
        canvas.drawOval(18f, 85f, 88f, 97f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = 0xFFAD732F.toInt()
        canvas.drawArc(19f, 25f, 82f, 91f, 180f, 180f, false, paint)
        body.reset()
        body.moveTo(18f, 47f); body.lineTo(83f, 47f); body.lineTo(74f, 88f)
        body.quadTo(50f, 98f, 28f, 88f); body.close()
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(18f, 0f, 83f, 0f,
            intArrayOf(0xFF855025.toInt(), 0xFFF2C773.toInt(), 0xFFAE7133.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawPath(body, paint)
        paint.shader = null
        paint.color = 0xFF754321.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
        for (x in listOf(34f, 45f, 57f, 69f)) canvas.drawLine(x, 52f, 50f + (x - 50f) * .7f, 88f, paint)
        paint.color = 0xFFFFDB86.toInt(); paint.strokeWidth = 5f
        canvas.drawArc(18f, 39f, 83f, 56f, 0f, 180f, false, paint)
        canvas.drawArc(27f, 77f, 75f, 91f, 0f, 180f, false, paint)
        canvas.restoreToCount(checkpoint)
    }
}
