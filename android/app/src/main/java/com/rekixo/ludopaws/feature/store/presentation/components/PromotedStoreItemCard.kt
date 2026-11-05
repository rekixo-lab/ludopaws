package com.rekixo.ludopaws.feature.store.presentation.components

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
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
        if (pawSpotlight) setPaddingRelative(0, 0, dp(6), 0)
    }
    val character = if (item.category == CosmeticCategory.CHARACTER_PACK) {
        CharacterDefinitions.all.firstOrNull {
            StoreCosmeticCatalog.characterUnlock(it.character.id)?.id == item.id
        }?.character
    } else null
    val title = if (pawSpotlight) character?.displayName ?: item.title else item.title
    val titleSize = when {
        pawSpotlight -> 20f
        compact -> 15f
        else -> 16f
    }
    details.addView(label(title, titleSize), LinearLayout.LayoutParams(-1, -2))
    val preview = when {
        !item.contentAvailable -> label("Preview unavailable", 14f)
        item.category == CosmeticCategory.BOARD &&
            com.rekixo.ludopaws.feature.boards.domain.BoardRegistry.find(item.id) != null ->
            StoreCatalogArtwork.board(context, item.id)
        item.category == CosmeticCategory.DICE &&
            com.rekixo.ludopaws.feature.dice.domain.DiceRegistry.find(item.id) != null ->
            StoreCatalogArtwork.dice(context, item.id)
        character != null -> StoreCatalogArtwork.paw(context, character.id)
        else -> label("Preview unavailable", 14f)
    }
    val screenWidthDp = resources.configuration.screenWidthDp
    val previewSize = dp(when {
        pawSpotlight && screenWidthDp in 1..299 -> screenWidthDp * 2 / 5
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
            if (pawSpotlight) "${item.gemPrice} Gems to unlock" else "${item.gemPrice} Gems · instant unlock"
        primary == SmartUnlockAction.PLAY_PRODUCT -> if (pawSpotlight) "Google Play purchase" else "Permanent Google Play unlock"
        primary == SmartUnlockAction.WATCH_AD || primary == SmartUnlockAction.CLAIM_ADS ->
            "$done / ${item.requiredAdViews} ads watched"
        primary == SmartUnlockAction.GET_GEMS ->
            if (pawSpotlight) "${item.gemPrice} Gems needed" else "${item.gemPrice} Gems · earn or buy"
        else -> "Choose your unlock"
    }
    details.addView(label(subline, if (pawSpotlight) 12f else 11f).apply {
        setTextColor(0xFF60452D.toInt())
        maxLines = if (pawSpotlight) 2 else 1
        if (pawSpotlight) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (pawSpotlight) 4 else 2) })
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
        content.contentDescription = "$title. $actionLabel"
        content.setOnClickListener { activate() }
    }
    val buttonHeight = dp(if (pawSpotlight) 48 else 44)
    details.addView(Button(context).apply {
        text = buttonLabel
        if (pawSpotlight) {
            stylePawSpotlightAction(this)
            contentDescription = actionLabel
        } else {
            LudoProofTheme.positive(this)
        }
        minHeight = buttonHeight
        minimumHeight = buttonHeight
        isEnabled = item.contentAvailable && (cosmetics.isOwned(item.id) || item.purchaseAvailable)
        setOnClickListener { activate() }
    }, LinearLayout.LayoutParams(
        if (pawSpotlight) -2 else -1,
        if (pawSpotlight) -2 else buttonHeight,
    ).apply {
        topMargin = dp(if (pawSpotlight) 5 else 6)
    })
    if (!cosmetics.isOwned(item.id)) unlockAlternativeLabel(item)?.let { alternative ->
        details.addView(TextView(context).apply {
            text = if (alternative == "Get Gems to Unlock") "Or get Gems ›" else "Or $alternative ›"
            textSize = 11.5f
            gravity = if (pawSpotlight) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
            setTextColor(0xFF5D422B.toInt())
            maxLines = if (pawSpotlight) 2 else 1
            ellipsize = TextUtils.TruncateAt.END
            minHeight = dp(if (pawSpotlight) 32 else 30)
            if (pawSpotlight) setPaddingRelative(0, dp(3), 0, dp(3))
            contentDescription = "Alternative unlock: $alternative"
            setOnClickListener {
                StoreCosmeticCatalog.find(item.id)?.let { requestAlternateCosmetic(it) }
            }
        }, LinearLayout.LayoutParams(-1, if (pawSpotlight) -2 else dp(30)))
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

private fun StoreActivity.stylePawSpotlightAction(button: Button) {
    val enabled = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(0xFF28644E.toInt(), 0xFF164333.toInt()),
    ).apply {
        cornerRadius = dp(11).toFloat()
        setStroke(dp(1), 0xFFCB9C58.toInt())
    }
    val disabled = LudoProofTheme.rounded(
        0xFF675B4E.toInt(), 11f, 0xFFB29A7D.toInt(), 1f, this,
    )
    val surface = StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), disabled)
        addState(intArrayOf(), enabled)
    }
    button.apply {
        isAllCaps = false
        textSize = 14f
        typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        setTextColor(0xFFFFF1D8.toInt())
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        minWidth = dp(112)
        maxWidth = dp(180)
        background = InsetDrawable(
            RippleDrawable(ColorStateList.valueOf(0x40FFE0A0), surface, null),
            0, dp(3), 0, dp(3),
        )
        backgroundTintList = null
        stateListAnimator = null
        elevation = dp(2).toFloat()
        setPaddingRelative(dp(10), dp(9), dp(10), dp(9))
    }
}
