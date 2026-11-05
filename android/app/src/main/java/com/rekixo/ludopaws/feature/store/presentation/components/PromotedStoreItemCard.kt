package com.rekixo.ludopaws.feature.store.presentation.components

import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground
import com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.feature.store.presentation.preview.CosmeticPreview

/** Exact selected item, above the catalog; displaying it never starts an ad/payment. */
internal fun StoreActivity.promotedItemCard(item: StoreCosmetic, compact: Boolean = false): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER_HORIZONTAL
    setPadding(dp(10), dp(8), dp(10), dp(8))
    background = recessedPlywoodBackground(this@promotedItemCard, outerCornerRadiusDp = 16f)
    fun label(value: CharSequence, size: Float) = TextView(context).apply {
        text = value; textSize = size; gravity = Gravity.START or Gravity.CENTER_VERTICAL
        maxLines = 2; ellipsize = TextUtils.TruncateAt.END; includeFontPadding = false
        setTextColor(0xFF422B1C.toInt())
        typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
    }
    // Always use a gallery-style horizontal row: the catalog must stay visible.
    val horizontal = true
    val content = LinearLayout(context).apply {
        orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val details = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
    }
    details.addView(label(item.title, if (compact) 15f else 16f), LinearLayout.LayoutParams(-1, -2))
    val primary = smartUnlockDecision(item).primary
    val target = CosmeticPreview.forOffer(item)
    val preview = target?.artwork(context, animatePaw = true) ?: label("Preview unavailable", 14f)
    val actionLabel = smartUnlockLabel(item).takeIf {
        item.contentAvailable && (cosmetics.isOwned(item.id) || item.purchaseAvailable)
    }
    val artwork = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val side = dp(if (compact) 82 else 92)
        addView(preview, LinearLayout.LayoutParams(side, side))
        if (target != null) {
            addPreviewCue()
            target.attachTo(this, actionLabel) { activateCatalogOffer(item, primary) }
        }
    }
    content.addView(artwork, LinearLayout.LayoutParams(-2, -2).apply {
        if (horizontal) marginEnd = dp(10)
    })
    val done = cosmetics.rewardedAdProgress(item.id).coerceAtMost(item.requiredAdViews)
    val subline = when {
        cosmetics.isOwned(item.id) -> "Owned · Select to equip"
        primary == SmartUnlockAction.GEMS -> "${item.gemPrice} Gems · instant unlock"
        primary == SmartUnlockAction.PLAY_PRODUCT -> "Permanent Google Play unlock"
        primary == SmartUnlockAction.WATCH_AD || primary == SmartUnlockAction.CLAIM_ADS ->
            "$done / ${item.requiredAdViews} ads watched"
        primary == SmartUnlockAction.GET_GEMS -> "${item.gemPrice} Gems · earn or buy"
        else -> "Choose your unlock"
    }
    details.addView(label(subline, 11f).apply {
        setTextColor(0xFF60452D.toInt())
        maxLines = 1
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
    if (item.requiredAdViews > 0 && !cosmetics.isOwned(item.id) &&
        (primary == SmartUnlockAction.WATCH_AD || primary == SmartUnlockAction.CLAIM_ADS)) {
        val progress = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFD7B68B.toInt())
                cornerRadius = dp(4).toFloat()
            }
        }
        progress.addView(android.view.View(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF5AA63D.toInt())
                cornerRadius = dp(4).toFloat()
            }
        }, LinearLayout.LayoutParams(0, dp(4), done.toFloat()))
        progress.addView(android.view.View(context), LinearLayout.LayoutParams(
            0, dp(4), (item.requiredAdViews - done).coerceAtLeast(0).toFloat(),
        ))
        details.addView(progress, LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(5) })
    }
    details.addView(Button(context).apply {
        text = smartUnlockLabel(item)
        isAllCaps = false
        textSize = 13f
        LudoProofTheme.positive(this)
        minHeight = dp(44)
        minimumHeight = dp(44)
        isEnabled = item.contentAvailable && (cosmetics.isOwned(item.id) || item.purchaseAvailable)
        setOnClickListener { activateCatalogOffer(item, primary) }
    }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(6) })
    if (!cosmetics.isOwned(item.id)) unlockAlternativeLabel(item)?.let { alternative ->
        details.addView(TextView(context).apply {
            text = if (alternative == "Get Gems to Unlock") "Or get Gems ›" else "Or $alternative ›"
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTextColor(0xFF5D422B.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            minHeight = dp(30)
            contentDescription = "Alternative unlock: $alternative"
            setOnClickListener {
                StoreCosmeticCatalog.find(item.id)?.let { requestAlternateCosmetic(it) }
            }
        }, LinearLayout.LayoutParams(-1, dp(30)))
    }
    content.addView(details, if (horizontal) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2))
    addView(content, LinearLayout.LayoutParams(-1, -2))
    if (!compact) addView(TextView(context).apply {
        text = "All items ›"
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(0xFF60452D.toInt())
        minHeight = dp(32)
        contentDescription = "Back to full catalog"
        setOnClickListener { promotedCosmeticId = null; renderStore() }
    }, LinearLayout.LayoutParams(-1, dp(32)))
}
