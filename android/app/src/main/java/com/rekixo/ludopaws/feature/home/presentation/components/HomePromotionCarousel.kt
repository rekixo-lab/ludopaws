package com.rekixo.ludopaws.ui.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.app.Activity
import com.rekixo.ludopaws.launchPlayerScreen
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.LudoProofApplication
import com.rekixo.ludopaws.feature.referrals.FriendshipBundleView
import com.rekixo.ludopaws.feature.referrals.ReferralActivity
import com.rekixo.ludopaws.feature.referrals.ReferralRepository
import com.rekixo.ludopaws.feature.referrals.TimedFriendshipOffer
import com.rekixo.ludopaws.feature.store.presentation.components.GamePawsPromotionView
import kotlin.math.abs

internal class HomePromotionCarousel(
    context: Context,
    onExplore: (String, String?) -> Unit,
) : FrameLayout(context) {
    private val pages = FrameLayout(context)
    private val hero = ImageView(context).apply {
        setImageResource(R.drawable.ludo_paws_home_hero)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "Ludo Paws characters"
    }
    private val promotion = GamePawsPromotionView(context, gameplay = false, onExplore = onExplore)
    private val spotlight = FrameLayout(context)
    private val bundle = FriendshipBundleView(context, compact = true).apply {
        isClickable = true
        isFocusable = true
        setOnClickListener { (context as? Activity)?.launchPlayerScreen(Intent(context, ReferralActivity::class.java)) }
    }
    private var bundleAvailable = false
    private var timedBundle: FriendshipBundleView? = null
    private var timedOffer: TimedFriendshipOffer? = null
    private enum class Spotlight { TIMED_BUNDLE, BUNDLE, SHOP }
    private var shown = Spotlight.TIMED_BUNDLE
    private var next = Spotlight.TIMED_BUNDLE
    private var bundleGeneration = 0
    private val choices: List<Spotlight> get() = buildList {
        if (timedOffer?.canInvite == true) add(Spotlight.TIMED_BUNDLE)
        if (bundleAvailable) add(Spotlight.BUNDLE)
        if (promotion.hasLockedOffers) add(Spotlight.SHOP)
    }
    private val hasOffers: Boolean get() = choices.isNotEmpty()
    private val indicator = TextView(context)
    private var active = false
    private var page = 0
    private var offset = 0f
    private var transition: ValueAnimator? = null
    private var touching = false
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var nextAutoDelay = 2400L
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val autoSlide = Runnable {
        if (canAutoSlide()) slideTo(1 - page, manual = false)
    }

    init {
        clipChildren = true
        clipToPadding = true
        pages.clipChildren = true
        pages.addView(hero, LayoutParams(-1, -1))
        spotlight.addView(promotion, LayoutParams(-1, -1))
        spotlight.addView(bundle, LayoutParams(-1, -1))
        pages.addView(spotlight, LayoutParams(-1, -1))
        addView(pages, LayoutParams(-1, -1))
        addView(indicator.apply {
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(dp(42), dp(16), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(4)
            marginEnd = dp(4)
        })
        isFocusable = true
        promotion.onAvailabilityChanged = {
            if (!hasOffers) {
                removeCallbacks(autoSlide)
                transition?.cancel()
                transition = null
                page = 0
                touching = false
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            updatePageState()
            scheduleAutoSlide()
        }
        promotion.setActive(false)
        updatePageState()
    }

    fun refreshConnectivity() {
        promotion.refreshOffers()
        if (active) refreshBundle()
    }

    fun setActive(value: Boolean) {
        active = value
        bundleGeneration++
        if (value) {
            bundleAvailable = false
            timedOffer = null
            next = Spotlight.TIMED_BUNDLE
        }
        if (value) refreshBundle()
        if (value) promotion.refreshOffers()
        if (value) page = 0
        touching = false
        dragging = false
        removeCallbacks(autoSlide)
        stopTransition()
        if (value) {
            nextAutoDelay = 2400L
            updatePageState()
            scheduleAutoSlide()
        } else updatePageState()
    }

    private fun refreshBundle() {
        val generation = ++bundleGeneration
        ReferralRepository.executor.execute {
            val repository = ReferralRepository(context)
            val snapshot = runCatching { repository.status() }.getOrNull()
            val accountId = snapshot?.optString("localAccountId").orEmpty()
            val offer = TimedFriendshipOffer.from(snapshot?.optJSONObject("timedOffer"))
                ?: if (accountId.isNotBlank() && snapshot?.optBoolean("timedOfferAvailable") == true) {
                    runCatching { repository.showTimedOffer(accountId) }.getOrNull()
                } else null
            post {
                if (!active || bundleGeneration != generation || !isAttachedToWindow) return@post
                val account = (context.applicationContext as LudoProofApplication).accountRepository.currentSession()
                bundleAvailable = com.rekixo.ludopaws.feature.safety.FamilyAgeScreen.optionalGoogleAllowed(context) &&
                    snapshot?.optBoolean("enabled") == true && snapshot.optString("state") != "REWARDED" &&
                    snapshot.optString("localAccountId") == account?.accountId
                if (accountId == account?.accountId &&
                    com.rekixo.ludopaws.feature.safety.FamilyAgeScreen.optionalGoogleAllowed(context) && offer?.canInvite == true) {
                    timedOffer = offer
                    val card = timedBundle ?: FriendshipBundleView(context, compact = true, timed = true).also {
                        it.isClickable = true
                        it.isFocusable = true
                        it.setOnClickListener {
                            (context as? Activity)?.launchPlayerScreen(Intent(context, ReferralActivity::class.java))
                        }
                        spotlight.addView(it, LayoutParams(-1, -1))
                        timedBundle = it
                    }
                    card.bindOffer(offer) {
                        timedOffer = null
                        updatePageState()
                        scheduleAutoSlide()
                    }
                }
                (context as? com.rekixo.ludopaws.HomeActivity)?.refreshHomeGemBalance()
                updatePageState()
                scheduleAutoSlide()
            }
        }
    }

    private fun systemMotionAllowed(): Boolean = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
        ValueAnimator.areAnimatorsEnabled()

    private fun canAutoSlide(): Boolean = hasOffers && active && isAttachedToWindow && isShown &&
        hasWindowFocus() && windowVisibility == VISIBLE && !touching && !dragging && systemMotionAllowed()

    private fun scheduleAutoSlide() {
        removeCallbacks(autoSlide)
        if (canAutoSlide()) postDelayed(autoSlide, nextAutoDelay)
    }

    private fun positionPages(value: Float) {
        offset = value
        hero.translationX = value
        spotlight.translationX = value + width
    }

    private fun updatePageState() {
        if (!hasOffers) page = 0
        val available = choices
        if (shown !in available || page == 0) shown = next.takeIf { it in available } ?: available.firstOrNull() ?: Spotlight.SHOP
        positionPages(-page * width.toFloat())
        hero.visibility = if (page == 0) VISIBLE else INVISIBLE
        spotlight.visibility = if (page == 1) VISIBLE else INVISIBLE
        updateSpotlightVisibility()
        val showingSpotlight = active && page == 1 && hasWindowFocus() && windowVisibility == VISIBLE
        promotion.setActive(showingSpotlight && shown == Spotlight.SHOP)
        bundle.setActive(showingSpotlight && shown == Spotlight.BUNDLE)
        timedBundle?.setActive(showingSpotlight && shown == Spotlight.TIMED_BUNDLE)
        indicator.text = if (page == 0) "●  ○" else "○  ●"
        indicator.visibility = if (hasOffers) VISIBLE else GONE
        contentDescription = if (!hasOffers) "Ludo Paws hero" else if (page == 0) "Ludo Paws hero. Page 1 of 2. Swipe left to explore." else when (shown) {
            Spotlight.TIMED_BUNDLE -> "Friendship Bundle 2. Swipe right for Ludo Paws hero."
            Spotlight.BUNDLE -> "Friendship bundle. Swipe right for Ludo Paws hero."
            Spotlight.SHOP -> "Store spotlight. Swipe right for Ludo Paws hero."
        }
    }

    private fun updateSpotlightVisibility() {
        promotion.visibility = if (shown == Spotlight.SHOP) VISIBLE else GONE
        bundle.visibility = if (shown == Spotlight.BUNDLE) VISIBLE else GONE
        timedBundle?.visibility = if (shown == Spotlight.TIMED_BUNDLE) VISIBLE else GONE
    }

    private fun stopTransition() {
        transition?.cancel()
        transition = null
        updatePageState()
    }

    private fun slideTo(target: Int, manual: Boolean) {
        if (target == 1 && !hasOffers) {
            stopTransition()
            removeCallbacks(autoSlide)
            return
        }
        removeCallbacks(autoSlide)
        transition?.cancel()
        transition = null
        val available = choices
        if (target == 0 && page == 1 && available.isNotEmpty()) {
            next = available[(available.indexOf(shown) + 1) % available.size]
        }
        if (target == 1 && page == 0) {
            shown = next.takeIf { it in available } ?: available.firstOrNull() ?: Spotlight.SHOP
            updateSpotlightVisibility()
        }
        nextAutoDelay = if (manual) 20000L else if (target == 0) 7000L else 10000L
        val destination = -target * width.toFloat()
        if (width <= 0 || !systemMotionAllowed() || abs(offset - destination) < 1f) {
            page = target
            updatePageState()
            scheduleAutoSlide()
            return
        }
        hero.visibility = VISIBLE
        spotlight.visibility = VISIBLE
        promotion.setActive(false)
        bundle.setActive(false)
        timedBundle?.setActive(false)
        transition = ValueAnimator.ofFloat(offset, destination).apply {
            duration = 420L
            interpolator = DecelerateInterpolator()
            addUpdateListener { positionPages(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    transition = null
                    page = target
                    updatePageState()
                    scheduleAutoSlide()
                }
            })
            start()
        }
    }

    private fun beginTouch(event: MotionEvent) {
        removeCallbacks(autoSlide)
        stopTransition()
        downX = event.x
        downY = event.y
        touching = true
        dragging = false
        nextAutoDelay = 20000L
    }

    private fun drag(event: MotionEvent) {
        if (!hasOffers) return
        val delta = event.x - downX
        if (!dragging && abs(delta) > touchSlop && abs(delta) > abs(event.y - downY)) {
            dragging = true
            parent?.requestDisallowInterceptTouchEvent(true)
            hero.visibility = VISIBLE
            spotlight.visibility = VISIBLE
            promotion.setActive(false)
            bundle.setActive(false)
            timedBundle?.setActive(false)
        }
        if (dragging) positionPages((-page * width + delta).coerceIn(-width.toFloat(), 0f))
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginTouch(event)
            MotionEvent.ACTION_MOVE -> drag(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!dragging) {
                touching = false
                scheduleAutoSlide()
            }
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (!touching) beginTouch(event)
            MotionEvent.ACTION_MOVE -> drag(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasDragging = dragging
                val delta = event.x - downX
                val threshold = minOf(dp(72).toFloat(), width * .2f)
                val target = if (event.actionMasked == MotionEvent.ACTION_CANCEL) page else when {
                    delta < -threshold -> 1
                    delta > threshold -> 0
                    else -> page
                }
                touching = false
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (wasDragging) slideTo(target, manual = true) else {
                    performClick()
                    scheduleAutoSlide()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isScrollable = hasOffers
        if (hasOffers) {
            info.addAction(if (page == 0) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD else
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            if (!hasOffers) return false
            slideTo(if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) 1 else 0, manual = true)
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        stopTransition()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!isAttachedToWindow) return
        if (!hasWindowFocus) { removeCallbacks(autoSlide); stopTransition() }
        updatePageState()
        scheduleAutoSlide()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (!isAttachedToWindow) return
        if (visibility != VISIBLE) { removeCallbacks(autoSlide); stopTransition() }
        updatePageState()
        scheduleAutoSlide()
    }

    override fun onDetachedFromWindow() {
        setActive(false)
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
