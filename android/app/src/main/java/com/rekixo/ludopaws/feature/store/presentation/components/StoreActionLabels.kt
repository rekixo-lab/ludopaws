package com.rekixo.ludopaws.feature.store.presentation.components

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction

internal object StoreActionLabels {
    fun action(context: Context, label: String, action: SmartUnlockAction, iconSizeDp: Int = 12): CharSequence {
        val icon = when (action) {
            SmartUnlockAction.PLAY_PRODUCT -> R.drawable.ic_google_play_mark
            SmartUnlockAction.WATCH_AD -> R.drawable.ic_store_rewarded_ad
            else -> return label
        }
        return withIcon(context, "\uFFFC\u00A0$label", 0, icon, iconSizeDp)
    }

    fun secureCheckout(context: Context): CharSequence {
        val label = "Pay securely with Google Play"
        return withIcon(context, "$label\u00A0\uFFFC", label.length + 1, R.drawable.ic_google_play_mark, 15)
    }

    private fun withIcon(context: Context, text: String, index: Int, iconId: Int, iconSizeDp: Int): CharSequence {
        val icon = requireNotNull(context.getDrawable(iconId)).mutate()
        val height = LudoProofTheme.dp(context, iconSizeDp)
        val width = (height * icon.intrinsicWidth / icon.intrinsicHeight).coerceAtLeast(1)
        icon.setBounds(0, 0, width, height)
        return SpannableString(text).apply {
            setSpan(ImageSpan(icon, ImageSpan.ALIGN_BASELINE), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
