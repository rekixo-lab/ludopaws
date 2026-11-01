package com.rekixo.ludopaws.ui.offline.state

import com.rekixo.ludopaws.GameMode
import com.rekixo.ludopaws.MatchSnapshot
import com.rekixo.ludopaws.OfflineGameActivity
import com.rekixo.ludopaws.feature.characters.domain.selection.StarterPawsAssignmentPolicy
import com.rekixo.ludopaws.feature.offline.data.local.ActiveOfflineCharacterSetup

/**
 * Presentation-only recovery policy for Starter Paws assignments.
 * Saved Ludo gameplay remains independent from cosmetic character metadata.
 */
object OfflineCharacterAssignmentPolicy {
    fun resolve(
        mode: GameMode,
        playerCount: Int,
        preferredCharacterId: String,
        active: ActiveOfflineCharacterSetup?,
        unlockedCharacterIds: Set<String> = StarterPawsAssignmentPolicy.starterCharacterIds.toSet(),
    ): List<String> {
        require(mode.isLocal) {
            "Offline character assignment requires a local game mode"
        }

        val requested =
            active
                ?.takeIf {
                    it.mode == mode &&
                        it.playerCount == playerCount
                }
                ?.characterIds
                .orEmpty()

        return StarterPawsAssignmentPolicy.normalize(
            playerCount = playerCount,
            requestedCharacterIds = requested,
            preferredCharacterId = preferredCharacterId,
            computerMode = mode == GameMode.COMPUTER,
            unlockedCharacterIds = unlockedCharacterIds,
        )
    }
}

/**
 * Resolves one stable character list for the active local match.
 *
 * A resumed match may outlive the presentation-only character preference record
 * (for example after an app upgrade or preference repair). In that case we
 * deterministically rebuild Starter Paws assignments and persist the repaired
 * presentation record without touching OfflineGameEngine saved state.
 */
internal fun OfflineGameActivity.resolveOfflineCharacterIds(
    state: MatchSnapshot,
): List<String> {
    val playerCount =
        state.players.size
    if (
        activeCharacterMatchId == state.matchId &&
        activeCharacterIdsBySeat.size == playerCount
    ) {
        return activeCharacterIdsBySeat
    }

    val active =
        offlineCharacterSetupStore.loadActive()
    val preferredCharacterId =
        characterSelectionStore
            .load()
            .characterId
    val resolved =
        OfflineCharacterAssignmentPolicy.resolve(
            mode = gameMode,
            playerCount = playerCount,
            preferredCharacterId = preferredCharacterId,
            active = active,
            unlockedCharacterIds = characterSelectionStore.unlockedCharacterIds(),
        )

    val preferredColor =
        state.players
            .firstOrNull()
            ?.color
            ?: active?.preferredColor
            ?: "BLUE"

    if (
        active == null ||
        active.mode != gameMode ||
        active.playerCount != playerCount ||
        active.preferredColor != preferredColor ||
        active.characterIds != resolved
    ) {
        offlineCharacterSetupStore.saveActive(
            mode = gameMode,
            playerCount = playerCount,
            preferredColor = preferredColor,
            characterIds = resolved,
        )
    }

    activeCharacterMatchId =
        state.matchId
    activeCharacterIdsBySeat =
        resolved
    return resolved
}
