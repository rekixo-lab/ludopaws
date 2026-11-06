package com.rekixo.ludopaws.ui.home

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.rekixo.ludopaws.R
import kotlin.math.min

/** Google Play mark rendered from a vector path asset for clean, exact colour joins. */
internal class HomeGooglePlayIconView(
    context: Context,
) : View(context) {
    private val playMark =
        context.getDrawable(R.drawable.ic_google_play_mark)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val drawable = playMark ?: return
        val side = min(width, height).toFloat()
        if (side <= 0f) return

        // Preserve the original prism's portrait proportions instead of stretching it
        // into a triangle; all four colour segments come from the same vector asset.
        val markHeight = (side * 0.92f * 0.8f).toInt().coerceAtLeast(1)
        val markWidth = (markHeight * 38.1f / 44.1f).toInt().coerceAtLeast(1)
        val left = (width - markWidth) / 2
        val top = (height - markHeight) / 2
        drawable.setBounds(left, top, left + markWidth, top + markHeight)
        drawable.draw(canvas)
    }
}
