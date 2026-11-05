package com.rekixo.ludopaws.feature.store.presentation.components

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.widget.TextView
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction
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

private fun storeButtonBackground(context: Context, color: Int = 0xB3372718.toInt()) =
    GradientDrawable().apply {
        cornerRadius = LudoProofTheme.dp(context, 12).toFloat()
        setColor(color)
    }

internal fun TextView.applyCosmeticOwnershipBadge(equipped: Boolean) {
    val tint = if (equipped) 0xFFFFE4A4.toInt() else 0xFF104B29.toInt()
    val iconRes = if (equipped) R.drawable.ic_store_equipped else R.drawable.ic_store_unlocked
    val icon = requireNotNull(context.getDrawable(iconRes)).mutate().apply { setTint(tint) }
    text = if (equipped) "Equipped" else "Unlocked"
    textSize = 12f
    setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
    setTextColor(tint)
    gravity = android.view.Gravity.CENTER
    includeFontPadding = false
    letterSpacing = 0f
    minHeight = LudoProofTheme.dp(context, 48)
    val inset = LudoProofTheme.dp(context, 10)
    setPaddingRelative(inset, 0, inset, 0)
    compoundDrawablePadding = LudoProofTheme.dp(context, 5)
    setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
    background = if (equipped) storeButtonBackground(context)
        else storeButtonBackground(context, 0xC7DAF5E1.toInt())
}

/** Show each configured payment method, including when Google Play has no price. */
internal fun StoreActivity.cosmeticPaymentOptions(
    cosmetic: StoreCosmetic,
): android.widget.LinearLayout? {
    val options = listUnlockOptions(cosmetic)
    if (options.isEmpty()) return null
    return android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        options.forEach { (action, label, enabled) ->
            addView(android.widget.TextView(context).apply {
                text = when (action) {
                    SmartUnlockAction.GEMS,
                    SmartUnlockAction.GET_GEMS -> StoreActionLabels.gems(context, label, 12)
                    else -> StoreActionLabels.action(context, label, action, 12)
                }
                gravity = android.view.Gravity.CENTER
                minHeight = (48 * resources.displayMetrics.density).toInt()
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                val description = when {
                    !enabled -> "Google Play price unavailable"
                    action == SmartUnlockAction.GET_GEMS -> "$label. Open Gems shop"
                    else -> smartUnlockLabel(cosmetic, action)
                }
                contentDescription = "${cosmetic.title}: $description"
                background = storeButtonBackground(context)
                isEnabled = enabled
                isFocusable = enabled
                if (enabled) {
                    setOnClickListener { requestListedUnlock(cosmetic, action) }
                } else {
                    isClickable = true // Keep this tap from falling through to the card's default action.
                    alpha = 0.72f
                }
            }, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = (3 * resources.displayMetrics.density).toInt()
            })
        }
    }
}
