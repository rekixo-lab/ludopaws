package com.rekixo.ludopaws.feature.store.presentation.components

import android.graphics.Typeface
import android.graphics.drawable.InsetDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.feature.store.presentation.actions.handleCosmeticTap

/** Exact selected item, above the catalog; displaying it never starts an ad/payment. */
internal fun StoreActivity.promotedItemCard(item: StoreCosmetic, compact: Boolean = false): LinearLayout = LinearLayout(this).apply {
    val pawSpotlight = compact && item.category == CosmeticCategory.CHARACTER_PACK
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
    // Keep the artwork and offer details side by side so the catalog stays visible.
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val details = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val titleSize = when {
        pawSpotlight -> 14f
        compact -> 15f
        else -> 16f
    }
    details.addView(label(item.title, titleSize), LinearLayout.LayoutParams(-1, -2))
    val preview = when {
        !item.contentAvailable -> label("Preview unavailable", 14f)
        item.category == CosmeticCategory.BOARD &&
            com.rekixo.ludopaws.feature.boards.domain.BoardRegistry.find(item.id) != null ->
            StoreCatalogArtwork.board(context, item.id)
        item.category == CosmeticCategory.DICE &&
            com.rekixo.ludopaws.feature.dice.domain.DiceRegistry.find(item.id) != null ->
            StoreCatalogArtwork.dice(context, item.id)
        else -> {
            val id = CharacterDefinitions.all.firstOrNull { StoreCosmeticCatalog.characterUnlock(it.character.id)?.id == item.id }?.character?.id
            if (id == null) label("Preview unavailable", 14f)
            else StoreCatalogArtwork.paw(context, id)
        }
    }
    val previewSize = dp(when {
        pawSpotlight && LudoProofTheme.isCompactWidth(context) -> 120
        pawSpotlight -> 136
        compact -> 82
        else -> 92
    })
    content.addView(preview, LinearLayout.LayoutParams(previewSize, previewSize).apply {
        marginEnd = dp(if (pawSpotlight) 8 else 10)
    })
    val done = cosmetics.rewardedAdProgress(item.id).coerceAtMost(item.requiredAdViews)
    val primary = smartUnlockDecision(item).primary
    val subline = when {
        cosmetics.isOwned(item.id) -> "Owned · tap to equip"
        primary == SmartUnlockAction.GEMS ->
            if (pawSpotlight) "${item.gemPrice} Gems" else "${item.gemPrice} Gems · instant unlock"
        primary == SmartUnlockAction.PLAY_PRODUCT -> if (pawSpotlight) "Google Play purchase" else "Permanent Google Play unlock"
        primary == SmartUnlockAction.WATCH_AD || primary == SmartUnlockAction.CLAIM_ADS ->
            "$done / ${item.requiredAdViews} ads watched"
        primary == SmartUnlockAction.GET_GEMS ->
            if (pawSpotlight) "${item.gemPrice} Gems needed" else "${item.gemPrice} Gems · earn or buy"
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
    val activate = {
        val current = StoreCosmeticCatalog.find(item.id)
        if (current == null || current != item || smartUnlockDecision(current).primary != primary) {
            showStoreMessage("This offer changed. Choose the current item to continue.")
            renderStore()
        } else handleCosmeticTap(current)
    }
    val actionLabel = smartUnlockLabel(item, primary)
    val buttonLabel = if (pawSpotlight) when (primary) {
        SmartUnlockAction.GEMS, SmartUnlockAction.CLAIM_ADS -> "Unlock"
        SmartUnlockAction.PLAY_PRODUCT -> actionLabel.removeSuffix(" with Google Play")
        SmartUnlockAction.WATCH_AD -> "Watch Ad"
        SmartUnlockAction.GET_GEMS -> "Get Gems"
        else -> actionLabel
    } else actionLabel
    if (item.contentAvailable && (cosmetics.isOwned(item.id) || item.purchaseAvailable)) {
        content.isFocusable = true
        content.contentDescription = "${item.title}. $actionLabel"
        content.setOnClickListener { activate() }
    }
    val buttonHeight = dp(if (pawSpotlight) 48 else 44)
    details.addView(Button(context).apply {
        text = buttonLabel
        LudoProofTheme.positive(this)
        if (pawSpotlight) {
            // Keep the compact button face inside a full-height touch target.
            background = InsetDrawable(background, 0, dp(6), 0, dp(6))
            setPaddingRelative(dp(12), dp(6), dp(12), dp(6))
            minWidth = dp(80)
            minimumWidth = dp(80)
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setAutoSizeTextTypeUniformWithConfiguration(10, 13, 1, TypedValue.COMPLEX_UNIT_SP)
            contentDescription = actionLabel
            elevation = dp(2).toFloat()
        }
        minHeight = buttonHeight
        minimumHeight = buttonHeight
        isEnabled = item.contentAvailable && (cosmetics.isOwned(item.id) || item.purchaseAvailable)
        setOnClickListener { activate() }
    }, LinearLayout.LayoutParams(if (pawSpotlight) -2 else -1, buttonHeight).apply {
        topMargin = dp(if (pawSpotlight) 2 else 6)
    })
    if (!cosmetics.isOwned(item.id)) unlockAlternativeLabel(item)?.let { alternative ->
        details.addView(TextView(context).apply {
            text = if (alternative == "Get Gems to Unlock") "Or get Gems ›" else "Or $alternative ›"
            textSize = 11.5f
            gravity = if (pawSpotlight) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
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
    content.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
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
