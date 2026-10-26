package com.rekixo.ludopaws.ui.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
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
        pages.addView(promotion, LayoutParams(-1, -1))
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
        promotion.setActive(false)
        updatePageState()
    }

    fun setActive(value: Boolean) {
        active = value
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

    private fun systemMotionAllowed(): Boolean = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
        ValueAnimator.areAnimatorsEnabled()

    private fun canAutoSlide(): Boolean = active && isAttachedToWindow && isShown &&
        hasWindowFocus() && windowVisibility == VISIBLE && !touching && !dragging && systemMotionAllowed()

    private fun scheduleAutoSlide() {
        removeCallbacks(autoSlide)
        if (canAutoSlide()) postDelayed(autoSlide, nextAutoDelay)
    }

    private fun positionPages(value: Float) {
        offset = value
        hero.translationX = value
        promotion.translationX = value + width
    }

    private fun updatePageState() {
        positionPages(-page * width.toFloat())
        hero.visibility = if (page == 0) VISIBLE else INVISIBLE
        promotion.visibility = if (page == 1) VISIBLE else INVISIBLE
        promotion.setActive(active && page == 1 && hasWindowFocus() && windowVisibility == VISIBLE)
        indicator.text = if (page == 0) "●  ○" else "○  ●"
        contentDescription = if (page == 0) "Ludo Paws hero. Page 1 of 2. Swipe left to explore Store." else
            "Store spotlight. Page 2 of 2. Swipe right for Ludo Paws hero."
    }

    private fun stopTransition() {
        transition?.cancel()
        transition = null
        updatePageState()
    }

    private fun slideTo(target: Int, manual: Boolean) {
        removeCallbacks(autoSlide)
        transition?.cancel()
        transition = null
        nextAutoDelay = if (manual) 20000L else if (target == 0) 7000L else 10000L
        val destination = -target * width.toFloat()
        if (width <= 0 || !systemMotionAllowed() || abs(offset - destination) < 1f) {
            page = target
            updatePageState()
            scheduleAutoSlide()
            return
        }
        hero.visibility = VISIBLE
        promotion.visibility = VISIBLE
        promotion.setActive(false)
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
        val delta = event.x - downX
        if (!dragging && abs(delta) > touchSlop && abs(delta) > abs(event.y - downY)) {
            dragging = true
            parent?.requestDisallowInterceptTouchEvent(true)
            hero.visibility = VISIBLE
            promotion.visibility = VISIBLE
            promotion.setActive(false)
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
        info.isScrollable = true
        info.addAction(if (page == 0) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD else
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
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
