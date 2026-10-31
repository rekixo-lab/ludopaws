package com.rekixo.ludopaws.feature.characters.data.local

import android.content.Context
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.characters.domain.model.CharacterSelection

class CharacterSelectionStore(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun isUnlocked(characterId: String): Boolean {
        val canonical = LudoPawsCharacterCatalog.canonicalCharacterId(characterId) ?: return false
        val protectedId = com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
            .characterEntitlementId(canonical)
        return if (protectedId != null) {
            // Even a hidden/archived paid Paw requires a server-owned entitlement.
            com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore(appContext)
                .isOwned(protectedId)
        } else {
            LudoPawsCharacterCatalog.character(canonical) != null
        }
    }

    fun unlockedCharacterIds(): Set<String> =
        LudoPawsCharacterCatalog.characters.filter { isUnlocked(it.id) }.map { it.id }.toSet()
    private val prefs =
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )

    fun load(): CharacterSelection {
        val storedSchema =
            prefs.getInt(
                KEY_SCHEMA_VERSION,
                SCHEMA_VERSION,
            )
        if (storedSchema != SCHEMA_VERSION) {
            return repairToDefault()
        }

        val resolved =
            LudoPawsCharacterCatalog
                .resolveSelection(
                    packId =
                        prefs.getString(
                            KEY_PACK_ID,
                            null,
                        ),
                    characterId =
                        prefs.getString(
                            KEY_CHARACTER_ID,
                            null,
                        ),
                ).takeIf { isUnlocked(it.characterId) } ?: LudoPawsCharacterCatalog.defaultSelection

        val storedPack =
            prefs.getString(
                KEY_PACK_ID,
                null,
            )
        val storedCharacter =
            prefs.getString(
                KEY_CHARACTER_ID,
                null,
            )

        if (
            storedPack != resolved.packId ||
            storedCharacter != resolved.characterId
        ) {
            persist(resolved)
        } else {
            LudoPawsCharacterRuntime.update(
                resolved.characterId,
            )
        }
        return resolved
    }

    fun save(
        selection: CharacterSelection,
    ): CharacterSelection {
        val resolved =
            if (
                LudoPawsCharacterCatalog
                    .isValidSelection(
                        selection,
                    ) && isUnlocked(selection.characterId)
            ) {
                selection
            } else {
                LudoPawsCharacterCatalog
                    .defaultSelection
            }
        persist(resolved)
        return resolved
    }

    fun select(
        packId: String,
        characterId: String,
    ): CharacterSelection =
        save(
            CharacterSelection(
                packId = packId,
                characterId = characterId,
            ),
        )

    fun reset(): CharacterSelection =
        repairToDefault()

    private fun repairToDefault(): CharacterSelection {
        val fallback =
            LudoPawsCharacterCatalog
                .defaultSelection
        persist(fallback)
        return fallback
    }

    private fun persist(
        selection: CharacterSelection,
    ) {
        prefs.edit()
            .putInt(
                KEY_SCHEMA_VERSION,
                SCHEMA_VERSION,
            )
            .putString(
                KEY_PACK_ID,
                selection.packId,
            )
            .putString(
                KEY_CHARACTER_ID,
                selection.characterId,
            )
            .apply()
        LudoPawsCharacterRuntime.update(
            selection.characterId,
        )
    }

    private companion object {
        const val PREFS_NAME =
            "ludo_paws_character_selection_v1"
        const val SCHEMA_VERSION =
            1
        const val KEY_SCHEMA_VERSION =
            "schema_version"
        const val KEY_PACK_ID =
            "pack_id"
        const val KEY_CHARACTER_ID =
            "character_id"
    }
}
