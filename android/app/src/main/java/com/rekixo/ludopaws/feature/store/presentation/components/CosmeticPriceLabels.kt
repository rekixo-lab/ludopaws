package com.rekixo.ludopaws.feature.store.presentation.components

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import com.rekixo.ludopaws.feature.store.domain.StoreCatalog
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.core.ui.art.RareGemArtwork

internal object CosmeticPriceLabels {
    fun gems(context: Context, amount: Int, suffix: String = ""): CharSequence {
        val size = (12 * context.resources.displayMetrics.density + .5f).toInt().coerceAtLeast(1)
        val icon = BitmapDrawable(context.resources, RareGemArtwork.gem(context.resources)).apply {
            setFilterBitmap(true)
            setBounds(0, 0, size, size)
        }
        val prefix = "$amount "
        return SpannableString("$prefix\uFFFC$suffix").apply {
            setSpan(ImageSpan(icon, ImageSpan.ALIGN_BASELINE), prefix.length,
                prefix.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

internal fun StoreActivity.cosmeticPlayPrice(cosmeticId: String): String =
    StoreCatalog.cosmeticProductId(cosmeticId)?.let { priceFor(it, "Price unavailable") }
        ?: "Price unavailable"

internal fun StoreActivity.cosmeticPurchaseLabel(cosmetic: StoreCosmetic): CharSequence {
    val action = smartUnlockDecision(cosmetic).primary
    return StoreActionLabels.action(this, smartUnlockLabel(cosmetic, action), action)
}
