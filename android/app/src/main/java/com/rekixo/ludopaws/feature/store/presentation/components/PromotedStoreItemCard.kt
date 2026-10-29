package com.rekixo.ludopaws.feature.store.presentation.components

import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.DiceView
import com.rekixo.ludopaws.LudoBoardView
import com.rekixo.ludopaws.LudoPawsBoardChromeView
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.feature.store.presentation.actions.handleCosmeticTap

/** Exact selected item, above the catalog; displaying it never starts an ad/payment. */
internal fun StoreActivity.promotedItemCard(item: StoreCosmetic, compact: Boolean = false): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER_HORIZONTAL
    setPadding(dp(16), dp(12), dp(16), dp(12))
    background = recessedPlywoodBackground(this@promotedItemCard, outerCornerRadiusDp = 20f)
    fun label(value: CharSequence, size: Float) = TextView(context).apply {
        text = value; textSize = size; gravity = Gravity.CENTER
        setTextColor(0xFF422B1C.toInt())
        typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
    }
    val horizontal = compact && resources.configuration.screenWidthDp >= 320 && resources.configuration.fontScale < 1.3f
    val content = LinearLayout(context).apply {
        orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        gravity = Gravity.CENTER
    }
    val details = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
    }
    if (compact) details.addView(label(item.title, 18f), LinearLayout.LayoutParams(-1, -2))
    val preview = when {
        !item.contentAvailable -> label("Preview unavailable", 14f)
        item.category == CosmeticCategory.BOARD && com.rekixo.ludopaws.feature.boards.domain.BoardRegistry.find(item.id) != null -> FrameLayout(context).apply {
            addView(LudoBoardView(context).apply {
                setBoardThemeOverride(item.id); setClassicTokenDrawingEnabled(false)
            }, FrameLayout.LayoutParams(-1, -1))
            addView(LudoPawsBoardChromeView(context).apply { setBoardThemeOverride(item.id) }, FrameLayout.LayoutParams(-1, -1))
        }
        item.category == CosmeticCategory.DICE && com.rekixo.ludopaws.feature.dice.domain.DiceRegistry.find(item.id) != null -> DiceView(context).apply {
            setPreviewStyle(item.id); showOutcome(1, animate = false); setPlayerTeamColor(null)
        }
        else -> {
            val id = CharacterDefinitions.all.firstOrNull { StoreCosmeticCatalog.characterUnlock(it.character.id)?.id == item.id }?.character?.id
            if (id == null) label("Preview unavailable", 14f)
            else LudoPawsCharacterAvatarView(context).apply { bind(id, showCircularChrome = false) }
        }
    }
    if (!compact) {
        addView(label(item.title, 22f), LinearLayout.LayoutParams(-1, -2))
    }
    content.addView(preview, LinearLayout.LayoutParams(dp(if (compact) 100 else 150), dp(if (compact) 100 else 150)).apply {
        topMargin = dp(8); bottomMargin = dp(8)
        if (horizontal) marginEnd = dp(12)
    })
    val done = cosmetics.rewardedAdProgress(item.id).coerceAtMost(item.requiredAdViews)
    if (item.requiredAdViews > 0) details.addView(label("$done / ${item.requiredAdViews} verified ads completed", 14f))
    if (item.gemPrice > 0) details.addView(label(CosmeticPriceLabels.gems(context, item.gemPrice), 16f))
    if (item.unlockKind == CosmeticUnlockKind.PLAY_PRODUCT || CosmeticUnlockKind.PLAY_PRODUCT in item.unlockChannels)
        details.addView(label("Google Play: ${cosmeticPlayPrice(item.id)}", 14f))
    val adsOnlyUnavailable = item.requiredAdViews > done && item.gemPrice == 0 &&
        item.unlockChannels.size <= 1 && !BuildConfig.LUDOPROOF_ADS_ENABLED && !cosmetics.isOwned(item.id)
    details.addView(Button(context).apply {
        text = when {
            !item.contentAvailable -> "Unavailable"
            cosmetics.isOwned(item.id) -> "Select item"
            !item.purchaseAvailable || !com.rekixo.ludopaws.ConnectivityMonitor(context) { }.isOnline() -> "Connect to unlock"
            adsOnlyUnavailable -> "Ads unavailable"
            item.unlockChannels.size > 1 -> "Unlock options"
            item.requiredAdViews > done -> "Watch ad · ${item.requiredAdViews - done} remaining"
            else -> "Unlock item"
        }
        isAllCaps = false
        LudoProofTheme.positive(this)
        minHeight = dp(48)
        isEnabled = item.contentAvailable && !adsOnlyUnavailable
        setOnClickListener {
            val current = StoreCosmeticCatalog.find(item.id)
            if (current == null || !current.contentAvailable) showStoreMessage("This item is no longer available. Refresh the catalog.")
            else handleCosmeticTap(current)
        }
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    content.addView(details, if (horizontal) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(-1, -2))
    addView(content, LinearLayout.LayoutParams(-1, -2))
    if (!compact) addView(Button(context).apply {
        text = "Back to catalog"; isAllCaps = false
        setTextColor(0xFF422B1C.toInt())
        backgroundTintList = null
        background = plywoodBackground(context, cornerRadiusDp = 14f)
        minHeight = dp(48)
        setOnClickListener { promotedCosmeticId = null; renderStore() }
    }, LinearLayout.LayoutParams(-1, -2))
}
