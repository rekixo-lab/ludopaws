package com.rekixo.ludopaws.feature.offline.data.local

import android.content.Context
import com.rekixo.ludopaws.GameMode
import com.rekixo.ludopaws.OfflinePlayerLayout
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.characters.domain.selection.StarterPawsAssignmentPolicy
import com.rekixo.ludopaws.feature.offline.domain.engine.RobotTeamComposition

data class OfflineCharacterSetupSnapshot(
    val playerCount: Int,
    val selectedSlot: Int,
    val characterIds: List<String>,
    val colorsBySeat: List<String> = emptyList(),
    val humanCount: Int = 1,
)

data class ActiveOfflineCharacterSetup(
    val mode: GameMode,
    val playerCount: Int,
    val preferredColor: String,
    val characterIds: List<String>,
    val humanCount: Int = 1,
)

/**
 * Versioned presentation-only persistence for local character choices.
 *
 * Character metadata intentionally lives outside OfflineGameEngine saved-state
 * and EntroNex/proof material. Phase 5 can consume the active assignment for
 * rendering without changing authoritative Ludo state or fairness transcripts.
 */
class OfflineCharacterSetupStore(
    context: Context,
) {
    private val characterSelection = com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore(context)
    private val prefs =
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )

    fun loadSavedPlayerCount(mode: GameMode, fallback: Int): Int {
        require(mode.isLocal)
        if (prefs.getInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION) != SCHEMA_VERSION) {
            return fallback
        }
        return prefs.getInt(setupPlayerCountKey(mode), fallback)
            .takeIf(mode::supportsPlayerCount) ?: fallback
    }

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
        val humanCount = if (mode == GameMode.COMPUTER) {
            RobotTeamComposition.humans(playerCount, prefs.getInt(setupHumanCountKey(mode), 1))
        } else playerCount
        val normalized =
            StarterPawsAssignmentPolicy.normalize(
                playerCount = playerCount,
                requestedCharacterIds = ids,
                preferredCharacterId = preferredCharacterId,
                computerMode = mode == GameMode.COMPUTER,
                unlockedCharacterIds = characterSelection.unlockedCharacterIds(),
                humanCount = humanCount,
            )
        val storedColors = parseIds(prefs.getString(setupColorsKey(mode), null))
        val colorsBySeat = if (mode == GameMode.COMPUTER) {
            OfflinePlayerLayout.colorsFor(
                playerCount,
                storedColors.firstOrNull()?.takeIf(OfflinePlayerLayout.COLORS::contains) ?: "BLUE",
            )
        } else {
            OfflinePlayerLayout.normalizeColorsForSeats(
                playerCount = playerCount,
                requestedColors = storedColors,
                preferredColor = storedColors.firstOrNull() ?: "BLUE",
            )
        }
        val storedSlot =
            prefs.getInt(
                setupSlotKey(mode),
                0,
            )
        val selectedSlot = storedSlot.coerceIn(0, playerCount - 1)

        return saveSetup(
            mode = mode,
            snapshot =
                OfflineCharacterSetupSnapshot(
                    playerCount = playerCount,
                    selectedSlot = selectedSlot,
                    characterIds = normalized,
                    colorsBySeat = colorsBySeat,
                    humanCount = humanCount,
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

        val humanCount = if (mode == GameMode.COMPUTER) {
            RobotTeamComposition.humans(snapshot.playerCount, snapshot.humanCount)
        } else snapshot.playerCount
        val preferred =
            snapshot.characterIds.firstOrNull()
                ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
        val normalized =
            StarterPawsAssignmentPolicy.normalize(
                playerCount = snapshot.playerCount,
                requestedCharacterIds = snapshot.characterIds,
                preferredCharacterId = preferred,
                computerMode = mode == GameMode.COMPUTER,
                unlockedCharacterIds = characterSelection.unlockedCharacterIds(),
                humanCount = humanCount,
            )
        val safeSlot = snapshot.selectedSlot.coerceIn(0, snapshot.playerCount - 1)
        val repaired =
            OfflineCharacterSetupSnapshot(
                playerCount = snapshot.playerCount,
                selectedSlot = safeSlot,
                characterIds = normalized,
                humanCount = humanCount,
                colorsBySeat = if (mode == GameMode.COMPUTER) {
                    OfflinePlayerLayout.colorsFor(
                        snapshot.playerCount,
                        snapshot.colorsBySeat.firstOrNull()
                            ?.takeIf(OfflinePlayerLayout.COLORS::contains) ?: "BLUE",
                    )
                } else {
                    OfflinePlayerLayout.normalizeColorsForSeats(
                        playerCount = snapshot.playerCount,
                        requestedColors = snapshot.colorsBySeat,
                        preferredColor = snapshot.colorsBySeat.firstOrNull() ?: "BLUE",
                    )
                },
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
            .putInt(setupHumanCountKey(mode), repaired.humanCount)
            .putString(
                setupIdsKey(mode),
                repaired.characterIds.joinToString(","),
            )
            .putString(setupColorsKey(mode), repaired.colorsBySeat.joinToString(","))
            .apply()

        return repaired
    }

    fun saveActive(
        mode: GameMode,
        playerCount: Int,
        preferredColor: String,
        characterIds: List<String>,
        humanCount: Int = 1,
    ): ActiveOfflineCharacterSetup {
        require(mode.isLocal) {
            "Active offline character setup requires a local game mode"
        }
        val humans = if (mode == GameMode.COMPUTER) {
            RobotTeamComposition.humans(playerCount, humanCount)
        } else playerCount
        val normalized =
            StarterPawsAssignmentPolicy.normalize(
                playerCount = playerCount,
                requestedCharacterIds = characterIds,
                preferredCharacterId = characterIds.firstOrNull()
                    ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID,
                computerMode = mode == GameMode.COMPUTER,
                unlockedCharacterIds = characterSelection.unlockedCharacterIds(),
                humanCount = humans,
            )
        val active =
            ActiveOfflineCharacterSetup(
                mode = mode,
                playerCount = playerCount,
                preferredColor = preferredColor,
                characterIds = normalized,
                humanCount = humans,
            )

        prefs.edit()
            .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .putString(KEY_ACTIVE_MODE, mode.wireValue)
            .putInt(KEY_ACTIVE_PLAYER_COUNT, playerCount)
            .putInt(KEY_ACTIVE_HUMAN_COUNT, humans)
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
        val humanCount = if (mode == GameMode.COMPUTER) {
            RobotTeamComposition.humans(playerCount, prefs.getInt(KEY_ACTIVE_HUMAN_COUNT, 1))
        } else playerCount
        val normalized =
            StarterPawsAssignmentPolicy.normalize(
                playerCount = playerCount,
                requestedCharacterIds = ids,
                preferredCharacterId = ids.firstOrNull()
                    ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID,
                computerMode = mode == GameMode.COMPUTER,
                unlockedCharacterIds = characterSelection.unlockedCharacterIds(),
                humanCount = humanCount,
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
            humanCount = humanCount,
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

    private fun setupHumanCountKey(mode: GameMode): String =
        "${modeKey(mode)}_human_count"

    private fun setupColorsKey(mode: GameMode): String =
        "${modeKey(mode)}_colors_by_seat"

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
        const val KEY_ACTIVE_HUMAN_COUNT =
            "active_human_count"
        const val KEY_ACTIVE_COLOR =
            "active_color"
        const val KEY_ACTIVE_CHARACTER_IDS =
            "active_character_ids"
    }
}
