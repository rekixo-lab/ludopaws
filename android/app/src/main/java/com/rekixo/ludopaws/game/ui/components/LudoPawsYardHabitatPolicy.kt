package com.rekixo.ludopaws

/**
 * Pure presentation policy: one habitat belongs to the selected animal in each
 * occupied color yard, NOT to an arbitrary screen corner. Gameplay coordinates,
 * color identity, match state and token rules are never modified.
 */
internal enum class LudoPawsYardHabitat {
    DOG_GARDEN,
    GOAT_MEADOW,
    DUCK_POND,
    CAT_COURTYARD,
    DONKEY_FARM,
}

internal object LudoPawsYardHabitatPolicy {
    fun forCharacterId(id: String?): LudoPawsYardHabitat =
        when (id) {
            "duck" -> LudoPawsYardHabitat.DUCK_POND
            "goat" -> LudoPawsYardHabitat.GOAT_MEADOW
            "cat" -> LudoPawsYardHabitat.CAT_COURTYARD
            "donkey" -> LudoPawsYardHabitat.DONKEY_FARM
            else -> LudoPawsYardHabitat.DOG_GARDEN
        }

    /** Absent players leave their yards in the equipped board's original theme. */
    fun forMatch(
        snapshot: MatchSnapshot?,
        characterIdsBySeat: List<String>,
    ): Map<String, LudoPawsYardHabitat> {
        if (snapshot == null) return emptyMap()

        return buildMap {
            snapshot.players.forEach { player ->
                val team = LudoPawsTeamIdentityPolicy.resolve(player.color)
                    ?: return@forEach
                // Exactly the same seat-first canonical identity/fallback as
                // LudoPaws3DSceneRenderer: a blue Duck receives a BLUE pond.
                val animalId = LudoPaws3DCharacterPolicy.characterIdForSeat(
                    characterIdsBySeat = characterIdsBySeat,
                    seat = player.seat,
                    fallbackColor = team.wireName,
                )
                put(team.wireName, forCharacterId(animalId))
            }
        }
    }
}

/**
 * Complete painted YARD quadrant (5 x 5 physical cells), including the broad
 * former team-color band outside the raised inner paw platform.
 *
 * The Ludo board uses a NON-UNIFORM 15x15 logical coordinate layout: each
 * six-logical-cell yard spans five physical cells. Match axisBoundary() rather
 * than calculating pixels from logical indices directly.
 *
 * The environment replaces that entire home background. Road, center and
 * authoritative pawn coordinates remain completely unchanged.
 */
internal data class LudoPawsYardHabitatFace(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

internal object LudoPawsYardHabitatGeometry {
    fun face(color: String, cell: Float): LudoPawsYardHabitatFace? {
        if (!cell.isFinite() || cell <= 0f) return null
        val (row, col) = when (color) {
            "RED" -> 0 to 0
            "GREEN" -> 0 to 9
            "YELLOW" -> 9 to 9
            "BLUE" -> 9 to 0
            else -> return null
        }
        val left = axisBoundary(col, cell)
        val top = axisBoundary(row, cell)
        val right = axisBoundary(col + 6, cell)
        val bottom = axisBoundary(row + 6, cell)
        // Cover the COMPLETE color quadrant, not just the 4x4 inner pad.
        // Removing all insets is intentional: no visible RED/GREEN/YELLOW/
        // BLUE band should remain between the illustrated scene and the road.
        return LudoPawsYardHabitatFace(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        )
    }

    private fun axisBoundary(index: Int, cell: Float): Float {
        val yardSpan = cell * 5f
        val roadSpan = cell * 5f
        val yardStep = yardSpan / 6f
        val roadStep = roadSpan / 3f
        return when {
            index <= 6 -> index * yardStep
            index <= 9 -> yardSpan + (index - 6) * roadStep
            else -> yardSpan + roadSpan + (index - 9) * yardStep
        }
    }
}
