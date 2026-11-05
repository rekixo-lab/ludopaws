package com.rekixo.ludopaws.feature.growth

import android.content.Context
import com.rekixo.ludopaws.feature.account.data.AccountSessionStore

/** Local-only, non-currency milestones. Never an authority for wallet grants. */
internal class EngagementStore(context: Context, private val fixedOwner: String? = null, private val pinned: Boolean = false) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("engagement_v1", Context.MODE_PRIVATE)
    private fun owner() = if (pinned) fixedOwner ?: "installation" else AccountSessionStore(app).load()?.accountId ?: "installation"
    fun completed(matchId: String, completedAt: Long = System.currentTimeMillis()) = synchronized(LOCK) {
        if (matchId.isBlank() || completedAt <= 0L) return@synchronized
        val key = owner()
        if (key in app.getSharedPreferences("ludoproof_profile", Context.MODE_PRIVATE)
                .getStringSet("deleted_profile_accounts", emptySet()).orEmpty()) return@synchronized
        val ids = prefs.getStringSet("$key:matches", emptySet()).orEmpty().toMutableSet()
        if (!ids.add(matchId)) return@synchronized
        while (ids.size > 128) ids.remove(ids.first())
        val day = completedAt / AutomaticPromptGate.DAY
        val days = prefs.getStringSet("$key:days", emptySet()).orEmpty().toMutableSet().apply { add(day.toString()) }
        val previousDay = prefs.getLong("$key:today", -1)
        val count = if (previousDay == day) prefs.getInt("$key:todayCount", 0) + 1 else 1
        val editor = prefs.edit().putStringSet("$key:matches", ids)
            .putStringSet("$key:days", days.sortedBy { it.toLongOrNull() ?: -1L }.takeLast(366).toSet())
        // A delayed old result contributes its original day without replacing today's counter.
        if (day >= previousDay) editor.putLong("$key:today", day).putInt("$key:todayCount", count)
        check(editor.commit()) { "Match experience could not be saved. Retry the completed result." }
        GrowthMetrics.increment(app, "match_completed")
    }
    fun completedMatches(): Int = prefs.getStringSet("${owner()}:matches", emptySet()).orEmpty().size
    fun completedDays(): Int = prefs.getStringSet("${owner()}:days", emptySet()).orEmpty().size
    fun todayMatches(): Int = if (prefs.getLong("${owner()}:today", -1) == System.currentTimeMillis() / AutomaticPromptGate.DAY)
        prefs.getInt("${owner()}:todayCount", 0) else 0
    fun lastReviewAttempt(): Long = prefs.getLong("${owner()}:reviewAttempt", 0)
    fun reviewAttempt() { prefs.edit().putLong("${owner()}:reviewAttempt", System.currentTimeMillis()).apply() }
    private companion object { val LOCK = Any() }
}
