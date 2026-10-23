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
 * The inside face of the existing raised home platform. Calculations mirror
 * ClassicBoardRenderer.drawRaisedYard and the locked 15x15 stretched board.
 * Only paint is added inside these bounds; no board or pawn geometry changes.
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
        // Identical to: platform(0.5 cell inset) + face insets in
        // ClassicBoardRenderer. Do not replace with a uniform-grid rect.
        return LudoPawsYardHabitatFace(
            left = left + cell * (0.50f + 0.115f),
            top = top + cell * (0.50f + 0.105f),
            right = right - cell * (0.50f + 0.115f),
            bottom = bottom - cell * (0.50f + 0.145f),
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
