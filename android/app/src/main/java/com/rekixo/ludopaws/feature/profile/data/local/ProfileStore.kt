package com.rekixo.ludopaws.feature.profile.data.local

import android.content.Context
import com.rekixo.ludopaws.feature.account.data.AccountSessionStore
import com.rekixo.ludopaws.feature.profile.domain.ProfileProgression
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileGameMode
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileMatchSource
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileModeStats
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class ProfileStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    /**
     * Pins the legacy local profile to the currently active guest/account before account switching.
     * Old stats are never silently copied into a different restored account.
     */
    @Synchronized
    fun ensureCurrentAccountScope(): Boolean {
        val accountId = AccountSessionStore(appContext).load()?.accountId ?: return false
        val targetKey = PROFILE_JSON_PREFIX + accountId
        val editor = prefs.edit()
        if (!prefs.contains(targetKey) && !prefs.getBoolean(KEY_LEGACY_PROFILE_MIGRATED, false)) {
            val legacy = prefs.getString(KEY_PROFILE_JSON, null)
            if (legacy != null) editor.putString(targetKey, legacy)
        }
        editor.putBoolean(KEY_LEGACY_PROFILE_MIGRATED, true)
        check(editor.commit()) { "Profile account scope could not be saved safely" }
        return true
    }

    @Synchronized
    fun clearForAccountDeletion(accountId: String) {
        require(accountId.matches(Regex("^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$", RegexOption.IGNORE_CASE)))
        check(
            prefs.edit()
                .remove(PROFILE_JSON_PREFIX + accountId)
                .remove(KEY_PROFILE_JSON)
                .remove(KEY_QUARANTINED_PROFILE_JSON)
                .commit(),
        ) { "Local profile data could not be cleared after account deletion." }
    }

    /**
     * Stops a legacy unscoped profile from being adopted by a Google account restored on a fresh
     * device/session. If an old account is still active, pin its local profile to that account;
     * otherwise preserve the old profile in quarantine and mark migration complete.
     */
    @Synchronized
    fun prepareForAccountRestore(): Boolean {
        val accountId = AccountSessionStore(appContext).load()?.accountId
        val migrated = prefs.getBoolean(KEY_LEGACY_PROFILE_MIGRATED, false)
        val legacy = prefs.getString(KEY_PROFILE_JSON, null)
        val editor = prefs.edit()
        if (!migrated && legacy != null) {
            if (accountId != null) {
                val accountKey = PROFILE_JSON_PREFIX + accountId
                if (!prefs.contains(accountKey)) editor.putString(accountKey, legacy)
            } else if (!prefs.contains(KEY_QUARANTINED_PROFILE_JSON)) {
                editor.putString(KEY_QUARANTINED_PROFILE_JSON, legacy)
            }
        }
        editor.putBoolean(KEY_LEGACY_PROFILE_MIGRATED, true)
            .remove(KEY_PROFILE_JSON)
        check(editor.commit()) { "Legacy profile could not be isolated before account restore" }
        return true
    }

    private fun profileStorageKey(): String {
        val accountId = AccountSessionStore(appContext).load()?.accountId
        return accountId?.let { PROFILE_JSON_PREFIX + it } ?: KEY_PROFILE_JSON
    }

    fun snapshot():
        ProfileSnapshot =
        load()
            .toSnapshot()

    @Synchronized
    fun updateDisplayName(
        value: String,
    ): ProfileSnapshot {
        val normalized =
            value
                .trim()
                .replace(
                    Regex("\\s+"),
                    " ",
                )
                .take(
                    MAX_NAME_LENGTH,
                )
                .ifBlank {
                    "Guest User"
                }

        val state =
            load()
        state.displayName =
            normalized
        save(
            state,
        )
        return state
            .toSnapshot()
    }

    @Synchronized
    fun recordCompletedMatch(
        matchId: String,
        mode: ProfileGameMode,
        source: ProfileMatchSource,
        won: Boolean,
    ): Boolean {
        if (
            matchId.isBlank()
        ) {
            return false
        }

        val state =
            load()
        if (
            matchId in
                state.recordedMatchIds
        ) {
            return false
        }

        state.recordedMatchIds +=
            matchId
        while (
            state.recordedMatchIds
                .size >
                MAX_RECORDED_MATCHES
        ) {
            val oldest =
                state.recordedMatchIds
                    .firstOrNull()
                    ?: break
            state.recordedMatchIds
                .remove(
                    oldest,
                )
        }

        state.totalGames +=
            1
        if (won) {
            state.totalWins +=
                1
            state.currentWinStreak +=
                1
            state.bestWinStreak =
                maxOf(
                    state.bestWinStreak,
                    state.currentWinStreak,
                )
        } else {
            state.currentWinStreak =
                0
        }

        when (source) {
            ProfileMatchSource.ONLINE ->
                state.onlineGames +=
                    1
            ProfileMatchSource.LOCAL ->
                state.localGames +=
                    1
            ProfileMatchSource.COMPUTER ->
                Unit
        }

        val currentStats =
            state.modeStats[
                mode
            ] ?: ProfileModeStats()
        state.modeStats[
            mode
        ] =
            currentStats.copy(
                games =
                    currentStats.games +
                        1,
                wins =
                    currentStats.wins +
                        if (won) {
                            1
                        } else {
                            0
                        },
            )

        state.totalXp +=
            ProfileProgression
                .xpAward(
                    won,
                )

        val today =
            LocalDate
                .now()
                .toEpochDay()
        if (
            state.lastPlayedDay !=
                today
        ) {
            state.currentDayStreak =
                if (
                    state.lastPlayedDay ==
                        today -
                            1
                ) {
                    state.currentDayStreak +
                        1
                } else {
                    1
                }
            state.bestDayStreak =
                maxOf(
                    state.bestDayStreak,
                    state.currentDayStreak,
                )
            state.lastPlayedDay =
                today
        }

        save(
            state,
        )
        return true
    }

    @Synchronized
    fun addPurchase(
        productId: String,
    ): Boolean {
        val normalized =
            productId
                .trim()
        if (
            normalized.isBlank()
        ) {
            return false
        }

        val state =
            load()
        if (
            !state.purchasedProducts
                .add(
                    normalized,
                )
        ) {
            return false
        }

        save(
            state,
        )
        return true
    }

    private fun load():
        MutableProfileState {
        var storageKey = profileStorageKey()
        var raw = prefs.getString(storageKey, null)
        if (raw == null && storageKey != KEY_PROFILE_JSON &&
            !prefs.getBoolean(KEY_LEGACY_PROFILE_MIGRATED, false)
        ) {
            ensureCurrentAccountScope()
            storageKey = profileStorageKey()
            raw = prefs.getString(storageKey, null)
        }
        raw ?: return MutableProfileState()

        return runCatching {
            val json =
                JSONObject(
                    raw,
                )
            val modes =
                json.optJSONObject(
                    "modes",
                )

            MutableProfileState(
                displayName =
                    json.optString(
                        "displayName",
                        "Guest User",
                    ),
                totalGames =
                    json.optInt(
                        "totalGames",
                        0,
                    ),
                totalWins =
                    json.optInt(
                        "totalWins",
                        0,
                    ),
                currentDayStreak =
                    json.optInt(
                        "currentDayStreak",
                        0,
                    ),
                bestDayStreak =
                    json.optInt(
                        "bestDayStreak",
                        0,
                    ),
                currentWinStreak =
                    json.optInt(
                        "currentWinStreak",
                        0,
                    ),
                bestWinStreak =
                    json.optInt(
                        "bestWinStreak",
                        0,
                    ),
                totalXp =
                    json.optInt(
                        "totalXp",
                        0,
                    ),
                onlineGames =
                    json.optInt(
                        "onlineGames",
                        0,
                    ),
                localGames =
                    json.optInt(
                        "localGames",
                        0,
                    ),
                lastPlayedDay =
                    if (
                        json.has(
                            "lastPlayedDay",
                        )
                    ) {
                        json.optLong(
                            "lastPlayedDay",
                            Long.MIN_VALUE,
                        )
                            .takeIf {
                                it !=
                                    Long.MIN_VALUE
                            }
                    } else {
                        null
                    },
                modeStats =
                    ProfileGameMode
                        .entries
                        .associateWith {
                                mode ->
                            val value =
                                modes
                                    ?.optJSONObject(
                                        mode.name,
                                    )
                            ProfileModeStats(
                                games =
                                    value
                                        ?.optInt(
                                            "games",
                                            0,
                                        )
                                        ?: 0,
                                wins =
                                    value
                                        ?.optInt(
                                            "wins",
                                            0,
                                        )
                                        ?: 0,
                            )
                        }
                        .toMutableMap(),
                purchasedProducts =
                    json.optJSONArray(
                        "purchases",
                    )
                        .toStringSet(),
                recordedMatchIds =
                    json.optJSONArray(
                        "recordedMatchIds",
                    )
                        .toLinkedStringSet(),
            )
        }.getOrElse {
            MutableProfileState()
        }
    }

    private fun save(
        state: MutableProfileState,
    ) {
        val modes =
            JSONObject()
        state.modeStats
            .forEach {
                    (
                        mode,
                        stats,
                    ) ->
                modes.put(
                    mode.name,
                    JSONObject()
                        .put(
                            "games",
                            stats.games,
                        )
                        .put(
                            "wins",
                            stats.wins,
                        ),
                )
            }

        val json =
            JSONObject()
                .put(
                    "displayName",
                    state.displayName,
                )
                .put(
                    "totalGames",
                    state.totalGames,
                )
                .put(
                    "totalWins",
                    state.totalWins,
                )
                .put(
                    "currentDayStreak",
                    state.currentDayStreak,
                )
                .put(
                    "bestDayStreak",
                    state.bestDayStreak,
                )
                .put(
                    "currentWinStreak",
                    state.currentWinStreak,
                )
                .put(
                    "bestWinStreak",
                    state.bestWinStreak,
                )
                .put(
                    "totalXp",
                    state.totalXp,
                )
                .put(
                    "onlineGames",
                    state.onlineGames,
                )
                .put(
                    "localGames",
                    state.localGames,
                )
                .put(
                    "lastPlayedDay",
                    state.lastPlayedDay,
                )
                .put(
                    "modes",
                    modes,
                )
                .put(
                    "purchases",
                    JSONArray(
                        state.purchasedProducts
                            .toList(),
                    ),
                )
                .put(
                    "recordedMatchIds",
                    JSONArray(
                        state.recordedMatchIds
                            .toList(),
                    ),
                )

        val storageKey = profileStorageKey()
        val editor = prefs.edit().putString(storageKey, json.toString())
        if (storageKey != KEY_PROFILE_JSON) {
            editor.putBoolean(KEY_LEGACY_PROFILE_MIGRATED, true)
        }
        check(editor.commit()) {
            "Profile stats could not be persisted"
        }
    }

    private fun JSONArray?
        .toStringSet():
        MutableSet<String> =
        linkedSetOf<String>()
            .also {
                    result ->
                if (
                    this ==
                    null
                ) {
                    return@also
                }
                for (
                    index in
                    0 until
                        length()
                ) {
                    optString(
                        index,
                    )
                        .takeIf {
                            it.isNotBlank()
                        }
                        ?.let(
                            result::add,
                        )
                }
            }

    private fun JSONArray?
        .toLinkedStringSet():
        LinkedHashSet<String> =
        LinkedHashSet(
            toStringSet(),
        )

    private data class MutableProfileState(
        var displayName: String = "Guest User",
        var totalGames: Int = 0,
        var totalWins: Int = 0,
        var currentDayStreak: Int = 0,
        var bestDayStreak: Int = 0,
        var currentWinStreak: Int = 0,
        var bestWinStreak: Int = 0,
        var totalXp: Int = 0,
        var onlineGames: Int = 0,
        var localGames: Int = 0,
        var lastPlayedDay: Long? = null,
        val modeStats:
            MutableMap<
                ProfileGameMode,
                ProfileModeStats,
                > =
            ProfileGameMode
                .entries
                .associateWith {
                    ProfileModeStats()
                }
                .toMutableMap(),
        val purchasedProducts:
            MutableSet<String> =
            linkedSetOf(),
        val recordedMatchIds:
            LinkedHashSet<String> =
            linkedSetOf(),
    ) {
        fun toSnapshot():
            ProfileSnapshot =
            ProfileSnapshot(
                displayName =
                    displayName,
                totalGames =
                    totalGames,
                totalWins =
                    totalWins,
                currentDayStreak =
                    currentDayStreak,
                bestDayStreak =
                    bestDayStreak,
                currentWinStreak =
                    currentWinStreak,
                bestWinStreak =
                    bestWinStreak,
                totalXp =
                    totalXp,
                onlineGames =
                    onlineGames,
                localGames =
                    localGames,
                classic =
                    modeStats[
                        ProfileGameMode.CLASSIC
                    ] ?: ProfileModeStats(),
                rush =
                    modeStats[
                        ProfileGameMode.RUSH
                    ] ?: ProfileModeStats(),
                snakeLadder =
                    modeStats[
                        ProfileGameMode.SNAKE_LADDER
                    ] ?: ProfileModeStats(),
                purchasedProducts =
                    purchasedProducts
                        .toList(),
            )
    }

    private companion object {
        const val PREFS_NAME =
            "ludoproof_profile"
        const val KEY_PROFILE_JSON =
            "profile_json_v1"
        const val PROFILE_JSON_PREFIX =
            "profile_json_v2_account_"
        const val KEY_LEGACY_PROFILE_MIGRATED =
            "profile_legacy_scope_migrated"
        const val KEY_QUARANTINED_PROFILE_JSON =
            "profile_json_legacy_quarantine"
        const val MAX_RECORDED_MATCHES =
            250
        const val MAX_NAME_LENGTH =
            24
    }
}
