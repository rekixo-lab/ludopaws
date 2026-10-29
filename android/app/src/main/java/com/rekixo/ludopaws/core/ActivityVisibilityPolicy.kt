package com.rekixo.ludopaws

/** Process foreground transitions, independent of Activity pause/resume gaps. */
internal class ActivityVisibilityPolicy {
    private var started = 0
    var isForeground = false
        private set

    fun started(): Boolean {
        started++
        if (isForeground) return false
        isForeground = true
        return true
    }

    fun stopped(): Boolean {
        started = (started - 1).coerceAtLeast(0)
        return started == 0
    }

    fun backgroundTimeout(): Boolean {
        if (started > 0 || !isForeground) return false
        isForeground = false
        return true
    }
}
