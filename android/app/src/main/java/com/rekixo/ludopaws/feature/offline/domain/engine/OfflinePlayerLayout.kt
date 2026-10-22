package com.rekixo.ludopaws

object OfflinePlayerLayout {
    val COLORS =
        listOf(
            "RED",
            "GREEN",
            "YELLOW",
            "BLUE",
        )

    enum class Slot {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
    }

    fun colorsFor(
        playerCount: Int,
        preferredColor: String,
    ): List<String> {
        require(
            playerCount in
                2..4,
        )
        require(
            preferredColor in
                COLORS,
        )

        if (
            playerCount ==
            2
        ) {
            return listOf(
                preferredColor,
                oppositeColor(
                    preferredColor,
                ),
            )
        }

        return (
            listOf(
                preferredColor,
            ) +
                COLORS.filter {
                    it !=
                        preferredColor
                }
            )
            .take(
                playerCount,
            )
    }

    fun slotForColor(
        color: String,
    ): Slot =
        slotForColor(
            color =
                color,
            preferredBottomLeftColor =
                "BLUE",
        )

    fun slotForColor(
        color: String,
        preferredBottomLeftColor: String,
    ): Slot {
        val rotation =
            rotationQuarterTurns(
                preferredBottomLeftColor,
            )
        val base =
            colorIndex(
                color,
            )
        return slotForIndex(
            (
                base +
                    rotation
                ) %
                4,
        )
    }

    fun rotationQuarterTurns(
        preferredBottomLeftColor: String,
    ): Int =
        (
            3 -
                colorIndex(
                    preferredBottomLeftColor,
                ) +
                4
            ) %
            4

    /**
     * A player seat owns its selected board color. Two players use opposite
     * corners; three and four player games use distinct colors.
     */
    fun normalizeColorsForSeats(
        playerCount: Int,
        requestedColors: List<String>,
        preferredColor: String = "BLUE",
    ): List<String> {
        require(playerCount in 2..4)
        val safePreferred = preferredColor.takeIf(COLORS::contains) ?: "BLUE"
        val defaults = colorsFor(playerCount, safePreferred)
        if (requestedColors.isEmpty()) return defaults
        val assigned = MutableList<String?>(playerCount) { null }
        val used = mutableSetOf<String>()
        for (seat in 0 until playerCount) {
            val requested = requestedColors.getOrNull(seat)
            if (requested != null && requested in COLORS && used.add(requested)) {
                assigned[seat] = requested
            }
        }
        for (seat in 0 until playerCount) {
            if (assigned[seat] == null) {
                val fallback = (defaults + COLORS).first { it !in used }
                assigned[seat] = fallback
                used += fallback
            }
        }
        val result = assigned.map { requireNotNull(it) }
        return if (playerCount == 2) {
            listOf(result.first(), oppositeColor(result.first()))
        } else {
            result
        }
    }

    /** Selecting an occupied color swaps owners instead of making duplicates. */
    fun selectColorForSeat(
        playerCount: Int,
        currentColors: List<String>,
        selectedSeat: Int,
        requestedColor: String,
    ): List<String> {
        require(selectedSeat in 0 until playerCount)
        require(requestedColor in COLORS)
        val colors = normalizeColorsForSeats(
            playerCount, currentColors, currentColors.firstOrNull() ?: "BLUE",
        ).toMutableList()
        if (playerCount == 2) {
            return if (selectedSeat == 0) {
                listOf(requestedColor, oppositeColor(requestedColor))
            } else {
                listOf(oppositeColor(requestedColor), requestedColor)
            }
        }
        val previous = colors[selectedSeat]
        val owner = colors.indexOf(requestedColor)
        colors[selectedSeat] = requestedColor
        if (owner >= 0 && owner != selectedSeat) colors[owner] = previous
        return colors
    }

    private fun colorIndex(
        color: String,
    ): Int =
        when (color) {
            "RED" -> 0
            "GREEN" -> 1
            "YELLOW" -> 2
            "BLUE" -> 3
            else ->
                error(
                    "Unsupported Ludo color: $color",
                )
        }

    private fun slotForIndex(
        index: Int,
    ): Slot =
        when (
            index %
                4
        ) {
            0 -> Slot.TOP_LEFT
            1 -> Slot.TOP_RIGHT
            2 -> Slot.BOTTOM_RIGHT
            else -> Slot.BOTTOM_LEFT
        }

    fun oppositeColor(
        color: String,
    ): String =
        when (color) {
            "RED" ->
                "YELLOW"
            "YELLOW" ->
                "RED"
            "GREEN" ->
                "BLUE"
            "BLUE" ->
                "GREEN"
            else ->
                error(
                    "Unsupported Ludo color: $color",
                )
        }
}
