package com.rekixo.ludopaws.feature.team.data.local

import android.content.Context
import org.json.JSONObject
import java.util.UUID

data class TeamMatchmakingTicket(
    val requestId: String,
    val displayName: String,
    val startedAt: Long,
    val ownerAccountId: String? = null,
)

class TeamMatchmakingStore(context: Context) {
    private val sessions = com.rekixo.ludopaws.feature.account.data.AccountSessionStore(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(ticket: TeamMatchmakingTicket) {
        synchronized(LOCK) {
            require(runCatching { UUID.fromString(ticket.requestId) }.isSuccess)
            require(ticket.displayName.length in 2..24)
            require(ticket.startedAt > 0L)
            val owner = sessions.load()?.accountId
            require(ticket.ownerAccountId == null || ticket.ownerAccountId == owner)
            val newSearch = load()?.requestId != ticket.requestId
            val editor = prefs.edit().putString(KEY_JSON, JSONObject()
                .put("requestId", ticket.requestId)
                .put("displayName", ticket.displayName)
                .put("startedAt", ticket.startedAt)
                .put("owner", ticket.ownerAccountId ?: owner)
                .toString())
            if (newSearch) editor.remove("cancel_pending").remove("assignment_pending")
            check(editor.commit()) { "Team matchmaking ticket could not be persisted" }
        }
    }

    fun load(): TeamMatchmakingTicket? {
        val raw = prefs.getString(KEY_JSON, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val requestId = json.getString("requestId")
            val displayName = json.getString("displayName")
            val startedAt = json.getLong("startedAt")
            val owner = json.optString("owner").takeIf { it.isNotBlank() && it != "null" }
            if (owner != null && owner != sessions.load()?.accountId) return null
            require(runCatching { UUID.fromString(requestId) }.isSuccess)
            require(displayName.length in 2..24)
            require(startedAt > 0L)
            TeamMatchmakingTicket(requestId, displayName, startedAt, owner)
        }.getOrElse { clear(); null }
    }

    fun bindOwner(requestId: String) {
        synchronized(LOCK) {
            val ticket = load() ?: error("Search account changed")
            check(ticket.requestId == requestId)
            val owner = sessions.load()?.accountId ?: error("Connect your game account")
            check(ticket.ownerAccountId == null || ticket.ownerAccountId == owner)
            save(ticket.copy(ownerAccountId = owner))
        }
    }

    fun clearIfRequest(requestId: String) {
        synchronized(LOCK) { if (load()?.requestId == requestId) clear() }
    }

    fun markMatched(requestId: String): Boolean {
        synchronized(LOCK) {
            if (load()?.requestId != requestId) return false
            check(prefs.edit().remove("cancel_pending").putBoolean("assignment_pending", true).commit()) { "Could not save matched search" }
            return true
        }
    }

    fun awaitingAssignment(): Boolean = prefs.getBoolean("assignment_pending", false) && load() != null

    fun cancellationRequested(): Boolean = prefs.getBoolean("cancel_pending", false) && load() != null

    fun requestCancellation() {
        synchronized(LOCK) {
            check(load() != null) { "No active search to cancel" }
            check(prefs.edit().putBoolean("cancel_pending", true).commit()) { "Could not save pending cancellation" }
        }
    }

    fun clear() {
        synchronized(LOCK) {
            check(prefs.edit().clear().commit()) { "Could not clear the confirmed search" }
        }
    }

    private companion object {
        val LOCK = Any()
        const val PREFS_NAME = "ludoproof_team_matchmaking"
        const val KEY_JSON = "team_search_ticket"
    }
}
