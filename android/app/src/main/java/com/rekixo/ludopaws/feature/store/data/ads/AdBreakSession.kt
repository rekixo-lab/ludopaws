package com.rekixo.ludopaws.feature.store.data.ads

import android.os.SystemClock
import com.rekixo.ludopaws.feature.store.domain.AdBreakPolicy

/** Process-local only: no account identifiers, persistent tracking or network requests. */
internal object AdBreakSession {
    private val startedAt = SystemClock.uptimeMillis()
    private val matches = linkedSetOf<String>()
    private var lastShownAt: Long? = null
    private var reserved = false
    fun start() = Unit
    @Synchronized fun completed(matchId: String) {
        if (matchId.isNotBlank()) matches.add(matchId)
        while (matches.size > 64) matches.remove(matches.first())
    }
    @Synchronized fun canShow(): Boolean = !reserved && AdBreakPolicy.eligible(SystemClock.uptimeMillis(), startedAt, matches.size, lastShownAt)
    @Synchronized fun reserve(): Boolean {
        if (!canShow()) return false
        reserved = true
        return true
    }
    @Synchronized fun shown() { lastShownAt = SystemClock.uptimeMillis() }
    @Synchronized fun release() { reserved = false }
}
