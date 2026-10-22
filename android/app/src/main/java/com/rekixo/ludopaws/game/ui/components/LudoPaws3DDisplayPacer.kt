package com.rekixo.ludopaws

import kotlin.math.roundToInt

/** Select whole display refreshes instead of drifting against vsync with a millisecond timer. */
internal class LudoPaws3DDisplayPacer {
    private var lastFrameNanos = Long.MIN_VALUE

    fun reset() { lastFrameNanos = Long.MIN_VALUE }

    fun shouldRender(frameTimeNanos: Long, refreshPeriodNanos: Long, frameDelayMillis: Long): Boolean {
        val refresh = refreshPeriodNanos.coerceAtLeast(1L)
        val refreshes = (frameDelayMillis * 1_000_000.0 / refresh).roundToInt().coerceAtLeast(1)
        val interval = refresh * refreshes
        // Tolerate timestamp rounding without accidentally skipping an entire refresh.
        if (lastFrameNanos != Long.MIN_VALUE && frameTimeNanos - lastFrameNanos < interval - refresh / 4L) {
            return false
        }
        // A late frame replaces missed frames; never build a catch-up queue.
        lastFrameNanos = frameTimeNanos
        return true
    }
}
