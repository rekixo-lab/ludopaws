package com.rekixo.ludopaws.feature.store.presentation.components

import android.animation.ValueAnimator
import android.content.Context
import android.app.ActivityManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic

internal class GamePawsPromotionView(
    context: Context,
    private val gameplay: Boolean = true,
    private val onExplore: ((String, String?) -> Unit)? = null,
) : LinearLayout(context) {
    private data class Offer(val cosmetic: StoreCosmetic, val title: String, val characterId: String? = null)
    private val avatar = LudoPawsCharacterAvatarView(context)
    private val artwork = object : FrameLayout(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
    }
    private val name = TextView(context)
    private val storeLabel = TextView(context)
    private val categoryLabel = TextView(context)
    private val priceLabel = TextView(context)
    private val leftCopy = LinearLayout(context)
    private val rightCopy = LinearLayout(context)
    private var candidates = emptyList<Offer>()
    private var index = 0
    private var preview: FeaturedPawsPreview? = null
    private var extraPreview: View? = null
    private var promotionActive = true
    private var notice: Toast? = null
    private var protectedViews = emptyList<View>()
    private var spaceAvailable = true
    private val ownBounds = Rect()
    private val protectedBounds = Rect()
    private val location = IntArray(2)
    private val spaceObserver = ViewTreeObserver.OnPreDrawListener {
        updateSpace()
    }
    private val cycle = object : Runnable {
        override fun run() {
            if (!canPresentPromotion() || !animationsAllowed()) return
            if (candidates.size > 1) {
                index = (index + 1) % candidates.size
                bindCharacter()
            } else {
                postDelayed(this, if (gameplay) 12000L else 7000L)
            }
        }
    }
    private val startPreview = Runnable {
        if (canPresentPromotion() && candidates.getOrNull(index)?.characterId != null) {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (preview == null && manager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000) {
                try {
                    preview = FeaturedPawsPreview(context, if (gameplay) 66L else 50L, playful = true).also { live ->
                        live.characterId = candidates[index].characterId ?: "dog"
                        live.alpha = 0f
                        live.onReady = { live.alpha = 1f; avatar.visibility = INVISIBLE }
                        live.onUnavailable = {
                            live.alpha = 0f
                            if (candidates.getOrNull(index)?.characterId != null) avatar.visibility = VISIBLE
                        }
                        artwork.addView(live, FrameLayout.LayoutParams(-1, -1))
                    }
                } catch (error: RuntimeException) {
                    android.util.Log.w("GamePromotion", "Live preview unavailable", error)
                }
            }
            preview?.apply { visibility = VISIBLE; setActive(true) }
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = true
        clipToPadding = true
        setPadding(dp(4), dp(4), dp(4), dp(4))
        background = plywoodBackground(context, cornerRadiusDp = 12f)
        artwork.clipChildren = true
        artwork.addView(avatar, FrameLayout.LayoutParams(-1, -1))
        listOf(leftCopy, rightCopy).forEach { copy ->
            copy.orientation = VERTICAL
            copy.gravity = Gravity.CENTER
            copy.setPadding(dp(2), 0, dp(2), 0)
        }
        listOf(name, storeLabel, categoryLabel, priceLabel).forEach { label ->
            label.gravity = Gravity.CENTER
            label.includeFontPadding = false
            label.ellipsize = TextUtils.TruncateAt.END
        }
        leftCopy.apply {
            addView(categoryLabel.apply {
                textSize = 8f
                setTextColor(0xFFFFDC79.toInt())
                maxLines = 1
            }, LayoutParams(-1, -2))
            addView(name.apply {
                textSize = 10f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                maxLines = 2
            }, LayoutParams(-1, -2))
        }
        rightCopy.apply {
            addView(priceLabel.apply {
                textSize = 9f
                setTextColor(0xFFFFEDCA.toInt())
                maxLines = 2
            }, LayoutParams(-1, -2))
            addView(storeLabel.apply {
                text = if (gameplay) "IN STORE ›" else "EXPLORE ›"
                textSize = 9f
                setTextColor(0xFFFFDC79.toInt())
                setTypeface(Typeface.DEFAULT_BOLD)
                maxLines = 2
                setPadding(0, dp(4), 0, 0)
            }, LayoutParams(-1, -2))
        }
        addView(leftCopy, LayoutParams(0, -2, 1f))
        addView(artwork, LayoutParams(dp(64), -1))
        addView(rightCopy, LayoutParams(0, -2, 1f))
        isClickable = true
        isFocusable = true
        AppFocusRing.apply(this, cornerRadiusDp = 12f)
        setOnClickListener {
            val offer = candidates.getOrNull(index) ?: return@setOnClickListener
            if (!gameplay) {
                onExplore?.invoke(when (offer.cosmetic.category) {
                    CosmeticCategory.BOARD -> "BOARD"
                    CosmeticCategory.DICE -> "DICE"
                    else -> "PAWS"
                }, offer.characterId)
                return@setOnClickListener
            }
            notice?.cancel()
            notice = Toast.makeText(context,
                "Explore ${offer.title} in Store after your match.",
                Toast.LENGTH_SHORT).also { it.show() }
        }
    }

    private fun bindCharacter() {
        val offer = candidates.getOrNull(index) ?: return
        name.text = offer.title
        categoryLabel.text = if (offer.characterId != null) "MEET" else "DISCOVER"
        val owned = CosmeticInventoryStore(context).isOwned(offer.cosmetic.id)
        priceLabel.text = when {
            owned -> "YOURS TO PLAY"
            offer.cosmetic.gemPrice > 0 -> CosmeticPriceLabels.gems(context, offer.cosmetic.gemPrice)
            offer.cosmetic.requiredAdViews > 0 -> "${offer.cosmetic.requiredAdViews} ADS"
            else -> "MAKE IT YOURS"
        }
        preview?.setActive(false)
        preview?.alpha = 0f
        preview?.visibility = GONE
        extraPreview?.let { artwork.removeView(it) }
        extraPreview = null
        avatar.visibility = if (offer.characterId != null) VISIBLE else GONE
        if (offer.characterId != null) {
            avatar.bind(characterId = offer.characterId, ringColor = 0xFF5BE0FF.toInt(),
                highlighted = false, showCircularChrome = false)
            preview?.characterId = offer.characterId
        } else {
            extraPreview = when (offer.cosmetic.category) {
                CosmeticCategory.BOARD -> FrameLayout(context).apply {
                    addView(com.rekixo.ludopaws.LudoBoardView(context).apply {
                        setBoardThemeOverride(offer.cosmetic.id)
                        setClassicTokenDrawingEnabled(false)
                    }, FrameLayout.LayoutParams(-1, -1))
                    addView(com.rekixo.ludopaws.LudoPawsBoardChromeView(context).apply {
                        setBoardThemeOverride(offer.cosmetic.id)
                    }, FrameLayout.LayoutParams(-1, -1))
                }
                CosmeticCategory.DICE -> com.rekixo.ludopaws.DiceView(context).apply {
                    setPreviewStyle(offer.cosmetic.id)
                    showOutcome(6, animate = false)
                    setPlayerTeamColor(null)
                }
                else -> null
            }?.also { view ->
                view.isClickable = false
                view.isFocusable = false
                view.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                artwork.addView(view, FrameLayout.LayoutParams(-1, -1))
            }
        }
        contentDescription = "${offer.title}. ${if (gameplay) "Available in Store after your match" else "Explore in Store"}."
        updateMotion()
    }

    fun protect(views: List<View>) {
        protectedViews = views
    }

    private fun updateSpace(): Boolean {
        if (!gameplay) return true
        if (!isShown || width <= 0 || height <= 0) return true
        getLocationOnScreen(location)
        ownBounds.set(location[0], location[1], location[0] + width, location[1] + height)
        var availableHeight = dp(132)
        for (view in protectedViews) {
            if (view.isShown && view.getGlobalVisibleRect(protectedBounds) &&
                protectedBounds.left < ownBounds.right && protectedBounds.right > ownBounds.left &&
                protectedBounds.bottom > ownBounds.top) {
                availableHeight = minOf(availableHeight, protectedBounds.top - ownBounds.top - dp(8))
            }
        }
        val heightHost = (parent as? GamePawsPromotionSlot) ?: this
        val targetHeight = availableHeight.coerceIn(dp(52), dp(132))
        val params = heightHost.layoutParams
        if (params != null && params.height != targetHeight) {
            params.height = targetHeight
            heightHost.layoutParams = params
            return false
        }
        val available = width >= dp(120) && availableHeight >= dp(52) && height <= availableHeight
        if (available == spaceAvailable) return true
        spaceAvailable = available
        alpha = if (available) 1f else 0f
        isClickable = available
        isFocusable = available
        importantForAccessibility = if (available) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        updateMotion()
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        val portraitSize = minOf(availableHeight - dp(8), (availableWidth * .46f).toInt()).coerceAtLeast(dp(40))
        if (artwork.layoutParams.width != portraitSize || artwork.layoutParams.height != portraitSize) {
            artwork.layoutParams = LayoutParams(portraitSize, portraitSize)
        }
        name.textSize = if (availableWidth >= dp(300)) 16f else if (availableHeight >= dp(96)) 11f else 9f
        storeLabel.textSize = if (availableWidth >= dp(300)) 12f else 9f
        categoryLabel.textSize = if (availableWidth >= dp(300)) 10f else 8f
        priceLabel.textSize = if (availableWidth >= dp(300)) 13f else 9f
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun refreshCandidates() {
        val previousId = candidates.getOrNull(index)?.cosmetic?.id
        val ownedIds = CosmeticInventoryStore(context).ownedCosmeticIds()
        val characters = CharacterDefinitions.all.mapNotNull { definition ->
            val offer = StoreCosmeticCatalog.characterUnlock(definition.character.id)
                ?.let { StoreCosmeticCatalog.find(it.id) }
            if (CharacterRegistry.find(definition.character.id) != null && offer?.contentAvailable == true)
                Offer(offer, definition.character.displayName, definition.character.id) else null
        }
        val boards = StoreCosmeticCatalog.boards.filter { it.contentAvailable && it.id != StoreCosmeticCatalog.DEFAULT_BOARD }
            .map { Offer(it, it.title) }
        val dice = StoreCosmeticCatalog.dice.filter { it.contentAvailable && it.id != StoreCosmeticCatalog.DEFAULT_DICE }
            .map { Offer(it, it.title) }
        candidates = (0 until maxOf(characters.size, boards.size, dice.size)).flatMap { position ->
            listOfNotNull(characters.getOrNull(position), boards.getOrNull(position), dice.getOrNull(position))
        }.sortedBy { if (it.cosmetic.id in ownedIds) 1 else 0 }
        index = candidates.indexOfFirst { it.cosmetic.id == previousId }.coerceAtLeast(0)
        if (candidates.isEmpty()) visibility = GONE
        bindCharacter()
    }

    private fun canPresentPromotion(): Boolean = promotionActive && spaceAvailable && isAttachedToWindow && isShown && windowVisibility == VISIBLE && hasWindowFocus()

    private fun animationsAllowed(): Boolean = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

    fun setActive(value: Boolean) {
        if (promotionActive == value) return
        promotionActive = value
        if (value) refreshCandidates() else updateMotion()
    }

    private fun updateMotion() {
        removeCallbacks(cycle)
        removeCallbacks(startPreview)
        preview?.setActive(false)
        if (!canPresentPromotion()) return
        if (candidates.getOrNull(index)?.characterId != null) postDelayed(startPreview, 250L)
        if (animationsAllowed()) postDelayed(cycle, if (gameplay) 12000L else 7000L)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshCandidates()
        viewTreeObserver.addOnPreDrawListener(spaceObserver)
        updateMotion()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        updateMotion()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) updateMotion()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) updateMotion()
    }

    override fun onDetachedFromWindow() {
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(spaceObserver)
        removeCallbacks(cycle)
        removeCallbacks(startPreview)
        preview?.setActive(false)
        notice?.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}

internal class GamePawsPromotionSlot(context: Context, private val fallback: View?) : FrameLayout(context) {
    private val promotion = GamePawsPromotionView(context)

    init {
        clipChildren = true
        clipToPadding = true
        fallback?.let { addView(it, LayoutParams(-1, -1)) }
        addView(promotion, LayoutParams(-1, -1))
        setMatchActive(false)
    }

    fun setMatchActive(active: Boolean) {
        fallback?.visibility = if (active) GONE else VISIBLE
        promotion.visibility = if (active) VISIBLE else GONE
        promotion.setActive(active)
        if (!active && layoutParams != null && layoutParams.height != (52 * resources.displayMetrics.density + .5f).toInt()) {
            layoutParams = layoutParams.apply { height = (52 * resources.displayMetrics.density + .5f).toInt() }
        }
    }

    fun protect(views: List<View>) = promotion.protect(views)
}
