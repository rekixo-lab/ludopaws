package com.rekixo.ludopaws.feature.online.domain

import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog

/**
 * Backward-compatible cosmetic identity contract shared by remote presentation.
 *
 * The wire field stays optional for legacy clients. Old starter ids are migrated
 * onto the canonical Dog/Goat/Duck/Cat roster; missing, blank, stale or
 * unsupported values fall back to the production default. This contract remains
 * presentation-only and never participates in dice/proof state.
 */
object LudoPawsCharacterIdentityContract {
    const val WIRE_FIELD = "characterId"
    const val SCHEMA_VERSION = 1

    /** Send every locally registered paw unchanged; server ownership checks
     * human seats without limiting the shared roster for auto-filled bots. */
    fun resolveForRequest(characterId: String?): String = resolveRemote(characterId)

    fun resolveRemote(characterId: String?): String {
        val canonical =
            LudoPawsCharacterCatalog
                .canonicalCharacterId(
                    characterId
                        ?.trim()
                        ?.lowercase(),
                )
        return canonical
            ?.takeIf {
                LudoPawsCharacterCatalog.character(it) != null
            }
            ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
    }
}
