package com.rekixo.ludopaws.core.ui.art

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.rekixo.ludopaws.R

/** Currency artwork shared by Home, Shop, Rewards, price spans and referral previews. */
internal object RareGemArtwork {
    private val bitmaps = LruCache<Int, Bitmap>(2)

    fun gem(resources: Resources): Bitmap = load(resources, R.drawable.currency_rare_gem_3d)
    fun bucket(resources: Resources): Bitmap = load(resources, R.drawable.referral_rare_gem_bucket_3d)

    @Synchronized
    private fun load(resources: Resources, resourceId: Int): Bitmap {
        bitmaps.get(resourceId)?.let { return it }
        val options = BitmapFactory.Options().apply {
            inScaled = false
            inSampleSize = 4
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return requireNotNull(BitmapFactory.decodeResource(resources, resourceId, options)) {
            "Missing rare Gem artwork: $resourceId"
        }.also { bitmaps.put(resourceId, it) }
    }
}
