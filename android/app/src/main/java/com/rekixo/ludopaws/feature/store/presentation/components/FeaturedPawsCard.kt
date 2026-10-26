package com.rekixo.ludopaws.feature.store.presentation.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind

internal class FeaturedPawsCard(
    context: Context,
    initialId: String? = null,
    private val detail: Boolean = false,
    private val onAction: (String) -> Unit,
) : LinearLayout(context) {
    private val inventory = CosmeticInventoryStore(context)
    private val selection = CharacterSelectionStore(context)
    private val initialOwnedIds = inventory.ownedCosmeticIds()
    private val candidates = CharacterDefinitions.all.filter { definition ->
        val id = definition.character.id
        val offer = StoreCosmeticCatalog.characterUnlock(id)
        CharacterRegistry.find(id) != null && (offer == null || offer.contentAvailable) &&
            (detail || offer == null || offer.id in initialOwnedIds || BuildConfig.LUDOPROOF_ADS_ENABLED)
    }.sortedBy { definition ->
        val offer = StoreCosmeticCatalog.characterUnlock(definition.character.id)
        if (offer != null && offer.id !in initialOwnedIds) 0 else 1
    }
    private var index = candidates.indexOfFirst { it.character.id == initialId }.coerceAtLeast(0)
    private var preview: FeaturedPawsPreview? = null
    private var active = false
    private val avatar = LudoPawsCharacterAvatarView(context)
    private val viewport = FrameLayout(context)
    private val name = TextView(context)
    private val description = TextView(context)
    private val action = Button(context)
    private val cycle = object : Runnable {
        override fun run() {
            if (!active || detail || !isShown || candidates.size < 2) return
            index = (index + 1) % candidates.size
            refresh()
            postDelayed(this, 12000L)
        }
    }
    val characterId: String get() = candidates.getOrNull(index)?.character?.id ?: "dog"

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(6), dp(8), dp(6))
        background = plywoodBackground(context, cornerRadiusDp = 16f)
        viewport.addView(avatar, FrameLayout.LayoutParams(-1, -1))
        addView(viewport, LayoutParams(dp(if (detail) 140 else 112), dp(if (detail) 164 else 124)))
        val copy = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(6), 0, 0, 0)
        }
        copy.addView(TextView(context).apply {
            text = if (detail) "MEET THIS PAW" else "PAWS SPOTLIGHT"
            textSize = 9f
            setTextColor(0xFFFFDC79.toInt())
            setTypeface(Typeface.DEFAULT_BOLD)
        })
        name.textSize = if (detail) 18f else 16f
        name.setTypeface(Typeface.DEFAULT_BOLD)
        name.setTextColor(Color.WHITE)
        name.maxLines = 1
        copy.addView(name)
        description.textSize = 10f
        description.setTextColor(0xFFFFEDCA.toInt())
        description.maxLines = 2
        copy.addView(description)
        action.textSize = 10f
        action.minHeight = dp(48)
        action.minimumHeight = dp(48)
        action.setPadding(dp(4), 0, dp(4), 0)
        action.setOnClickListener { onAction(characterId) }
        copy.addView(action, LayoutParams(-1, dp(48)))
        addView(copy, LayoutParams(0, -2, 1f))
        refresh()
    }

    fun refresh() {
        val character = candidates.getOrNull(index)?.character ?: return
        name.text = character.displayName.uppercase()
        avatar.bind(characterId = character.id, ringColor = 0xFF5BE0FF.toInt(),
            highlighted = false, showCircularChrome = false)
        preview?.characterId = character.id
        val owned = selection.isUnlocked(character.id)
        val selected = owned && selection.load().characterId == character.id
        val offer = StoreCosmeticCatalog.characterUnlock(character.id)?.let { StoreCosmeticCatalog.find(it.id) }
        val progress = offer?.let { inventory.rewardedAdProgress(it.id).coerceAtMost(it.requiredAdViews) } ?: 0
        description.text = when {
            owned -> "Cute moves. Your next match companion."
            offer?.unlockKind == CosmeticUnlockKind.REWARDED_ADS -> "$progress/${offer.requiredAdViews} verified ads completed"
            offer?.unlockKind == CosmeticUnlockKind.GEMS -> "Unlock with ${offer.gemPrice} Gems"
            else -> "Explore this character in the Store"
        }
        action.text = when {
            !detail -> "EXPLORE ›"
            selected -> "SELECTED ✓"
            owned -> "SELECT PAW"
            offer == null || !offer.contentAvailable -> "UNAVAILABLE"
            offer.unlockKind == CosmeticUnlockKind.REWARDED_ADS && !BuildConfig.LUDOPROOF_ADS_ENABLED -> "ADS UNAVAILABLE"
            offer.unlockKind == CosmeticUnlockKind.REWARDED_ADS && progress < offer.requiredAdViews -> "WATCH AD · $progress/${offer.requiredAdViews}"
            offer.unlockKind == CosmeticUnlockKind.REWARDED_ADS -> "UNLOCK PAW"
            offer.unlockKind == CosmeticUnlockKind.GEMS -> "${offer.gemPrice} GEMS · UNLOCK"
            offer.unlockKind == CosmeticUnlockKind.FREE -> "GET PAW"
            else -> "UNAVAILABLE"
        }
        action.isEnabled = !detail || (!selected && (owned || offer != null && offer.contentAvailable &&
            (offer.unlockKind == CosmeticUnlockKind.FREE || offer.unlockKind == CosmeticUnlockKind.GEMS ||
                offer.unlockKind == CosmeticUnlockKind.REWARDED_ADS && BuildConfig.LUDOPROOF_ADS_ENABLED)))
        contentDescription = "${character.displayName}. ${description.text}"
    }

    fun setActive(value: Boolean) {
        active = value
        removeCallbacks(cycle)
        if (value) {
            refresh()
            postDelayed({
                if (!active || !isAttachedToWindow) return@postDelayed
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                if (preview == null && manager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000) {
                    preview = FeaturedPawsPreview(context).also { live ->
                        live.characterId = characterId
                        live.onReady = { avatar.visibility = View.INVISIBLE }
                        live.onUnavailable = { avatar.visibility = View.VISIBLE }
                        viewport.addView(live, FrameLayout.LayoutParams(-1, -1))
                    }
                }
                preview?.setActive(true)
            }, 250L)
            val motionAllowed = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
                android.animation.ValueAnimator.areAnimatorsEnabled()
            if (!detail && motionAllowed) postDelayed(cycle, 12000L)
        } else preview?.setActive(false)
    }

    override fun onDetachedFromWindow() {
        setActive(false)
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
