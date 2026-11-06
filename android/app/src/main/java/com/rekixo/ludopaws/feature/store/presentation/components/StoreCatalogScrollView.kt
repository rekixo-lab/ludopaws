package com.rekixo.ludopaws.feature.store.presentation.components

import android.content.Context
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.widget.ScrollView

/** Keeps card animation and UI updates out of the drag/fling frames. */
internal class StoreCatalogScrollView(context: Context) : ScrollView(context) {
    private val artworkListeners = linkedSetOf<() -> Unit>()
    private var touchInProgress = false
    var isMoving = false
        private set
    var onSettled: (() -> Unit)? = null

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (!isMoving) notifyArtwork()
    }
    private val settle = Runnable {
        if (!touchInProgress && isMoving) {
            isMoving = false
            val action = onSettled
            onSettled = null
            action?.invoke()
            notifyArtwork()
        }
    }

    fun addArtworkListener(listener: () -> Unit) {
        artworkListeners.add(listener)
    }

    fun removeArtworkListener(listener: () -> Unit) {
        artworkListeners.remove(listener)
    }

    private fun notifyArtwork() {
        artworkListeners.toList().forEach { it() }
    }

    private fun scheduleSettle() {
        removeCallbacks(settle)
        if (isMoving && !touchInProgress) postDelayed(settle, 80L)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (t == oldt || !isAttachedToWindow) return
        if (!isMoving) {
            isMoving = true
            notifyArtwork()
        }
        scheduleSettle()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            touchInProgress = true
            scheduleSettle()
        }
        val handled = super.dispatchTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> if (!handled) {
                touchInProgress = false
                scheduleSettle()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchInProgress = false
                scheduleSettle()
            }
        }
        return handled
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            touchInProgress = false
            scheduleSettle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        removeCallbacks(settle)
        touchInProgress = false
        isMoving = false
        onSettled = null
        super.onDetachedFromWindow()
    }
}
