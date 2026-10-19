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
        prefs.edit()
            .putString(KEY_JSON, safe.toString())
            .apply()
    }

    fun load(): JSONObject? =
        prefs.getString(KEY_JSON, null)
            ?.let {
                runCatching {
                    JSONObject(it)
                        .put(
                            CACHE_SOURCE_MARKER,
                            true,
                        )
                }.getOrNull()
            }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        internal const val CACHE_SOURCE_MARKER =
            "__ludopaws_cached_match_v1"

        private const val PREFS_NAME =
            "ludoproof_cached_match"
        private const val KEY_JSON =
            "safe_match_envelope"
    }
}
