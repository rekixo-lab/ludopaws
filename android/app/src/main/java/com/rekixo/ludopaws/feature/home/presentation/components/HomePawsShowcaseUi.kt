package com.rekixo.ludopaws.ui.home

import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.feature.store.presentation.components.FeaturedPawsCard

internal class HomePawsShowcase(context: Context, onExplore: (String) -> Unit) : LinearLayout(context) {
    private val card = FeaturedPawsCard(context, onAction = onExplore)

    init {
        orientation = VERTICAL
        addView(card, LayoutParams(-1, dp(136)))
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ludo_paws_home_hero)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Ludo Paws"
        }, LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
    }

    fun setActive(value: Boolean) = card.setActive(value)

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
