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
        humanCount: Int = 1,
    ): List<String> {
        require(mode.isLocal) {
            "Offline character assignment requires a local game mode"
        }

        val requested =
            active
                ?.takeIf {
                    it.mode == mode &&
                        it.playerCount == playerCount &&
                        (mode != GameMode.COMPUTER || it.humanCount == humanCount)
                }
                ?.characterIds
                .orEmpty()

        return StarterPawsAssignmentPolicy.normalize(
            playerCount = playerCount,
            requestedCharacterIds = requested,
            preferredCharacterId = preferredCharacterId,
            computerMode = mode == GameMode.COMPUTER,
            unlockedCharacterIds = unlockedCharacterIds,
            humanCount = humanCount,
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
    // The match checkpoint owns the human/robot seat layout; preferences may
    // have changed after saving this match.
    val humanCount = if (gameMode == GameMode.COMPUTER) {
        state.players.count { !engine.isComputerPlayer(it.playerId) }
            .coerceIn(1, playerCount - 1)
    } else playerCount
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
            humanCount = humanCount,
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
        active.humanCount != humanCount ||
        active.preferredColor != preferredColor ||
        active.characterIds != resolved
    ) {
        offlineCharacterSetupStore.saveActive(
            mode = gameMode,
            playerCount = playerCount,
            preferredColor = preferredColor,
            characterIds = resolved,
            humanCount = humanCount,
        )
    }

    activeCharacterMatchId =
        state.matchId
    activeCharacterIdsBySeat =
        resolved
    return resolved
}
