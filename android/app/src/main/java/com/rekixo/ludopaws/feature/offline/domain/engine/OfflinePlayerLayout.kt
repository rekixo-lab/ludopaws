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
     * Each seat owns its selected color. Initial two-player defaults use opposite
     * corners, but explicit selections can use any two distinct colors.
     * Revalidating persisted choices must NEVER rewrite a valid seat color.
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
        return result
    }

    /**
     * Apply a color tap immediately for Pass & Play.
     *
     * An unclaimed color changes only this seat. If another seat already owns
     * the requested color, exchange the two colors atomically while leaving
     * all other player seats untouched. Selecting the current color is a no-op.
     */
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
        val owner = colors.indexOf(requestedColor)
        if (owner == selectedSeat) return colors

        val previousColor = colors[selectedSeat]
        colors[selectedSeat] = requestedColor
        if (owner >= 0) {
            colors[owner] = previousColor
        }
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
