package com.rekixo.ludopaws.feature.store.presentation.components

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.app.ActivityManager
import android.graphics.Color
import android.graphics.Canvas
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
import com.rekixo.ludopaws.LudoPawsTapHandArt
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.feature.boards.rendering.GameplayBoardEnvironment
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.data.local.GemWalletStore
import com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction
import com.rekixo.ludopaws.feature.store.domain.SmartUnlockPolicy
import com.rekixo.ludopaws.feature.store.domain.StoreCatalog
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
                } else if (gameplay && child === avatar) {
                    child.translationY = side * (.10f - .08f * child.scaleY)
                }
            }
        }
        override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
    }
    // Gameplay art is anchored to the middle of this frame, independent of the copy widths.
    private val gameplayFrame = FrameLayout(context)
    private val name = TextView(context)
    private val storeLabel = Button(context)
    private val saveHintFrame = if (gameplay) SaveTapHintFrame(context) else null
    private var savedOfferId: String? = null
    private val categoryLabel = TextView(context)
    private val priceLabel = TextView(context)
    private val leftCopy = LinearLayout(context)
    private val rightCopy = LinearLayout(context)
    private val adProgress = TextView(context)
    // Lightweight native progress bar; no network asset or animation allocation.
    private val adMeter = object : View(context) {
        var completion = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }
        private val trackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD3B28A.toInt()
        }
        private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF55A13A.toInt()
        }
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val h = height.toFloat()
            val w = width.toFloat()
            canvas.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, trackPaint)
            if (completion > 0f) {
                canvas.drawRoundRect(0f, 0f, w * completion, h, h / 2f, h / 2f, fillPaint)
            }
        }
    }
    private var environmentBoardId: String? = null
    private var candidates = emptyList<Offer>()
    val hasLockedOffers: Boolean get() = candidates.isNotEmpty()
    var onAvailabilityChanged: ((Boolean) -> Unit)? = null
    private val inventoryPreferences = context.applicationContext.getSharedPreferences(StorePreferences.PREFS_NAME, Context.MODE_PRIVATE)
    private val refreshOwnedOffers = Runnable { refreshCandidates() }
    private val inventoryListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == StorePreferences.KEY_OWNED_COSMETICS || key == StorePreferences.KEY_BALANCE || key == StorePreferences.KEY_WALLET_ACCOUNT_ID ||
            key == StorePreferences.KEY_SELECTED_BOARD || key == "offline_selection_BOARD" || (key.startsWith("local_equipment_") && key.endsWith("_BOARD")) ||
            key.startsWith(StorePreferences.KEY_AD_PROGRESS_PREFIX)) {
            removeCallbacks(refreshOwnedOffers)
            post(refreshOwnedOffers)
        }
    }
    private var index = 0
    private var preview: FeaturedPawsPreview? = null
    private var extraPreview: View? = null
    private var promotionActive = true
    private var quietUntilMillis = 0L
    private val resumeAfterQuiet = Runnable { updateMotion() }

    fun quietFor(durationMillis: Long) {
        if (durationMillis <= 0L) return
        quietUntilMillis = maxOf(quietUntilMillis, android.os.SystemClock.uptimeMillis() + durationMillis)
        removeCallbacks(resumeAfterQuiet)
        removeCallbacks(cycle)
        removeCallbacks(startPreview)
        preview?.setActive(false)
        postDelayed(resumeAfterQuiet, (quietUntilMillis - android.os.SystemClock.uptimeMillis()).coerceAtLeast(1L))
    }
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
        if (canPresentPromotion() && animationsAllowed() && candidates.getOrNull(index)?.characterId != null) {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (preview == null && manager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000) {
                try {
                    preview = FeaturedPawsPreview(context, if (gameplay) 66L else 50L,
                        playful = true, prominent = !gameplay,
                        portraitZoomMultiplier = if (gameplay) 1.70f else 1.30f).also { live ->
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
        setPadding(if (gameplay) dp(6) else dp(4), dp(4), if (gameplay) dp(6) else dp(4), dp(4))
        // Each mode owns its surface so the carousel can swap cards cleanly.
        background = if (gameplay) GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xD92B4D36.toInt(), 0xB51C382D.toInt()),
        ).apply {
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), 0x707DA46A)
        } else plywoodBackground(context, cornerRadiusDp = 20f)
        artwork.clipChildren = true
        artwork.addView(avatar, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        listOf(leftCopy, rightCopy).forEach { copy ->
            copy.orientation = VERTICAL
            copy.gravity = Gravity.CENTER
            copy.setPadding(dp(2), 0, dp(2), 0)
            copy.background = null
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
                maxLines = if (gameplay) 1 else 2
                if (gameplay) {
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(0xFFFFE9AA.toInt())
                }
            }, LayoutParams(-1, -2))
            val actionButton = storeLabel.apply {
                text = if (gameplay) "STORE ›" else "EXPLORE ›"
                textSize = 9f
                setTextColor(0xFFFFDC79.toInt())
                setTypeface(Typeface.DEFAULT_BOLD)
                maxLines = 2
                setPadding(0, dp(4), 0, 0)
                if (gameplay) {
                    maxLines = 1
                    letterSpacing = .03f
                    setTextColor(Color.WHITE)
                    setPadding(dp(8), 0, dp(8), 0)
                    background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(0xB2539142.toInt(), 0xB22D6C38.toInt())).apply {
                        cornerRadius = dp(9).toFloat()
                        setStroke(dp(1), 0xB08FCB79.toInt())
                    }
                }
            }
            if (gameplay) {
                val frame = checkNotNull(saveHintFrame)
                frame.addView(actionButton, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ))
                addView(frame, LayoutParams(-1, -2).apply { topMargin = dp(4) })
            } else {
                addView(actionButton, LayoutParams(-1, -2))
            }
            storeLabel.apply {
                isAllCaps = false
                backgroundTintList = null
                minWidth = 0; minimumWidth = 0
                if (!gameplay) {
                    minHeight = dp(48)
                    minimumHeight = dp(48)
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                    setTextColor(0xFFFFF3DA.toInt())
                    typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
                    background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(0xFF28644E.toInt(), 0xFF164333.toInt())).apply {
                        cornerRadius = dp(13).toFloat()
                        setStroke(dp(1), 0xFFCDA05F.toInt())
                    }
                } else { minHeight = dp(28); minimumHeight = dp(28) }
                setOnClickListener { this@GamePawsPromotionView.performClick() }
            }
        }
        if (gameplay) {
            gameplayFrame.apply {
                clipChildren = false
                clipToPadding = false
                addView(leftCopy, FrameLayout.LayoutParams(dp(88), -1,
                    Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(3) })
                addView(artwork, FrameLayout.LayoutParams(dp(76), dp(76), Gravity.CENTER))
                addView(rightCopy, FrameLayout.LayoutParams(dp(88), -1,
                    Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(3) })
            }
            addView(gameplayFrame, LayoutParams(-1, -1))
        } else {
            // Keep the offer details beside the artwork and the action below both.
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            rightCopy.removeAllViews()
            leftCopy.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            leftCopy.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF263E4E.toInt(), 0xFF132935.toInt()),
            ).apply {
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), 0xFFD6AE70.toInt())
            }
            leftCopy.setPaddingRelative(dp(10), dp(12), dp(10), dp(12))
            listOf(categoryLabel, name, priceLabel).forEach {
                it.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
            categoryLabel.apply {
                letterSpacing = .08f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFF6CE83.toInt())
            }
            name.apply {
                typeface = Typeface.create(Typeface.DEFAULT, 800, false)
                letterSpacing = -.015f
                setTextColor(0xFFFFF5DF.toInt())
                setPadding(0, dp(6), 0, 0)
            }
            priceLabel.apply {
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(0xFFC0F0DA.toInt())
            }
            leftCopy.addView(priceLabel, LayoutParams(-1, -2).apply { topMargin = dp(8) })
            leftCopy.addView(adProgress.apply {
                textSize = 11.5f
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(0xFFE6C998.toInt())
                maxLines = 1
            }, LayoutParams(-1, -2).apply { topMargin = dp(7) })
            leftCopy.addView(adMeter.apply {
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(-1, dp(5)).apply { topMargin = dp(5) })
            val heroRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            heroRow.addView(leftCopy, LayoutParams(0, -2, 0.55f).apply {
                marginStart = dp(2)
            })
            heroRow.addView(artwork, LayoutParams(0, -1, 0.45f))
            addView(heroRow, LayoutParams(-1, 0, 1f))
            storeLabel.apply {
                minHeight = dp(48)
                minimumHeight = dp(48)
                setPadding(dp(8), 0, dp(8), 0)
                gravity = Gravity.CENTER
            }
            addView(storeLabel, LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        }
        isClickable = true
        isFocusable = true
        AppFocusRing.apply(this, cornerRadiusDp = 12f)
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
            savedOfferId = offer.cosmetic.id
            storeLabel.text = "Saved ✓"
            saveHintFrame?.setHintActive(false)
            storeLabel.contentDescription = "${offer.title} saved for after the match"
            contentDescription = "${offer.title} saved for after the match"
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
        (background as? GradientDrawable)?.apply {
            colors = intArrayOf(tint(environment.panelTop, 217), tint(environment.panelBottom, 181))
            setStroke(dp(1), tint(environment.accent, 112))
        }
        name.setTextColor(environment.title)
        name.setShadowLayer(dp(1).toFloat(), 0f, dp(1).toFloat(), tint(environment.panelBottom, 180))
        categoryLabel.setTextColor(environment.accent)
        priceLabel.setTextColor(environment.price)
        storeLabel.setTextColor(Color.WHITE)
        (storeLabel.background as? GradientDrawable)?.apply {
            setColor(tint(environment.accent, 82))
            setStroke(dp(1), tint(environment.accent, 164))
        }
    }

    private fun bindCharacter() {
        val offer = candidates.getOrNull(index) ?: return
        name.text = offer.title
        categoryLabel.text = if (!gameplay) {
            when (offer.cosmetic.category) {
                CosmeticCategory.BOARD -> "FEATURED BOARD"
                CosmeticCategory.DICE -> "FEATURED DICE"
                else -> "FEATURED PAW"
            }
        } else when (offer.cosmetic.category) {
            CosmeticCategory.BOARD -> "BOARD PICK"
            CosmeticCategory.DICE -> "DICE PICK"
            else -> "PAWS PICK"
        }
        val done = CosmeticInventoryStore(context).rewardedAdProgress(offer.cosmetic.id)
            .coerceAtMost(offer.cosmetic.requiredAdViews)
        val decision = SmartUnlockPolicy.decide(
            cosmetic = offer.cosmetic,
            owned = false,
            gems = GemWalletStore(context).balance(),
            adProgress = done,
            playAvailable = BuildConfig.LUDOPROOF_BILLING_ENABLED &&
                StoreCatalog.cosmeticProductId(offer.cosmetic.id) != null,
            adsAvailable = BuildConfig.LUDOPROOF_ADS_ENABLED &&
                BuildConfig.ADMOB_REWARDED_AD_UNIT_ID.isNotBlank(),
        )
        priceLabel.text = if (gameplay) when (decision.primary) {
            SmartUnlockAction.GEMS -> "${offer.cosmetic.gemPrice} Gems"
            SmartUnlockAction.PLAY_PRODUCT -> "Play Store"
            SmartUnlockAction.WATCH_AD -> "$done/${offer.cosmetic.requiredAdViews} Ads"
            SmartUnlockAction.CLAIM_ADS -> "Ads ready"
            SmartUnlockAction.GET_GEMS -> "Get Gems"
            SmartUnlockAction.FREE -> "Free"
            else -> "See offer"
        } else when (decision.primary) {
            SmartUnlockAction.GEMS -> "${offer.cosmetic.gemPrice} Gems to unlock"
            SmartUnlockAction.PLAY_PRODUCT -> "One-time Google Play purchase"
            SmartUnlockAction.WATCH_AD -> "Unlock by watching ads"
            SmartUnlockAction.CLAIM_ADS -> "Ads complete. Ready to unlock."
            SmartUnlockAction.GET_GEMS -> "${offer.cosmetic.gemPrice} Gems needed"
            SmartUnlockAction.FREE -> "Free to unlock"
            else -> "See details in the Shop"
        }
        adProgress.text = if (!gameplay && offer.cosmetic.requiredAdViews > 0 &&
            (decision.primary == SmartUnlockAction.WATCH_AD || decision.primary == SmartUnlockAction.CLAIM_ADS)) {
            "$done / ${offer.cosmetic.requiredAdViews} ads watched"
        } else ""
        adProgress.visibility = if (adProgress.text.isBlank()) GONE else VISIBLE
        adMeter.visibility = if (adProgress.text.isNotBlank()) VISIBLE else GONE
        adMeter.completion = if (offer.cosmetic.requiredAdViews > 0) {
            done.toFloat() / offer.cosmetic.requiredAdViews
        } else 0f
        val offerSaved = gameplay && savedOfferId == offer.cosmetic.id
        val homeAction = decision.primary.takeIf {
            !gameplay && offer.cosmetic.purchaseAvailable && com.rekixo.ludopaws.ConnectivityMonitor(context) { }.isOnline()
        }
        val actionText = when {
            offerSaved -> "Saved ✓"
            gameplay -> "Save"
            else -> when (homeAction) {
                null -> if (offer.cosmetic.purchaseAvailable) "Connect to unlock" else "View in Shop"
                SmartUnlockAction.PLAY_PRODUCT -> "Buy"
                SmartUnlockAction.WATCH_AD -> "Watch Ad"
                SmartUnlockAction.GET_GEMS -> "Get Gems"
                SmartUnlockAction.GEMS, SmartUnlockAction.CLAIM_ADS, SmartUnlockAction.FREE -> "Unlock"
                else -> "View in Shop"
            }
        }
        storeLabel.text = when (homeAction) {
            SmartUnlockAction.GEMS, SmartUnlockAction.GET_GEMS -> StoreActionLabels.gems(context, actionText, 16)
            null -> actionText
            else -> StoreActionLabels.action(context, actionText, homeAction, 16)
        }
        storeLabel.contentDescription = when {
            offerSaved -> "${offer.title} saved for after the match"
            gameplay -> "Save ${offer.title} for after the match"
            else -> actionText
        }
        preview?.setActive(false)
        preview?.alpha = 0f
        preview?.visibility = GONE
        extraPreview?.let { artwork.removeView(it) }
        extraPreview = null
        avatar.visibility = if (offer.characterId != null) VISIBLE else GONE
        if (offer.characterId != null) {
            if (!gameplay) {
                val zoom = (homeSpotlightPortraitZoom(offer.characterId) * 1.30f).coerceAtMost(1.55f)
                avatar.scaleX = zoom
                avatar.scaleY = zoom
                val side = minOf(artwork.width, artwork.height)
                avatar.translationY = side * (.20f - .12f * zoom)
            } else {
                val zoom = compactSpotlightPortraitZoom(offer.characterId) * 1.70f
                avatar.scaleX = zoom
                avatar.scaleY = zoom
                val side = minOf(artwork.width, artwork.height)
                avatar.translationY = side * (.10f - .08f * zoom)
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
        contentDescription = if (gameplay) {
            val unlockDetail = when (decision.primary) {
                SmartUnlockAction.GEMS -> "${offer.cosmetic.gemPrice} Gems to unlock"
                SmartUnlockAction.PLAY_PRODUCT -> "Buy with Google Play"
                SmartUnlockAction.WATCH_AD -> "$done of ${offer.cosmetic.requiredAdViews} ads watched"
                SmartUnlockAction.CLAIM_ADS -> "Required ads completed"
                SmartUnlockAction.GET_GEMS -> "More Gems needed to unlock"
                SmartUnlockAction.FREE -> "Free unlock"
                else -> "Offer details available after the match"
            }
            if (offerSaved) "${offer.title} saved for after the match."
            else "${offer.title}. $unlockDetail. Save this offer until after your match."
        } else "${offer.title}. ${priceLabel.text}. $actionText."
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
        var availableHeight = dp(84)
        for (view in protectedViews) {
            if (view.isShown && view.getGlobalVisibleRect(protectedBounds) &&
                protectedBounds.left < ownBounds.right && protectedBounds.right > ownBounds.left &&
                protectedBounds.bottom > ownBounds.top) {
                availableHeight = minOf(availableHeight, protectedBounds.top - ownBounds.top - dp(8))
            }
        }
        val heightHost = (parent as? GamePawsPromotionSlot) ?: this
        val targetHeight = availableHeight.coerceIn(dp(52), dp(84))
        val params = heightHost.layoutParams
        if (params != null && params.height != targetHeight) {
            params.height = targetHeight
            heightHost.layoutParams = params
            return false
        }
        val available = width >= dp(220) && availableHeight >= dp(58) && height <= availableHeight
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
            val contentWidth = (availableWidth - paddingLeft - paddingRight).coerceAtLeast(dp(1))
            val portraitSize = minOf(dp(78), availableHeight - paddingTop - paddingBottom,
                contentWidth / 3).coerceAtLeast(dp(36))
            val copyWidth = ((contentWidth - portraitSize) / 2 - dp(10)).coerceAtLeast(dp(42))
            val artParams = artwork.layoutParams as FrameLayout.LayoutParams
            if (artParams.width != portraitSize || artParams.height != portraitSize) {
                artwork.layoutParams = FrameLayout.LayoutParams(portraitSize, portraitSize, Gravity.CENTER)
            }
            for (copy in listOf(leftCopy, rightCopy)) {
                val params = copy.layoutParams as FrameLayout.LayoutParams
                if (params.width != copyWidth) {
                    copy.layoutParams = FrameLayout.LayoutParams(copyWidth, -1,
                        if (copy === leftCopy) Gravity.START or Gravity.CENTER_VERTICAL
                        else Gravity.END or Gravity.CENTER_VERTICAL).apply {
                        if (copy === leftCopy) marginStart = dp(3) else marginEnd = dp(3)
                    }
                }
            }
            val roomy = contentWidth >= dp(270) && availableHeight >= dp(66)
            name.textSize = if (roomy) 13f else 11f
            // Avoid requestLayout loops from repeatedly changing maxLines inside measure.
            if (name.maxLines != 2) name.maxLines = 2
            storeLabel.textSize = if (roomy) 12f else 10f
            categoryLabel.textSize = if (roomy) 9f else 8f
            priceLabel.textSize = if (roomy) 11.5f else 10f
            categoryLabel.visibility = if (availableHeight >= dp(58)) VISIBLE else GONE
            storeLabel.visibility = VISIBLE
        } else {
            name.textSize = if (availableWidth >= dp(315)) 23f else 19f
            storeLabel.textSize = 16f
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

    private fun canPresentPromotion(): Boolean = android.os.SystemClock.uptimeMillis() >= quietUntilMillis && hasLockedOffers && promotionActive && spaceAvailable && isAttachedToWindow && isShown && windowVisibility == VISIBLE && hasWindowFocus()

    private fun animationsAllowed(): Boolean = !com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore(context).snapshot().reducedMotion &&
        (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled())

    fun setActive(value: Boolean) {
        if (promotionActive == value) return
        promotionActive = value
        if (value) refreshCandidates() else updateMotion()
    }

    private fun updateMotion() {
        removeCallbacks(cycle)
        removeCallbacks(startPreview)
        preview?.setActive(false)
        removeCallbacks(resumeAfterQuiet)
        val quietRemaining = quietUntilMillis - android.os.SystemClock.uptimeMillis()
        saveHintFrame?.setHintActive(
            quietRemaining <= 0L && canPresentPromotion() && animationsAllowed() &&
                storeLabel.text.toString() == "Save",
        )
        if (quietRemaining > 0L) {
            if (isAttachedToWindow) postDelayed(resumeAfterQuiet, quietRemaining)
            return
        }
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
        saveHintFrame?.setHintActive(false)
        removeCallbacks(resumeAfterQuiet)
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

/**
 * Intermittent non-clickable hand cue beside Save. The actual pill keeps
 * WRAP_CONTENT width, while the frame provides a place to draw the hand
 * outside the text. The same artwork and 3.6s/1.4s cadence as pawn hints.
 */
private class SaveTapHintFrame(context: Context) : FrameLayout(context) {
    private val hand = LudoPawsTapHandArt(context)
    private var hintActive = false
    private val ticker = object : Runnable {
        override fun run() {
            if (!hintActive || !isAttachedToWindow || !isShown || windowVisibility != VISIBLE) return
            invalidate()
            val phase = android.os.SystemClock.uptimeMillis() % 3600L
            postDelayed(this, if (phase < 1400L) 48L else 3600L - phase)
        }
    }

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setHintActive(value: Boolean) {
        if (hintActive == value) return
        hintActive = value
        refreshTicker()
    }

    private fun refreshTicker() {
        removeCallbacks(ticker)
        invalidate()
        if (hintActive && isAttachedToWindow && isShown && windowVisibility == VISIBLE) {
            postOnAnimation(ticker)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshTicker()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        refreshTicker()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        refreshTicker()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!hintActive || !isShown || childCount == 0) return
        val phase = android.os.SystemClock.uptimeMillis() % 3600L
        if (phase >= 1400L) return
        val button = getChildAt(0)
        if (button.width <= 0 || button.height <= 0) return
        val fade = minOf(1f, phase / 220f, (1400L - phase) / 240f).coerceAtLeast(0f)
        val tapProgress = kotlin.math.sin(phase / 1400.0 * Math.PI).toFloat()
        hand.draw(
            canvas,
            button.right - dp(2).toFloat(),
            button.top + button.height * .48f + dp(3) * tapProgress,
            dp(17).toFloat(),
            (230f * fade).toInt(),
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + .5f).toInt()
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
        // In a match the offer gets its own full-width row beneath Back/Settings.
        // In a lobby the original centered title remains in the 52dp control row.
        val params = layoutParams as? FrameLayout.LayoutParams
        if (params != null) {
            val density = resources.displayMetrics.density
            fun dp(value: Int) = (value * density + .5f).toInt()
            val side = dp(if (active) 12 else 76)
            val top = dp(if (active) 70 else 14)
            if (params.topMargin != top || params.leftMargin != side || params.rightMargin != side) {
                params.width = LayoutParams.MATCH_PARENT
                params.height = dp(if (active) 84 else 52)
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.topMargin = top
                params.leftMargin = side
                params.rightMargin = side
                layoutParams = params
            }
        }
    }

    private fun updateContentVisibility() {
        fallback?.visibility = if (matchActive) GONE else VISIBLE
        promotion.visibility = if (matchActive && promotion.hasLockedOffers) VISIBLE else GONE
        visibility = if (matchActive && !promotion.hasLockedOffers) GONE else VISIBLE
    }

    fun protect(views: List<View>) = promotion.protect(views)
    fun quietFor(durationMillis: Long) = promotion.quietFor(durationMillis)
}
