package com.rekixo.ludopaws.feature.characters.domain.selection

import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.offline.domain.engine.RobotTeamComposition

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
        humanCount: Int = 1,
    ): List<String> {
        requirePlayerCount(playerCount)
        val humans = if (computerMode) RobotTeamComposition.humans(playerCount, humanCount) else playerCount
        val available = starterCharacterIds.filter { it in unlockedCharacterIds }
        val owned = available
        require(owned.size >= humans) {
            "Not enough unlocked paws for $humans human seats"
        }
        require(available.size >= playerCount) {
            "Starter Paws must contain at least $playerCount characters"
        }

        val canonicalPreferred =
            LudoPawsCharacterCatalog
                .canonicalCharacterId(preferredCharacterId)
        val safePreferred =
            canonicalPreferred
                ?.takeIf(owned::contains)
                ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
                    .takeIf(owned::contains)
                ?: owned.first()
        // Keep the seat indices intact even when an older saved ID is invalid.
        val canonicalRequested =
            requestedCharacterIds.map(
                LudoPawsCharacterCatalog::canonicalCharacterId,
            )

        if (computerMode) {
            // Every seat is chosen by this account in local Robot mode; locked
            // cosmetics are not usable by CPU opponents or human seats.
            val humanCharacter = canonicalRequested.firstOrNull()
                ?.takeIf(owned::contains) ?: safePreferred
            val used = linkedSetOf(humanCharacter)
            return buildList(playerCount) {
                add(humanCharacter)
                for (seat in 1 until playerCount) {
                    val pool = available
                    val requested = canonicalRequested.getOrNull(seat)
                    val assigned = requested?.takeIf { it in pool && it !in used }
                        ?: pool.first { it !in used }
                    add(assigned)
                    used += assigned
                }
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
        humanCount: Int = 1,
    ): List<String> {
        requirePlayerCount(playerCount)
        val humans = if (computerMode) RobotTeamComposition.humans(playerCount, humanCount) else playerCount
        require(selectedSlot in 0 until playerCount) {
            "Character slot $selectedSlot is outside the active player range"
        }

        val available = starterCharacterIds.filter { it in unlockedCharacterIds }
        val canonicalRequested =
            LudoPawsCharacterCatalog
                .canonicalCharacterId(requestedCharacterId)
        if (
            canonicalRequested == null ||
            canonicalRequested !in available ||
            canonicalRequested !in unlockedCharacterIds
        ) {
            return normalize(
                playerCount = playerCount,
                requestedCharacterIds = currentCharacterIds,
                preferredCharacterId = currentCharacterIds.firstOrNull()
                    ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID,
                computerMode = computerMode,
                unlockedCharacterIds = unlockedCharacterIds,
                humanCount = humans,
            )
        }

        val current =
            normalize(
                playerCount = playerCount,
                requestedCharacterIds = currentCharacterIds,
                preferredCharacterId = currentCharacterIds.firstOrNull()
                    ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID,
                computerMode = computerMode,
                unlockedCharacterIds = unlockedCharacterIds,
                humanCount = humans,
            )
                .toMutableList()

        val previousCharacterId = current[selectedSlot]
        if (previousCharacterId == canonicalRequested) {
            return current
        }

        val ownerSlot = current.indexOf(canonicalRequested)
        // Changing a CPU opponent must never silently replace the human's paw.
        if (computerMode && selectedSlot >= humans && ownerSlot in 0 until humans) {
            return current
        }
        current[selectedSlot] = canonicalRequested

        if (ownerSlot >= 0 && ownerSlot != selectedSlot) {
            // Swap between already-owned paws, keeping every seat visually unique.
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
