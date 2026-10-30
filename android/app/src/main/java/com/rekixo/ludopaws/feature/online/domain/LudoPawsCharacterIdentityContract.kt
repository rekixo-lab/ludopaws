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

    /** New characters are available locally while production still has its old roster.
     * Remove this fallback only with the matching server rollout; never overwrite local selection. */
    fun resolveForRequest(characterId: String?): String {
        val canonical=resolveRemote(characterId)
        return when (canonical) {
            "donkey" -> LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
            "goat_lumi" -> "goat"
            "cock" -> "duck"
            "pig" -> LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
            "elephant" -> LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
            "horse" -> LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
            "rabbit" -> LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
            "monkey" -> LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
            "panda" -> LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
            else -> canonical
        }
    }

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
