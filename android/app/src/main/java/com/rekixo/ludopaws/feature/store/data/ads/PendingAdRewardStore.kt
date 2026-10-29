package com.rekixo.ludopaws.feature.store.data.ads

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class PendingAdReward(
    val accountId: String,
    val requestId: String,
    val cosmeticId: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val earned: Boolean = false,
    val abandoned: Boolean = false,
)

/** Recovery hints, never credits: signed server receipts remain authoritative. */
internal class PendingAdRewardStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pending_ad_rewards_v1", Context.MODE_PRIVATE)

    fun forAccount(accountId: String): List<PendingAdReward> = synchronized(LOCK) {
        read(accountId)
    }

    fun put(record: PendingAdReward) = synchronized(LOCK) {
        require(UUID.matches(record.accountId) && UUID.matches(record.requestId))
        val all = read(record.accountId).filterNot { it.requestId == record.requestId }.toMutableList()
        check(all.size < 64) { "Previous ad rewards still need verification. Retry their status before watching more ads." }
        all += record
        write(record.accountId, all)
    }

    fun confirm(accountId: String, requestIds: Set<String>) = synchronized(LOCK) {
        if (requestIds.isNotEmpty()) write(accountId, read(accountId).filterNot { it.requestId in requestIds })
    }

    fun removeAccount(accountId: String) = synchronized(LOCK) {
        check(prefs.edit().remove("rewards:$accountId").commit())
    }

    private fun read(accountId: String): List<PendingAdReward> {
        val json = JSONArray(prefs.getString("rewards:$accountId", "[]"))
        check(json.length() <= 64) { "Reward recovery records need support review; keep the app installed." }
        val now = System.currentTimeMillis()
        return (0 until json.length()).map { index ->
            val row = json.getJSONObject(index)
            val account = row.getString("accountId")
            val request = row.getString("requestId")
            check(account == accountId && UUID.matches(account) && UUID.matches(request)) { "Reward recovery data could not be read safely." }
            PendingAdReward(account, request, row.optString("cosmeticId").takeIf(String::isNotBlank),
                row.getLong("createdAt"), row.optBoolean("earned"), row.optBoolean("abandoned"))
        }.filter { record ->
            // Never expire a reported earned reward. Old abandoned/prepared
            // hints may disappear; their server-side grant can still arrive.
            record.earned || now - record.createdAt < (if (record.abandoned) 300_000L else 86_400_000L)
        }
    }

    private fun write(accountId: String, records: List<PendingAdReward>) {
        val json = JSONArray()
        records.forEach { row -> json.put(JSONObject().put("accountId", row.accountId)
            .put("requestId", row.requestId).put("cosmeticId", row.cosmeticId ?: "")
            .put("createdAt", row.createdAt).put("earned", row.earned).put("abandoned", row.abandoned)) }
        val key = "rewards:$accountId"
        val previous = prefs.getString(key, null)
        if (!prefs.edit().putString(key, json.toString()).commit()) {
            val rollback = prefs.edit()
            if (previous == null) rollback.remove(key) else rollback.putString(key, previous)
            rollback.apply()
            error("Reward recovery could not be saved. Keep the app installed and retry.")
        }
    }

    private companion object {
        val LOCK = Any()
        val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
