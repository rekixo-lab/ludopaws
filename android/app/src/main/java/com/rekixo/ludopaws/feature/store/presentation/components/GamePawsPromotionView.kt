package com.rekixo.ludopaws.feature.store.presentation.components

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.app.ActivityManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.feature.boards.rendering.GameplayBoardEnvironment
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.data.local.StorePreferences
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
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val side = minOf(measuredWidth, measuredHeight)
            val exact = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY)
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                (child.layoutParams as? FrameLayout.LayoutParams)?.gravity = Gravity.CENTER
                if (!gameplay || child !is FeaturedPawsPreview) child.measure(exact, exact)
                if (!gameplay && child === avatar) {
                    // Cached portraits use anchor .62 and zoom 1. Match the
                    // live preview's .70 anchor after scaling about the center.
                    child.translationY = side * (.20f - .12f * child.scaleY)
                }
            }
        }
        override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
    }
    private val name = TextView(context)
    private val storeLabel = Button(context)
    private val categoryLabel = TextView(context)
    private val priceLabel = TextView(context)
    private val leftCopy = LinearLayout(context)
    private val rightCopy = LinearLayout(context)
    private val adProgress = TextView(context)
    private var environmentBoardId: String? = null
    private var candidates = emptyList<Offer>()
    val hasLockedOffers: Boolean get() = candidates.isNotEmpty()
    var onAvailabilityChanged: ((Boolean) -> Unit)? = null
    private val inventoryPreferences = context.applicationContext.getSharedPreferences(StorePreferences.PREFS_NAME, Context.MODE_PRIVATE)
    private val refreshOwnedOffers = Runnable { refreshCandidates() }
    private val inventoryListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == StorePreferences.KEY_OWNED_COSMETICS || key == StorePreferences.KEY_WALLET_ACCOUNT_ID ||
            key == StorePreferences.KEY_SELECTED_BOARD || key == "offline_selection_BOARD" ||
            key.startsWith(StorePreferences.KEY_AD_PROGRESS_PREFIX)) {
            removeCallbacks(refreshOwnedOffers)
            post(refreshOwnedOffers)
        }
    }
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
            refreshCandidates(advance = true)
        }
    }
    private val startPreview = Runnable {
        if (canPresentPromotion() && candidates.getOrNull(index)?.characterId != null) {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (preview == null && manager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000) {
                try {
                    preview = FeaturedPawsPreview(context, if (gameplay) 66L else 50L, playful = true, prominent = !gameplay).also { live ->
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
        background = if (gameplay) null else recessedPlywoodBackground(context, outerCornerRadiusDp = 20f)
        artwork.clipChildren = true
        artwork.addView(avatar, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        listOf(leftCopy, rightCopy).forEach { copy ->
            copy.orientation = VERTICAL
            copy.gravity = Gravity.CENTER
            copy.setPadding(dp(2), 0, dp(2), 0)
            if (gameplay) {
                copy.setPadding(dp(5), dp(6), dp(5), dp(6))
                copy.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0xD92B4D36.toInt(), 0xB51C382D.toInt())).apply {
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), 0x507DA46A)
                }
            }
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
                if (gameplay) {
                    letterSpacing = .09f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(0xFFBFE3A1.toInt())
                }
            }, LayoutParams(-1, -2))
            addView(name.apply {
                textSize = 10f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                maxLines = 2
                if (gameplay) {
                    setTextColor(0xFFFFF2D3.toInt())
                    setShadowLayer(dp(1).toFloat(), 0f, dp(1).toFloat(), 0xA610251C.toInt())
                    setPadding(0, dp(3), 0, 0)
                }
            }, LayoutParams(-1, -2))
        }
        rightCopy.apply {
            addView(priceLabel.apply {
                textSize = 9f
                setTextColor(0xFFFFEDCA.toInt())
                maxLines = 2
                if (gameplay) {
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(0xFFFFE9AA.toInt())
                }
            }, LayoutParams(-1, -2))
            addView(storeLabel.apply {
                text = if (gameplay) "STORE ›" else "EXPLORE ›"
                textSize = 9f
                setTextColor(0xFFFFDC79.toInt())
                setTypeface(Typeface.DEFAULT_BOLD)
                maxLines = 2
                setPadding(0, dp(4), 0, 0)
                if (gameplay) {
                    maxLines = 1
                    letterSpacing = .03f
                    setTextColor(0xFFD7F1BD.toInt())
                    setPadding(dp(2), dp(4), dp(2), dp(4))
                    background = GradientDrawable().apply {
                        setColor(0x485B904D)
                        cornerRadius = dp(8).toFloat()
                        setStroke(dp(1), 0x708FCB79)
                    }
                }
            }, LayoutParams(-1, -2).apply { if (gameplay) topMargin = dp(5) })
            storeLabel.apply {
                isAllCaps = false
                backgroundTintList = null
                minWidth = 0; minimumWidth = 0
                if (!gameplay) {
                    minHeight = dp(48)
                    minimumHeight = dp(48)
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                    setTextColor(Color.WHITE)
                    background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(0xFF80CD36.toInt(), 0xFF2D821C.toInt())).apply {
                        cornerRadius = dp(14).toFloat()
                    }
                } else { minHeight = 0; minimumHeight = 0 }
                setOnClickListener { this@GamePawsPromotionView.performClick() }
            }
        }
        if (gameplay) {
            addView(leftCopy, LayoutParams(0, -2, 1f))
            addView(artwork, LayoutParams(dp(64), -1))
            addView(rightCopy, LayoutParams(0, -2, 1f))
        } else {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(20), dp(12), dp(12))
            leftCopy.setPadding(dp(2), 0, dp(2), dp(6))
            addView(leftCopy, LayoutParams(-1, -2))
            addView(artwork, LayoutParams(-1, 0, 1f))
            rightCopy.removeAllViews()
            rightCopy.orientation = HORIZONTAL
            rightCopy.gravity = Gravity.CENTER_VERTICAL
            val offerCopy = LinearLayout(context).apply {
                orientation = VERTICAL
                addView(priceLabel, LayoutParams(-1, -2))
                addView(adProgress.apply {
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF60452D.toInt())
                }, LayoutParams(-1, -2))
            }
            rightCopy.addView(offerCopy, LayoutParams(0, -2, 1f))
            rightCopy.addView(storeLabel, LayoutParams(dp(142), -2).apply { marginStart = dp(8) })
            addView(rightCopy, LayoutParams(-1, -2))
        }
        isClickable = true
        isFocusable = true
        AppFocusRing.apply(this, cornerRadiusDp = 12f)
        if (!gameplay) {
            name.setTextColor(0xFF422B1C.toInt())
            priceLabel.setTextColor(0xFF422B1C.toInt())
            categoryLabel.setTextColor(0xFF60452D.toInt())
        }
        applyEnvironmentStyle()
        setOnClickListener {
            val offer = candidates.getOrNull(index) ?: return@setOnClickListener
            if (CosmeticInventoryStore(context).isOwned(offer.cosmetic.id)) {
                refreshCandidates()
                return@setOnClickListener
            }
            if (!gameplay) {
                onExplore?.invoke(when (offer.cosmetic.category) {
                    CosmeticCategory.BOARD -> "BOARD"
                    CosmeticCategory.DICE -> "DICE"
                    else -> "PAWS"
                }, offer.cosmetic.id)
                return@setOnClickListener
            }
            com.rekixo.ludopaws.feature.store.presentation.PromotionInterest.save(context, offer.cosmetic.id)
            storeLabel.text = "Saved ✓"
            notice?.cancel()
            notice = Toast.makeText(context, "${offer.title} saved. We’ll offer its details after the match.",
                Toast.LENGTH_SHORT).also { it.show() }
        }
    }

    private fun applyEnvironmentStyle() {
        if (!gameplay) return
        val environment = GameplayBoardEnvironment.resolve(CosmeticInventoryStore(context).selectedId(CosmeticCategory.BOARD))
        if (environmentBoardId == environment.boardId) return
        environmentBoardId = environment.boardId
        fun tint(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)
        listOf(leftCopy, rightCopy).forEach { copy ->
            (copy.background as? GradientDrawable)?.apply {
                colors = intArrayOf(tint(environment.panelTop, 217), tint(environment.panelBottom, 181))
                setStroke(dp(1), tint(environment.accent, 80))
            }
        }
        name.setTextColor(environment.title)
        name.setShadowLayer(dp(1).toFloat(), 0f, dp(1).toFloat(), tint(environment.panelBottom, 180))
        categoryLabel.setTextColor(environment.accent)
        priceLabel.setTextColor(environment.price)
        storeLabel.setTextColor(environment.accent)
        (storeLabel.background as? GradientDrawable)?.apply {
            setColor(tint(environment.accent, 36))
            setStroke(dp(1), tint(environment.accent, 112))
        }
    }

    private fun bindCharacter() {
        val offer = candidates.getOrNull(index) ?: return
        name.text = if (gameplay) offer.title else "Unlock ${offer.title}"
        categoryLabel.text = if (!gameplay) {
            when (offer.cosmetic.category) {
                CosmeticCategory.BOARD -> "BOARD SPOTLIGHT"
                CosmeticCategory.DICE -> "DICE SPOTLIGHT"
                else -> "CHARACTER SPOTLIGHT"
            }
        } else when (offer.cosmetic.category) {
            CosmeticCategory.BOARD -> "BOARD PICK"
            CosmeticCategory.DICE -> "DICE PICK"
            else -> "PAWS PICK"
        }
        val done = CosmeticInventoryStore(context).rewardedAdProgress(offer.cosmetic.id)
            .coerceAtMost(offer.cosmetic.requiredAdViews)
        val remaining = (offer.cosmetic.requiredAdViews - done).coerceAtLeast(0)
        priceLabel.text = when {
            offer.cosmetic.gemPrice > 0 && remaining > 0 ->
                CosmeticPriceLabels.gems(context, offer.cosmetic.gemPrice, " or ads")
            offer.cosmetic.gemPrice > 0 -> CosmeticPriceLabels.gems(context, offer.cosmetic.gemPrice)
            offer.cosmetic.requiredAdViews > 0 -> "$done / ${offer.cosmetic.requiredAdViews} ads completed"
            else -> "View unlock options"
        }
        adProgress.text = if (offer.cosmetic.requiredAdViews > 0 && offer.cosmetic.gemPrice > 0)
            "$done / ${offer.cosmetic.requiredAdViews} ads completed" else ""
        adProgress.visibility = if (adProgress.text.isBlank()) GONE else VISIBLE
        storeLabel.text = when {
            gameplay -> "View after match"
            remaining > 0 && offer.cosmetic.gemPrice == 0 && BuildConfig.LUDOPROOF_ADS_ENABLED -> "Unlock with Ads ›"
            offer.cosmetic.gemPrice > 0 && remaining == 0 -> "View & Unlock ›"
            else -> "Unlock Options ›"
        }
        preview?.setActive(false)
        preview?.alpha = 0f
        preview?.visibility = GONE
        extraPreview?.let { artwork.removeView(it) }
        extraPreview = null
        avatar.visibility = if (offer.characterId != null) VISIBLE else GONE
        if (offer.characterId != null) {
            if (!gameplay) {
                val zoom = homeSpotlightPortraitZoom(offer.characterId)
                avatar.scaleX = zoom
                avatar.scaleY = zoom
                val side = minOf(artwork.width, artwork.height)
                avatar.translationY = side * (.20f - .12f * zoom)
            }
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
                    showOutcome(com.rekixo.ludopaws.feature.dice.domain.DiceRegistry.resolve(offer.cosmetic.id).previewFace, animate = false)
                    setPlayerTeamColor(null)
                }
                else -> null
            }?.also { view ->
                view.isClickable = false
                view.isFocusable = false
                view.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                artwork.addView(view, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
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
        updateOfferVisibility()
        updateMotion()
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        if (gameplay) {
            val portraitSize = minOf(availableHeight - dp(8), (availableWidth * .36f).toInt()).coerceAtLeast(dp(40))
            if (artwork.layoutParams.width != portraitSize || artwork.layoutParams.height != portraitSize)
                artwork.layoutParams = LayoutParams(portraitSize, portraitSize)
        }
        if (gameplay) {
            val roomy = availableWidth >= dp(300) && availableHeight >= dp(96)
            name.textSize = if (roomy) 15f else if (availableHeight >= dp(96)) 11f else 9f
            // TextView.setMaxLines requests another layout even for the same
            // value. Doing that on every measure prevents the UI becoming idle.
            val titleLines = if (availableHeight >= dp(96)) 3 else 2
            if (name.maxLines != titleLines) name.maxLines = titleLines
            storeLabel.textSize = if (roomy) 11f else 9f
            categoryLabel.textSize = if (roomy) 9f else 7f
            priceLabel.textSize = if (roomy) 13f else 9f
            categoryLabel.visibility = if (availableHeight >= dp(76)) VISIBLE else GONE
            storeLabel.visibility = VISIBLE
        } else {
            name.textSize = 20f
            storeLabel.textSize = 14f
            categoryLabel.textSize = 10f
            priceLabel.textSize = 13f
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun refreshOffers() = refreshCandidates()

    private fun updateOfferVisibility() {
        val available = spaceAvailable && hasLockedOffers
        alpha = if (available) 1f else 0f
        isClickable = available
        isFocusable = available
        storeLabel.isEnabled = available
        storeLabel.isClickable = available
        importantForAccessibility = if (available) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private fun refreshCandidates(advance: Boolean = false) {
        applyEnvironmentStyle()
        val previouslyAvailable = hasLockedOffers
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
        }.filter { it.cosmetic.id !in ownedIds }
        val previousIndex = candidates.indexOfFirst { it.cosmetic.id == previousId }
        index = if (advance && previousIndex >= 0) (previousIndex + 1) % candidates.size else previousIndex.coerceAtLeast(0)
        updateOfferVisibility()
        if (hasLockedOffers) bindCharacter() else {
            name.text = ""
            priceLabel.text = ""
            contentDescription = ""
            preview?.alpha = 0f
            extraPreview?.let { artwork.removeView(it) }
            extraPreview = null
            updateMotion()
        }
        if (previouslyAvailable != hasLockedOffers) onAvailabilityChanged?.invoke(hasLockedOffers)
    }

    private fun canPresentPromotion(): Boolean = hasLockedOffers && promotionActive && spaceAvailable && isAttachedToWindow && isShown && windowVisibility == VISIBLE && hasWindowFocus()

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
        inventoryPreferences.registerOnSharedPreferenceChangeListener(inventoryListener)
        refreshCandidates()
        viewTreeObserver.addOnPreDrawListener(spaceObserver)
        updateMotion()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && isAttachedToWindow) refreshCandidates() else updateMotion()
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
        inventoryPreferences.unregisterOnSharedPreferenceChangeListener(inventoryListener)
        removeCallbacks(refreshOwnedOffers)
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
    private var matchActive = false

    init {
        clipChildren = true
        clipToPadding = true
        fallback?.let { addView(it, LayoutParams(-1, -1)) }
        addView(promotion, LayoutParams(-1, -1))
        promotion.onAvailabilityChanged = { updateContentVisibility() }
        setMatchActive(false)
    }

    fun setMatchActive(active: Boolean) {
        matchActive = active
        updateContentVisibility()
        promotion.setActive(active)
        if (!active && layoutParams != null && layoutParams.height != (52 * resources.displayMetrics.density + .5f).toInt()) {
            layoutParams = layoutParams.apply { height = (52 * resources.displayMetrics.density + .5f).toInt() }
        }
    }

    private fun updateContentVisibility() {
        fallback?.visibility = if (matchActive) GONE else VISIBLE
        promotion.visibility = if (matchActive && promotion.hasLockedOffers) VISIBLE else GONE
        visibility = if (matchActive && !promotion.hasLockedOffers) GONE else VISIBLE
    }

    fun protect(views: List<View>) = promotion.protect(views)
}
