package com.rekixo.ludopaws

import android.content.Context
import org.json.JSONObject

class CachedMatchStore(
    context: Context,
) {
    private val prefs =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    fun save(
        playerId: String?,
        state: JSONObject?,
    ) {
        if (state == null) return
        val safe =
            JSONObject()
                .put(
                    "playerId",
                    playerId,
                )
                .put(
                    "state",
                    state,
                )
        synchronized(cacheLock) {
            pendingCache = safe
            cacheLoaded = true
            writes.submit(PREFS_NAME) {
                runCatching {
                    check(prefs.edit().putString(KEY_JSON, safe.toString()).commit())
                }.onFailure { android.util.Log.w("MatchCache", "Could not persist cached match", it) }
            }
        }
    }

    fun load(): JSONObject? = synchronized(cacheLock) {
        val stored = if (cacheLoaded) pendingCache?.toString() else prefs.getString(KEY_JSON, null)
        stored?.let {
            runCatching { JSONObject(it).put(CACHE_SOURCE_MARKER, true) }.getOrNull()
        }
    }

    fun clear() {
        synchronized(cacheLock) {
            pendingCache = null
            cacheLoaded = true
            writes.submit(PREFS_NAME) {
                runCatching { check(prefs.edit().clear().commit()) }
                    .onFailure { android.util.Log.w("MatchCache", "Could not clear cached match", it) }
            }
        }
    }

    companion object {
        private val cacheLock = Any()
        private var cacheLoaded = false
        private var pendingCache: JSONObject? = null
        private val cacheWriter = java.util.concurrent.Executors.newSingleThreadExecutor { work ->
            Thread(work, "LudoPawsMatchCache").apply { isDaemon = true }
        }
        private val writes = LatestWriteQueue<String>(cacheWriter) {
            android.util.Log.w("MatchCache", "Could not write cached match", it)
        }

        internal const val CACHE_SOURCE_MARKER =
            "__ludopaws_cached_match_v1"

        private const val PREFS_NAME =
            "ludoproof_cached_match"
        private const val KEY_JSON =
            "safe_match_envelope"
    }
}
