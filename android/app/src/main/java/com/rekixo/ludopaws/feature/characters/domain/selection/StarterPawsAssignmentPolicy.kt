package com.rekixo.ludopaws.feature.characters.domain.selection

import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog

/**
 * Presentation-only assignment policy for the expandable starter-character 3D pack.
 * It never participates in dice, legal-move, turn, capture, CPU, or winner logic.
 */
object StarterPawsAssignmentPolicy {
    private const val MIN_PLAYERS = 2
    private const val MAX_PLAYERS = 4

    val starterCharacterIds: List<String>
        get() =
            LudoPawsCharacterCatalog
                .charactersForPack(
                    LudoPawsCharacterCatalog.STARTER_PACK_ID,
                )
                .map { character -> character.id }

    fun normalize(
        playerCount: Int,
        requestedCharacterIds: List<String>,
        preferredCharacterId: String,
        computerMode: Boolean,
        unlockedCharacterIds: Set<String> = starterCharacterIds.toSet(),
    ): List<String> {
        requirePlayerCount(playerCount)

        val available = starterCharacterIds.filter { computerMode || it in unlockedCharacterIds }
        require(available.size >= playerCount) {
            "Starter Paws must contain at least $playerCount characters"
        }

        val canonicalPreferred =
            LudoPawsCharacterCatalog
                .canonicalCharacterId(preferredCharacterId)
        val safePreferred =
            canonicalPreferred
                ?.takeIf { it in available && it in unlockedCharacterIds }
                ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
                    .takeIf(available::contains)
                ?: available.first()
        val canonicalRequested =
            requestedCharacterIds.mapNotNull(
                LudoPawsCharacterCatalog::canonicalCharacterId,
            )

        if (computerMode) {
            return buildList(playerCount) {
                add(safePreferred)
                available
                    .asSequence()
                    .filterNot { characterId -> characterId == safePreferred }
                    .take(playerCount - 1)
                    .forEach(::add)
            }
        }

        val normalized = MutableList<String?>(playerCount) { null }
        val used = linkedSetOf<String>()

        for (slot in 0 until playerCount) {
            val requested = canonicalRequested.getOrNull(slot)
            val candidate =
                when {
                    requested != null &&
                        requested in available &&
                        requested !in used -> requested

                    slot == 0 && safePreferred !in used -> safePreferred
                    else -> null
                }

            if (candidate != null) {
                normalized[slot] = candidate
                used += candidate
            }
        }

        for (slot in 0 until playerCount) {
            if (normalized[slot] != null) continue
            val fallback =
                available.firstOrNull { characterId -> characterId !in used }
                    ?: available[slot % available.size]
            normalized[slot] = fallback
            used += fallback
        }

        return normalized.map { characterId ->
            requireNotNull(characterId)
        }
    }

    fun select(
        playerCount: Int,
        currentCharacterIds: List<String>,
        selectedSlot: Int,
        requestedCharacterId: String,
        computerMode: Boolean,
        unlockedCharacterIds: Set<String> = starterCharacterIds.toSet(),
    ): List<String> {
        requirePlayerCount(playerCount)
        require(selectedSlot in 0 until playerCount) {
            "Character slot $selectedSlot is outside the active player range"
        }

        val available = starterCharacterIds.filter { computerMode || it in unlockedCharacterIds }
        val canonicalRequested =
            LudoPawsCharacterCatalog
                .canonicalCharacterId(requestedCharacterId)
        if (canonicalRequested == null || canonicalRequested !in available || canonicalRequested !in unlockedCharacterIds) {
            return normalize(
                playerCount = playerCount,
                requestedCharacterIds = currentCharacterIds,
                preferredCharacterId = currentCharacterIds.firstOrNull()
                    ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID,
                computerMode = computerMode,
                unlockedCharacterIds = unlockedCharacterIds,
            )
        }

        if (computerMode) {
            // Only the human slot is directly selectable in Computer mode.
            val humanCharacterId =
                if (selectedSlot == 0) {
                    canonicalRequested
                } else {
                    currentCharacterIds.firstOrNull()
                        ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
                }
            return normalize(
                playerCount = playerCount,
                requestedCharacterIds = listOf(humanCharacterId),
                preferredCharacterId = humanCharacterId,
                computerMode = true,
                unlockedCharacterIds = unlockedCharacterIds,
            )
        }

        val current =
            normalize(
                playerCount = playerCount,
                requestedCharacterIds = currentCharacterIds,
                preferredCharacterId = currentCharacterIds.firstOrNull()
                    ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID,
                computerMode = false,
                unlockedCharacterIds = unlockedCharacterIds,
            )
                .toMutableList()

        val previousCharacterId = current[selectedSlot]
        if (previousCharacterId == canonicalRequested) {
            return current
        }

        val ownerSlot = current.indexOf(canonicalRequested)
        current[selectedSlot] = canonicalRequested

        if (ownerSlot >= 0 && ownerSlot != selectedSlot) {
            // Swap instead of silently creating duplicates. This keeps every
            // active local player visually distinct while preserving intent.
            current[ownerSlot] = previousCharacterId
        }

        return current
    }

    private fun requirePlayerCount(playerCount: Int) {
        require(playerCount in MIN_PLAYERS..MAX_PLAYERS) {
            "Ludo Paws local character assignment supports 2 to 4 players"
        }
    }
}
