package com.rekixo.ludopaws.feature.store.presentation.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
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
import com.rekixo.ludopaws.feature.characters.domain.model.CharacterDefinition
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog

internal class GamePawsPromotionView(context: Context) : LinearLayout(context) {
    private val avatar = LudoPawsCharacterAvatarView(context)
    private val name = TextView(context)
    private val storeLabel = TextView(context)
    private var candidates = emptyList<CharacterDefinition>()
    private var index = 0
    private var motion: ValueAnimator? = null
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
            if (!shouldAnimatePromotion()) return
            if (candidates.size > 1) {
                index = (index + 1) % candidates.size
                bindCharacter()
            }
            postDelayed(this, 12000L)
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = true
        clipToPadding = true
        setPadding(dp(4), dp(2), dp(5), dp(2))
        background = plywoodBackground(context, cornerRadiusDp = 12f)
        addView(avatar, LayoutParams(dp(44), dp(44)))
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(name.apply {
                textSize = 11f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LayoutParams(-1, -2))
            addView(storeLabel.apply {
                text = "IN STORE ›"
                textSize = 9f
                setTextColor(0xFFFFDC79.toInt())
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LayoutParams(-1, -2))
        }, LayoutParams(0, -2, 1f))
        isClickable = true
        isFocusable = true
        AppFocusRing.apply(this, cornerRadiusDp = 12f)
        setOnClickListener {
            notice?.cancel()
            notice = Toast.makeText(context,
                "Meet ${candidates.getOrNull(index)?.character?.displayName ?: "this paw"} in Store after your match.",
                Toast.LENGTH_SHORT).also { it.show() }
        }
    }

    private fun bindCharacter() {
        val character = candidates.getOrNull(index)?.character ?: return
        name.text = character.displayName
        avatar.bind(characterId = character.id, ringColor = 0xFF5BE0FF.toInt(),
            highlighted = false, showCircularChrome = false)
        contentDescription = "${character.displayName}. Available in Store after your match."
    }

    fun protect(views: List<View>) {
        protectedViews = views
    }

    private fun updateSpace(): Boolean {
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
        avatar.visibility = if (available) VISIBLE else INVISIBLE
        updateMotion()
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        val portraitSize = minOf(availableHeight - dp(8), (availableWidth * .55f).toInt(),
            availableWidth - dp(64)).coerceAtLeast(dp(44))
        if (avatar.layoutParams.width != portraitSize || avatar.layoutParams.height != portraitSize) {
            avatar.layoutParams = LayoutParams(portraitSize, portraitSize)
        }
        name.textSize = if (availableHeight >= dp(96)) 14f else 11f
        storeLabel.textSize = if (availableHeight >= dp(96)) 11f else 9f
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun refreshCandidates() {
        val ownedIds = CosmeticInventoryStore(context).ownedCosmeticIds()
        candidates = CharacterDefinitions.all.filter { definition ->
            val offer = StoreCosmeticCatalog.characterUnlock(definition.character.id)
                ?.let { StoreCosmeticCatalog.find(it.id) }
            CharacterRegistry.find(definition.character.id) != null && offer?.contentAvailable == true
        }.sortedBy { definition ->
            val offer = StoreCosmeticCatalog.characterUnlock(definition.character.id)
            if (offer != null && offer.id in ownedIds) 1 else 0
        }
        index = index.coerceAtMost((candidates.size - 1).coerceAtLeast(0))
        if (candidates.isEmpty()) visibility = GONE
        bindCharacter()
    }

    private fun shouldAnimatePromotion(): Boolean = spaceAvailable && isAttachedToWindow && isShown && windowVisibility == VISIBLE && hasWindowFocus() &&
        (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled())

    private fun updateMotion() {
        removeCallbacks(cycle)
        motion?.cancel()
        motion = null
        avatar.translationY = 0f
        avatar.rotation = 0f
        if (!shouldAnimatePromotion()) return
        motion = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                avatar.translationY = -dp(1) * fraction
                avatar.rotation = -1.5f + 3f * fraction
            }
            start()
        }
        postDelayed(cycle, 12000L)
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
        motion?.cancel()
        motion = null
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
        if (!active && layoutParams != null && layoutParams.height != (52 * resources.displayMetrics.density + .5f).toInt()) {
            layoutParams = layoutParams.apply { height = (52 * resources.displayMetrics.density + .5f).toInt() }
        }
    }

    fun protect(views: List<View>) = promotion.protect(views)
}
