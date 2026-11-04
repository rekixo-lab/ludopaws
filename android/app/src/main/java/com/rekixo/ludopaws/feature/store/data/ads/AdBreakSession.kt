package com.rekixo.ludopaws.feature.store.data.ads

import android.content.Context
import android.content.SharedPreferences
import com.rekixo.ludopaws.feature.store.domain.AdBreakPolicy

/** Completed matches stay process-local; only the last ad time survives a restart. */
internal object AdBreakSession {
    private val startedAt = System.currentTimeMillis()
    private val matches = linkedSetOf<String>()
    private var lastShownAt: Long? = null
    private var reserved = false
    private var prefs: SharedPreferences? = null
    @Synchronized fun start(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("ludopaws_ad_breaks", Context.MODE_PRIVATE)
        lastShownAt = prefs?.getLong("last_shown_at", 0L)?.takeIf { it > 0L }
    }
    @Synchronized fun completed(matchId: String) {
        if (matchId.isNotBlank()) matches.add(matchId)
        while (matches.size > 64) matches.remove(matches.first())
    }
    @Synchronized fun canShow(): Boolean = !reserved && AdBreakPolicy.eligible(System.currentTimeMillis(), startedAt, matches.size, lastShownAt)
    @Synchronized fun reserve(): Boolean {
        if (!canShow()) return false
        reserved = true
        return true
    }
    @Synchronized fun shown() {
        val now = System.currentTimeMillis()
        lastShownAt = now
        prefs?.edit()?.putLong("last_shown_at", now)?.apply()
    }
    @Synchronized fun release() { reserved = false }
}
