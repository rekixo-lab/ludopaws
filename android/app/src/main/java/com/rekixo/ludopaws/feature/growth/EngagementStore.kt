package com.rekixo.ludopaws.feature.growth

import android.content.Context
import com.rekixo.ludopaws.feature.account.data.AccountSessionStore

/** Local-only, non-currency milestones. Never an authority for wallet grants. */
internal class EngagementStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("engagement_v1", Context.MODE_PRIVATE)
    private fun owner() = AccountSessionStore(app).load()?.accountId ?: "installation"
    @Synchronized fun completed(matchId: String) {
        if (matchId.isBlank()) return
        val key = owner()
        val ids = prefs.getStringSet("$key:matches", emptySet()).orEmpty().toMutableSet()
        if (!ids.add(matchId)) return
        while (ids.size > 128) ids.remove(ids.first())
        val day = System.currentTimeMillis() / AutomaticPromptGate.DAY
        val days = prefs.getStringSet("$key:days", emptySet()).orEmpty().toMutableSet().apply { add(day.toString()) }
        val previousDay = prefs.getLong("$key:today", -1)
        val count = if (previousDay == day) prefs.getInt("$key:todayCount", 0) + 1 else 1
        prefs.edit().putStringSet("$key:matches", ids).putStringSet("$key:days", days.toList().takeLast(366).toSet())
            .putLong("$key:today", day).putInt("$key:todayCount", count).apply()
        GrowthMetrics.increment(app, "match_completed")
    }
    fun completedDays(): Int = prefs.getStringSet("${owner()}:days", emptySet()).orEmpty().size
    fun todayMatches(): Int = if (prefs.getLong("${owner()}:today", -1) == System.currentTimeMillis() / AutomaticPromptGate.DAY)
        prefs.getInt("${owner()}:todayCount", 0) else 0
    fun lastReviewAttempt(): Long = prefs.getLong("${owner()}:reviewAttempt", 0)
    fun reviewAttempt() { prefs.edit().putLong("${owner()}:reviewAttempt", System.currentTimeMillis()).apply() }
}
