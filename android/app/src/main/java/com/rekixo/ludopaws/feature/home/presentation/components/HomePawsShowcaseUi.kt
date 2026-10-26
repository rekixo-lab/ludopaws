package com.rekixo.ludopaws.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.view.View
import android.widget.LinearLayout
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.feature.store.presentation.components.FeaturedPawsCard

internal class HomePawsShowcase(context: Context, onExplore: (String) -> Unit) : LinearLayout(context) {
    private val card = FeaturedPawsCard(context, onAction = onExplore)

    init {
        orientation = VERTICAL
        addView(HomeWordmark(context), LayoutParams(-1, 0, 1f))
        addView(card, LayoutParams(-1, dp(136)))
    }

    fun setActive(value: Boolean) = card.setActive(value)

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}

private class HomeWordmark(context: Context) : View(context) {
    private val artwork = requireNotNull(context.getDrawable(R.drawable.ludo_paws_home_hero))
    private val outline = Path().apply {
        moveTo(210f, 230f)
        lineTo(290f, 30f)
        lineTo(1030f, 0f)
        lineTo(1220f, 150f)
        lineTo(1250f, 500f)
        lineTo(1170f, 520f)
        lineTo(750f, 515f)
        lineTo(530f, 550f)
        lineTo(450f, 525f)
        lineTo(450f, 440f)
        lineTo(235f, 420f)
        close()
    }

    init {
        contentDescription = "Ludo Paws"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width / 1080f, height / 550f)
        val saved = canvas.save()
        canvas.translate((width - 1080f * scale) * .5f - 180f * scale, 0f)
        canvas.scale(scale, scale)
        canvas.clipPath(outline)
        artwork.setBounds(0, 0, 1422, 1106)
        artwork.draw(canvas)
        canvas.restoreToCount(saved)
    }
}
