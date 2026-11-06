package com.rekixo.ludopaws.feature.store.presentation.art

import android.content.Context
import android.widget.ImageView
import com.rekixo.ludopaws.R

/** Offline renders of the same 3D chest with a progressively fuller gem pile. */
internal class GemPackArtworkView(context: Context, tier: Int) : ImageView(context) {
    init {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageResource(artwork[tier.coerceIn(artwork.indices)])
    }

    private companion object {
        private val artwork = intArrayOf(
            R.drawable.gem_pack_starter_3d,
            R.drawable.gem_pack_value_3d,
            R.drawable.gem_pack_popular_3d,
            R.drawable.gem_pack_mega_3d,
            R.drawable.gem_pack_ultra_3d,
            R.drawable.gem_pack_best_value_3d,
        )
    }
}
