package com.rekixo.ludopaws.feature.growth

import android.content.Context
import com.rekixo.ludopaws.feature.account.data.AccountSessionStore
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import org.json.JSONArray
import org.json.JSONObject

/** Device-local collection badge, never a wallet, ranking or competitive reward authority. */
internal class WeeklyChallengeStore(context: Context, private val fixedOwner: String? = null, private val pinned: Boolean = false) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("weekly_challenges_v1", Context.MODE_PRIVATE)
    private fun owner() = if (pinned) fixedOwner ?: "installation" else AccountSessionStore(app).load()?.accountId ?: "installation"
    data class Status(val title: String, val goal: String, val progress: Int, val target: Int,
        val endsAt: Long, val badges: Int) { val complete: Boolean get() = progress >= target }
    private fun week(time: Long) = Math.floorDiv(time / DAY + 3L, 7L) // Monday 00:00 UTC.
    private fun read(key: String): JSONObject = runCatching { JSONObject(prefs.getString(key, "{}") ?: "{}") }.getOrElse { JSONObject() }
    private fun strings(array: JSONArray?): List<String> = if (array == null) emptyList() else (0 until array.length()).map { array.optString(it) }
    fun status(): Status = synchronized(LOCK) {
        val w = week(System.currentTimeMillis())
        val data = read("${owner()}:week:$w")
        val kind = Math.floorMod(w, 3L).toInt()
        val count = when (kind) { 0 -> strings(data.optJSONArray("matches")).size; 1 -> strings(data.optJSONArray("days")).size; else -> strings(data.optJSONArray("characters")).size }
        val target = if (kind == 1) 2 else 3
        Status(listOf("Paws Party", "Two-Day Adventure", "Meet the Pals")[kind],
            listOf("Finish 3 matches", "Finish a match on 2 different days", "Finish matches using 3 different pals")[kind],
            count.coerceAtMost(target), target, ((w + 1) * 7 - 3) * DAY,
            prefs.getStringSet("${owner()}:badges", emptySet()).orEmpty().size)
    }
    fun completed(matchId: String, characters: List<String>, completedAt: Long = System.currentTimeMillis()) = synchronized(LOCK) {
        if (matchId.isBlank() || completedAt <= 0L) return@synchronized
        val account = owner()
        if (account in app.getSharedPreferences("ludoproof_profile", Context.MODE_PRIVATE)
                .getStringSet("deleted_profile_accounts", emptySet()).orEmpty()) return@synchronized
        val seen = strings(read("$account:seen").optJSONArray("ids")).toMutableList()
        if (matchId in seen) return@synchronized
        val w = week(completedAt)
        // Delayed recovery is never silently counted in a new week's challenge.
        if (w != week(System.currentTimeMillis())) {
            seen.add(matchId)
            check(prefs.edit().putString("$account:seen", JSONObject().put("ids", JSONArray(seen.takeLast(2048))).toString()).commit())
            return@synchronized
        }
        val key = "$account:week:$w"
        val data = read(key)
        val ids = strings(data.optJSONArray("matches")).toMutableList()
        if (matchId in ids) return@synchronized
        ids.add(matchId)
        while (ids.size > 512) ids.removeAt(0)
        val days = strings(data.optJSONArray("days")).toMutableSet().apply { add((completedAt / DAY).toString()) }
        val pals = strings(data.optJSONArray("characters")).toMutableSet().apply {
            addAll(characters.mapNotNull { LudoPawsCharacterCatalog.character(it)?.id })
        }
        data.put("matches", JSONArray(ids)).put("days", JSONArray(days.toList())).put("characters", JSONArray(pals.toList()))
        val complete = when (Math.floorMod(w, 3L).toInt()) { 0 -> ids.size >= 3; 1 -> days.size >= 2; else -> pals.size >= 3 }
        val badges = prefs.getStringSet("$account:badges", emptySet()).orEmpty().toMutableSet()
        if (complete) badges.add(w.toString())
        seen.add(matchId)
        while (seen.size > 2048) seen.removeAt(0)
        val edit = prefs.edit().putString(key, data.toString()).putStringSet("$account:badges", badges)
            .putString("$account:seen", JSONObject().put("ids", JSONArray(seen)).toString())
        prefs.all.keys.filter { it.startsWith("$account:week:") && it != key }.forEach { edit.remove(it) }
        check(edit.commit()) { "Weekly progress could not be saved. It will retry with the match result." }
    }
    companion object { private const val DAY = 86_400_000L; private val LOCK = Any() }
}
