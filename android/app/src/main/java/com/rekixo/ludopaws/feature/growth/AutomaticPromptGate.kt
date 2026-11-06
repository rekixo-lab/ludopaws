package com.rekixo.ludopaws.feature.growth

import android.content.Context
import android.os.SystemClock

/** One unsolicited promotion per installation/day and process session, across all hosts. */
internal object AutomaticPromptGate {
    private var active: String? = null
    private var shownThisSession = false
    private var quietUntil = 0L
    @Synchronized fun quietFor(millis: Long = 90_000L) {
        quietUntil = maxOf(quietUntil, SystemClock.elapsedRealtime() + millis)
    }
    @Synchronized fun reserve(context: Context, kind: String): Boolean {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("automatic_prompts_v2", Context.MODE_PRIVATE)
        val previous = prefs.getLong("last_at", 0L)
        if (active != null || shownThisSession || SystemClock.elapsedRealtime() < quietUntil ||
            previous > now || (previous > 0 && now / DAY == previous / DAY)) return false
        active = kind
        return true
    }
    @Synchronized fun presented(context: Context, kind: String) {
        if (active != kind) return
        shownThisSession = true
        context.getSharedPreferences("automatic_prompts_v2", Context.MODE_PRIVATE).edit()
            .putLong("last_at", System.currentTimeMillis()).apply()
    }
    @Synchronized fun release(kind: String) {
        if (active != kind) return
        active = null
        quietFor()
    }
    const val DAY = 86_400_000L
}
