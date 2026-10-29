package com.rekixo.ludopaws.feature.store.presentation.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import com.rekixo.ludopaws.feature.store.domain.StoreCatalog
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.feature.store.presentation.art.GemClusterView

internal object CosmeticPriceLabels {
    private var gemIcon: Bitmap? = null

    fun gems(context: Context, amount: Int, suffix: String = ""): CharSequence {
        val size = (12 * context.resources.displayMetrics.density + .5f).toInt().coerceAtLeast(1)
        val icon = gemIcon?.takeIf { it.width == size } ?: Bitmap.createBitmap(
            size, size, Bitmap.Config.ARGB_8888,
        ).also { bitmap ->
            bitmap.density = context.resources.displayMetrics.densityDpi
            GemClusterView(context).apply { layout(0, 0, size, size) }.draw(Canvas(bitmap))
            gemIcon = bitmap
        }
        val prefix = "$amount "
        return SpannableString("$prefix\uFFFC$suffix").apply {
            setSpan(ImageSpan(context, icon, ImageSpan.ALIGN_BASELINE), prefix.length,
                prefix.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

internal fun StoreActivity.cosmeticPlayPrice(cosmeticId: String): String =
    StoreCatalog.cosmeticProductId(cosmeticId)?.let { priceFor(it, "Price unavailable") }
        ?: "Price unavailable"

internal fun StoreActivity.cosmeticPurchaseLabel(cosmetic: StoreCosmetic): CharSequence =
    CosmeticPriceLabels.gems(this, cosmetic.gemPrice, "\nBUY · ${cosmeticPlayPrice(cosmetic.id)}")
