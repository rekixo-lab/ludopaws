package com.rekixo.ludopaws.feature.offline.data.local

import android.content.Context
import com.rekixo.ludopaws.GameMode
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.characters.domain.selection.StarterPawsAssignmentPolicy

data class OfflineCharacterSetupSnapshot(
    val playerCount: Int,
    val selectedSlot: Int,
    val characterIds: List<String>,
)

data class ActiveOfflineCharacterSetup(
    val mode: GameMode,
    val playerCount: Int,
    val preferredColor: String,
    val characterIds: List<String>,
)

/**
 * Versioned presentation-only persistence for local character choices.
 *
 * Character metadata intentionally lives outside OfflineGameEngine saved-state
 * and native dice receipts and fairness-history material. Phase 5 can consume the active assignment for
 * rendering without changing authoritative Ludo state or fairness transcripts.
 */
class OfflineCharacterSetupStore(
    context: Context,
) {
    private val prefs =
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )

    fun loadSetup(
        mode: GameMode,
        playerCount: Int,
        preferredCharacterId: String,
    ): OfflineCharacterSetupSnapshot {
        require(mode.isLocal) {
            "Offline character setup requires a local game mode"
        }

        val storedSchema =
            prefs.getInt(
                KEY_SCHEMA_VERSION,
                SCHEMA_VERSION,
            )
        if (storedSchema != SCHEMA_VERSION) {
            prefs.edit().clear().apply()
        }

        val ids =
            parseIds(
                prefs.getString(
                    setupIdsKey(mode),
                    null,
                ),
            )
        val normalized =
            StarterPawsAssignmentPolicy.normalize(
                playerCount = playerCount,
                requestedCharacterIds = ids,
                preferredCharacterId = preferredCharacterId,
                computerMode = mode == GameMode.COMPUTER,
            )
        val storedSlot =
            prefs.getInt(
                setupSlotKey(mode),
                0,
            )
        val selectedSlot =
            if (mode == GameMode.COMPUTER) {
                0
            } else {
                storedSlot.coerceIn(
                    0,
                    playerCount - 1,
                )
            }

        return saveSetup(
            mode = mode,
            snapshot =
                OfflineCharacterSetupSnapshot(
                    playerCount = playerCount,
                    selectedSlot = selectedSlot,
                    characterIds = normalized,
                ),
        )
    }

    fun saveSetup(
        mode: GameMode,
        snapshot: OfflineCharacterSetupSnapshot,
    ): OfflineCharacterSetupSnapshot {
        require(mode.isLocal) {
            "Offline character setup requires a local game mode"
        }

        val preferred =
            snapshot.characterIds.firstOrNull()
                ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
        val normalized =
            StarterPawsAssignmentPolicy.normalize(
                playerCount = snapshot.playerCount,
                requestedCharacterIds = snapshot.characterIds,
                preferredCharacterId = preferred,
                computerMode = mode == GameMode.COMPUTER,
            )
        val safeSlot =
            if (mode == GameMode.COMPUTER) {
                0
            } else {
                snapshot.selectedSlot.coerceIn(
                    0,
                    snapshot.playerCount - 1,
                )
            }
        val repaired =
            OfflineCharacterSetupSnapshot(
                playerCount = snapshot.playerCount,
                selectedSlot = safeSlot,
                characterIds = normalized,
            )

        prefs.edit()
            .putInt(
                KEY_SCHEMA_VERSION,
                SCHEMA_VERSION,
            )
            .putInt(
                setupPlayerCountKey(mode),
                repaired.playerCount,
            )
            .putInt(
                setupSlotKey(mode),
                repaired.selectedSlot,
            )
            .putString(
                setupIdsKey(mode),
                repaired.characterIds.joinToString(","),
            )
            .apply()

        return repaired
    }

    fun saveActive(
        mode: GameMode,
        playerCount: Int,
        preferredColor: String,
        characterIds: List<String>,
    ): ActiveOfflineCharacterSetup {
        require(mode.isLocal) {
            "Active offline character setup requires a local game mode"
        }
        val normalized =
            StarterPawsAssignmentPolicy.normalize(
                playerCount = playerCount,
                requestedCharacterIds = characterIds,
                preferredCharacterId = characterIds.firstOrNull()
                    ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID,
                computerMode = mode == GameMode.COMPUTER,
            )
        val active =
            ActiveOfflineCharacterSetup(
                mode = mode,
                playerCount = playerCount,
                preferredColor = preferredColor,
                characterIds = normalized,
            )

        prefs.edit()
            .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .putString(KEY_ACTIVE_MODE, mode.wireValue)
            .putInt(KEY_ACTIVE_PLAYER_COUNT, playerCount)
            .putString(KEY_ACTIVE_COLOR, preferredColor)
            .putString(KEY_ACTIVE_CHARACTER_IDS, normalized.joinToString(","))
            .apply()

        return active
    }

    fun loadActive(): ActiveOfflineCharacterSetup? {
        if (
            prefs.getInt(
                KEY_SCHEMA_VERSION,
                SCHEMA_VERSION,
            ) != SCHEMA_VERSION
        ) {
            return null
        }

        val mode =
            GameMode.fromWireValue(
                prefs.getString(
                    KEY_ACTIVE_MODE,
                    null,
                ),
            )
                ?.takeIf(GameMode::isLocal)
                ?: return null
        val playerCount =
            prefs.getInt(
                KEY_ACTIVE_PLAYER_COUNT,
                0,
            )
        if (!mode.supportsPlayerCount(playerCount)) {
            return null
        }

        val ids =
            parseIds(
                prefs.getString(
                    KEY_ACTIVE_CHARACTER_IDS,
                    null,
                ),
            )
        val normalized =
            StarterPawsAssignmentPolicy.normalize(
                playerCount = playerCount,
                requestedCharacterIds = ids,
                preferredCharacterId = ids.firstOrNull()
                    ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID,
                computerMode = mode == GameMode.COMPUTER,
            )

        return ActiveOfflineCharacterSetup(
            mode = mode,
            playerCount = playerCount,
            preferredColor =
                prefs.getString(
                    KEY_ACTIVE_COLOR,
                    "BLUE",
                )
                    ?: "BLUE",
            characterIds = normalized,
        )
    }

    private fun parseIds(
        raw: String?,
    ): List<String> =
        raw
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()

    private fun modeKey(
        mode: GameMode,
    ): String =
        mode.wireValue.lowercase()

    private fun setupPlayerCountKey(
        mode: GameMode,
    ): String =
        "${modeKey(mode)}_player_count"

    private fun setupSlotKey(
        mode: GameMode,
    ): String =
        "${modeKey(mode)}_selected_slot"

    private fun setupIdsKey(
        mode: GameMode,
    ): String =
        "${modeKey(mode)}_character_ids"

    private companion object {
        const val PREFS_NAME =
            "ludo_paws_offline_character_setup_v1"
        const val SCHEMA_VERSION =
            1
        const val KEY_SCHEMA_VERSION =
            "schema_version"
        const val KEY_ACTIVE_MODE =
            "active_mode"
        const val KEY_ACTIVE_PLAYER_COUNT =
            "active_player_count"
        const val KEY_ACTIVE_COLOR =
            "active_color"
        const val KEY_ACTIVE_CHARACTER_IDS =
            "active_character_ids"
    }
}
