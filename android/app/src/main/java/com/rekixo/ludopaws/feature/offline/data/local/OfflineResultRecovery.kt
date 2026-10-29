package com.rekixo.ludopaws.feature.offline.data.local

import android.content.Context
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileGameMode
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileMatchSource
import org.json.JSONObject
import java.util.concurrent.Executors

/** Independent of the checkpoint: leaving/rematching must not discard a failed stats write. */
internal object OfflineResultRecovery {
    private const val PREFS = "ludopaws_pending_local_results_v1"
    private val retryWorker = Executors.newSingleThreadExecutor { work ->
        Thread(work, "LudoPawsLocalResults").apply { isDaemon = true }
    }
    fun record(context: Context, matchId: String, owner: String?, won: Boolean, computer: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val result = JSONObject().put("owner", owner).put("won", won).put("computer", computer)
        check(prefs.edit().putString(matchId, result.toString()).commit()) { "Could not save pending match statistics" }
        apply(context, matchId, result)
    }
    fun retry(context: Context) {
        val app = context.applicationContext
        retryWorker.execute {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.all.forEach { (matchId, raw) ->
                runCatching { apply(app, matchId, JSONObject(raw as String)) }
                    .onFailure { android.util.Log.w("OfflineSave", "Match statistics will retry later", it) }
            }
        }
    }
    private fun apply(context: Context, matchId: String, result: JSONObject) {
        val owner = result.optString("owner").takeIf { it.isNotBlank() && it != "null" }
        ProfileStore(context, owner, pinAccountScope = true).recordCompletedMatch(
            matchId, ProfileGameMode.CLASSIC,
            if (result.getBoolean("computer")) ProfileMatchSource.COMPUTER else ProfileMatchSource.LOCAL,
            result.getBoolean("won"),
        )
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(matchId).commit())
    }
}
