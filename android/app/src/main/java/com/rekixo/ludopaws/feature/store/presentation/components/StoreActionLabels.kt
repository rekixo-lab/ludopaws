package com.rekixo.ludopaws.feature.store.presentation.components

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.core.ui.art.RareGemArtwork
import com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction

internal object StoreActionLabels {
    fun action(context: Context, label: String, action: SmartUnlockAction, iconSizeDp: Int = 12): CharSequence {
        val icon = when (action) {
            SmartUnlockAction.PLAY_PRODUCT -> R.drawable.ic_google_play_mark
            SmartUnlockAction.WATCH_AD -> R.drawable.ic_store_rewarded_ad
            else -> return label
        }
        return withIcon(context, "\uFFFC\u00A0$label", 0, drawable(context, icon), iconSizeDp)
    }

    fun secureCheckout(context: Context): CharSequence {
        val label = "Pay securely with Google Play"
        return withIcon(context, "$label\u00A0\uFFFC", label.length + 1, drawable(context, R.drawable.ic_google_play_mark), 15)
    }

    fun gems(context: Context, label: String, iconSizeDp: Int): CharSequence {
        val icon = BitmapDrawable(context.resources, RareGemArtwork.gem(context.resources)).apply {
            setFilterBitmap(true)
        }
        return withIcon(context, "\uFFFC\u00A0$label", 0, icon, iconSizeDp)
    }

    private fun drawable(context: Context, id: Int): Drawable =
        requireNotNull(context.getDrawable(id)).mutate()

    private fun withIcon(context: Context, text: String, index: Int, icon: Drawable, iconSizeDp: Int): CharSequence {
        val height = LudoProofTheme.dp(context, iconSizeDp)
        val width = (height * icon.intrinsicWidth / icon.intrinsicHeight).coerceAtLeast(1)
        icon.setBounds(0, 0, width, height)
        return SpannableString(text).apply {
            setSpan(ImageSpan(icon, ImageSpan.ALIGN_BASELINE), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
