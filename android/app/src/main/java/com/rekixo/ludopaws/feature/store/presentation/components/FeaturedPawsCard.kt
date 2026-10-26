package com.rekixo.ludopaws.feature.store.presentation.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Rect
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.characters.domain.model.CharacterDefinition
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
    private var candidates = emptyList<CharacterDefinition>()
    private var preferredId = initialId
    private var index = 0
    private var preview: FeaturedPawsPreview? = null
    private var active = false
    private val avatar = LudoPawsCharacterAvatarView(context)
    private val viewport = FrameLayout(context)
    private val name = TextView(context)
    private val eyebrow = TextView(context)
    private val copy = LinearLayout(context)
    private val description = TextView(context)
    private val action = Button(context)
    private var configuredWidth = -1
    private val visibleBounds = Rect()
    private val startPreview = object : Runnable {
        override fun run() {
            if (!active || !isAttachedToWindow || candidates.isEmpty()) return
            if (!viewport.isShown || !hasWindowFocus() || !viewport.getGlobalVisibleRect(visibleBounds) ||
                visibleBounds.height() < viewport.height / 2) {
                postDelayed(this, 250L)
                return
            }
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            if (preview == null && manager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000) {
                try {
                    preview = FeaturedPawsPreview(context).also { live ->
                        live.characterId = characterId
                        live.alpha = 0f
                        live.onReady = {
                            live.alpha = 1f
                            avatar.visibility = View.INVISIBLE
                        }
                        live.onUnavailable = { avatar.visibility = View.VISIBLE }
                        viewport.addView(live, FrameLayout.LayoutParams(-1, -1))
                    }
                } catch (error: RuntimeException) {
                    android.util.Log.w("FeaturedPaws", "Could not create preview", error)
                    avatar.visibility = View.VISIBLE
                }
            }
            preview?.setActive(true)
        }
    }
    private val cycle = object : Runnable {
        override fun run() {
            if (!active || detail || candidates.size < 2) return
            val motionAllowed = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
                android.animation.ValueAnimator.areAnimatorsEnabled()
            if (isShown && hasWindowFocus() && motionAllowed) {
                index = (index + 1) % candidates.size
                refresh()
            }
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
        copy.apply {
            orientation = VERTICAL
            setPadding(dp(6), 0, 0, 0)
        }
        copy.addView(eyebrow.apply {
            text = if (detail) "MEET THIS PAW" else "PAWS SPOTLIGHT"
            textSize = 9f
            setTextColor(0xFFFFDC79.toInt())
            setTypeface(Typeface.DEFAULT_BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        name.textSize = if (detail) 18f else 16f
        name.setTypeface(Typeface.DEFAULT_BOLD)
        name.setTextColor(Color.WHITE)
        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.END
        copy.addView(name)
        description.textSize = 11f
        description.setTextColor(0xFFFFEDCA.toInt())
        description.maxLines = 2
        copy.addView(description)
        LudoProofTheme.positive(action)
        AppFocusRing.apply(action, cornerRadiusDp = 12f)
        action.textSize = 11f
        action.minHeight = dp(48)
        action.minimumHeight = dp(48)
        action.setPadding(dp(4), 0, dp(4), 0)
        action.setOnClickListener { onAction(characterId) }
        copy.addView(action, LayoutParams(-1, dp(48)))
        addView(copy, LayoutParams(0, -2, 1f))
        refresh()
    }

    fun refresh() {
        val currentId = candidates.getOrNull(index)?.character?.id ?: preferredId
        val ownedIds = inventory.ownedCosmeticIds()
        candidates = CharacterDefinitions.all.filter { definition ->
            val id = definition.character.id
            val unlock = StoreCosmeticCatalog.characterUnlock(id)
            val offer = unlock?.let { StoreCosmeticCatalog.find(it.id) }
            CharacterRegistry.find(id) != null && (unlock == null || offer?.contentAvailable == true) &&
                (detail || unlock == null || unlock.id in ownedIds || BuildConfig.LUDOPROOF_ADS_ENABLED)
        }.sortedBy { definition ->
            val offer = StoreCosmeticCatalog.characterUnlock(definition.character.id)
            if (offer != null && offer.id !in ownedIds) 0 else 1
        }
        index = candidates.indexOfFirst { it.character.id == currentId }.coerceAtLeast(0)
        visibility = if (candidates.isEmpty()) View.GONE else View.VISIBLE
        val character = candidates.getOrNull(index)?.character ?: return
        preferredId = character.id
        name.text = character.displayName.uppercase()
        avatar.bind(characterId = character.id, ringColor = 0xFF5BE0FF.toInt(),
            highlighted = false, showCircularChrome = false)
        preview?.let { live ->
            if (live.characterId != character.id) {
                avatar.visibility = View.VISIBLE
                live.alpha = 0f
                live.characterId = character.id
            }
        }
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
            offer.unlockKind == CosmeticUnlockKind.REWARDED_ADS && progress < offer.requiredAdViews &&
                !BuildConfig.LUDOPROOF_ADS_ENABLED -> "ADS UNAVAILABLE"
            offer.unlockKind == CosmeticUnlockKind.REWARDED_ADS && progress < offer.requiredAdViews -> "WATCH AD · $progress/${offer.requiredAdViews}"
            offer.unlockKind == CosmeticUnlockKind.REWARDED_ADS -> "UNLOCK PAW"
            offer.unlockKind == CosmeticUnlockKind.GEMS -> "${offer.gemPrice} GEMS · UNLOCK"
            offer.unlockKind == CosmeticUnlockKind.FREE -> "GET PAW"
            else -> "UNAVAILABLE"
        }
        action.isEnabled = !detail || (!selected && (owned || offer != null && offer.contentAvailable &&
            (offer.unlockKind == CosmeticUnlockKind.FREE || offer.unlockKind == CosmeticUnlockKind.GEMS ||
                offer.unlockKind == CosmeticUnlockKind.REWARDED_ADS &&
                    (progress >= offer.requiredAdViews || BuildConfig.LUDOPROOF_ADS_ENABLED))))
        action.alpha = if (action.isEnabled) 1f else .65f
        contentDescription = "${character.displayName}. ${description.text}"
    }

    fun setActive(value: Boolean) {
        active = value
        removeCallbacks(cycle)
        removeCallbacks(startPreview)
        if (value) {
            refresh()
            if (candidates.isNotEmpty()) postDelayed(startPreview, 250L)
            val motionAllowed = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
                android.animation.ValueAnimator.areAnimatorsEnabled()
            if (!detail && motionAllowed) postDelayed(cycle, 12000L)
        } else {
            avatar.visibility = View.VISIBLE
            preview?.alpha = 0f
            preview?.setActive(false)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        if (available != configuredWidth) {
            configuredWidth = available
            val largeText = resources.configuration.fontScale >= 1.3f
            val stacked = detail && (available < dp(320) || largeText)
            orientation = if (stacked) VERTICAL else HORIZONTAL
            viewport.layoutParams = LayoutParams(if (stacked) -1 else
                minOf(dp(if (detail) 140 else 112), (available * .36f).toInt()), dp(if (detail) 164 else 124))
            copy.layoutParams = if (stacked) LayoutParams(-1, -2) else LayoutParams(0, -2, 1f)
            eyebrow.visibility = if (!detail && largeText) View.GONE else View.VISIBLE
            description.visibility = if (!detail && largeText) View.GONE else View.VISIBLE
            action.maxLines = 2
            action.ellipsize = TextUtils.TruncateAt.END
            action.layoutParams = LayoutParams(-1, if (detail && largeText) -2 else dp(48))
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onDetachedFromWindow() {
        setActive(false)
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
