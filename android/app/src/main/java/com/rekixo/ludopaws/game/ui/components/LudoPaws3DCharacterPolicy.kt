package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog

/**
 * Production 3D pawn identity resolver.
 *
 * Character selection/store identity is presentation-only and is resolved by
 * seat. Ludo color stays authoritative gameplay state and is used only as a
 * deterministic compatibility fallback when an older match/install has no
 * character identity yet.
 */
enum class LudoPaws3DSpecies {
    DOG,
    GOAT,
    DUCK,
    CAT,
}

internal object LudoPaws3DCharacterPolicy {
    /**
     * The shared renderer asks for species by board color. The render thread
     * installs a color -> selected-character mapping before drawing. Frames for
     * one immutable snapshot reuse that binding instead of rebuilding a small
     * map on every 16/33 ms render tick.
     *
     * Identity checks are deliberate: a fresh authoritative snapshot or a new
     * seat-assignment list invalidates the binding immediately, while repeated
     * frames for the same scene remain allocation-free here.
     */
    private data class RenderAssignments(
        val snapshot: MatchSnapshot?,
        val characterIdsBySeat: List<String>,
        val speciesByColor: Map<String, LudoPaws3DSpecies>,
        val characterIdsByColor: Map<String, String>,
    )

    private val renderAssignments =
        ThreadLocal<RenderAssignments?>()

    fun speciesForCharacterId(
        characterId: String?,
    ): LudoPaws3DSpecies? =
        when (
            LudoPawsCharacterCatalog
                .canonicalCharacterId(characterId)
        ) {
            "dog" -> LudoPaws3DSpecies.DOG
            "goat", "goat_lumi" -> LudoPaws3DSpecies.GOAT
            "duck" -> LudoPaws3DSpecies.DUCK
            "cat" -> LudoPaws3DSpecies.CAT
            "donkey" -> LudoPaws3DSpecies.GOAT
            else -> null
        }

    fun speciesForSeat(
        characterIdsBySeat: List<String>,
        seat: Int,
        fallbackColor: String,
    ): LudoPaws3DSpecies? =
        speciesForCharacterId(
            characterIdsBySeat.getOrNull(seat),
        ) ?: fallbackSpeciesForColor(fallbackColor)

    fun bindRenderAssignments(
        snapshot: MatchSnapshot?,
        characterIdsBySeat: List<String>,
    ) {
        val current = renderAssignments.get()
        if (
            current != null &&
            current.snapshot === snapshot &&
            current.characterIdsBySeat === characterIdsBySeat
        ) {
            return
        }

        val mapping =
            snapshot
                ?.players
                ?.mapNotNull {
                    player ->
                    speciesForSeat(
                        characterIdsBySeat = characterIdsBySeat,
                        seat = player.seat,
                        fallbackColor = player.color,
                    )
                        ?.let {
                            player.color to it
                        }
                }
                ?.toMap()
                .orEmpty()
        renderAssignments.set(
            RenderAssignments(
                snapshot = snapshot,
                characterIdsBySeat = characterIdsBySeat,
                speciesByColor = mapping,
                characterIdsByColor = snapshot?.players?.associate { player ->
                    player.color to (LudoPawsCharacterCatalog.character(
                        characterIdsBySeat.getOrNull(player.seat).orEmpty(),
                    )?.id ?: fallbackCharacterId(player.color))
                }.orEmpty(),
            ),
        )
    }

    fun clearRenderAssignments() {
        renderAssignments.remove()
    }

    /**
     * Renderer-facing lookup. Selected seat identity wins when installed for
     * the current render thread; color mapping is compatibility fallback only.
     */
    fun speciesForColor(
        color: String,
    ): LudoPaws3DSpecies? =
        renderAssignments
            .get()
            ?.speciesByColor
            ?.get(color)
            ?: fallbackSpeciesForColor(color)

    fun characterIdForSeat(characterIdsBySeat: List<String>, seat: Int, fallbackColor: String): String =
        LudoPawsCharacterCatalog.character(characterIdsBySeat.getOrNull(seat).orEmpty())?.id
            ?: fallbackCharacterId(fallbackColor)

    fun characterIdForColor(color: String): String =
        renderAssignments.get()?.characterIdsByColor?.get(color) ?: fallbackCharacterId(color)

    private fun fallbackCharacterId(color: String): String = when (color) {
        "GREEN" -> "goat"
        "YELLOW" -> "duck"
        "BLUE" -> "cat"
        else -> "dog"
    }

    private fun fallbackSpeciesForColor(
        color: String,
    ): LudoPaws3DSpecies? =
        when (color) {
            "RED" -> LudoPaws3DSpecies.DOG
            "GREEN" -> LudoPaws3DSpecies.GOAT
            "YELLOW" -> LudoPaws3DSpecies.DUCK
            "BLUE" -> LudoPaws3DSpecies.CAT
            else -> null
        }
}
