package com.rekixo.ludopaws

/**
 * Presentation-geometry mirror for the approved/final Ludo board.
 *
 * IMPORTANT: gameplay stays on the logical 15x15 grid, but the released board is not rendered
 * as 15 uniform visual cells. The four yards and the three-lane road are distributed through
 * the locked axisBoundary() mapping used by LudoBoardView and AnimalPawnOverlayView.
 *
 * Do not replace these calculations with row/column * cell math. Capture-return animation,
 * pawn trails, reaction FX, yard destinations, and finish FX must remain pixel-aligned with the
 * locked board without changing any gameplay rules or token positions.
 */
internal object LudoPawsFxBoardGeometry {
    const val BOARD_SIZE = 15f

    fun tokenCenter(
        color: String,
        tokenIndex: Int,
        position: Int,
        cell: Float,
        perspectiveQuarterTurns: Int = 0,
    ): Pair<Float, Float>? {
        val renderPosition =
            LudoPathEncoding
                .normalizeLegacyEntry(position)

        if (renderPosition == -1) {
            return yardTokenCenter(
                color = color,
                tokenIndex = tokenIndex,
                cell = cell,
                perspectiveQuarterTurns = perspectiveQuarterTurns,
            )
        }

        if (LudoPathEncoding.isTrackPosition(renderPosition)) {
            val offset = START_OFFSETS[color] ?: return null
            val coord = TRACK[(offset + renderPosition) % TRACK.size]
            return centerForCell(
                row = coord.first,
                col = coord.second,
                cell = cell,
            )
        }

        if (
            renderPosition in
            LudoPathEncoding.FIRST_HOME_LANE_POSITION..
                LudoPathEncoding.LAST_HOME_LANE_POSITION
        ) {
            val lane = HOME_LANES[color] ?: return null
            val coord =
                lane[
                    renderPosition -
                        LudoPathEncoding.FIRST_HOME_LANE_POSITION
                ]
            return centerForCell(
                row = coord.first,
                col = coord.second,
                cell = cell,
            )
        }

        if (renderPosition == LudoPathEncoding.HOME_POSITION) {
            return finishGardenCenter(color, tokenIndex, cell)
        }

        return null
    }

    /** Stable visual-only courtyard slots; arrival FX and both pawn renderers share them. */
    fun finishGardenCenter(color: String, tokenIndex: Int, cell: Float): Pair<Float, Float> {
        val left = axisBoundary(6, cell)
        val right = axisBoundary(9, cell)
        val center = (left + right) * .5f
        val halfSpan = (right - left) * .5f
        // Four individual spots leave the middle path and corner planting clear.
        val lateral = (tokenIndex.coerceIn(0, 3) - 1.5f) * .32f * halfSpan
        val outward = .90f * halfSpan
        return when (color) {
            "RED" -> center - outward to center + lateral
            "GREEN" -> center - lateral to center - outward
            "YELLOW" -> center + outward to center - lateral
            "BLUE" -> center + lateral to center + outward
            else -> center to center
        }
    }

    /**
     * Interpolates a moving pawn between its authoritative board-plane cell centers.
     *
     * Keep this interpolation on the path itself. The approved board stretches three logical
     * road rows/columns into five physical cell widths, so adding a board-center-directed X/Y
     * jump arc can cross a neighboring road boundary (most visibly on the top/bottom roads).
     * Jump height belongs to the character's 3D pose/lift; it must not bend the board-plane path.
     */
    fun interpolateMovementCenter(
        from: Pair<Float, Float>,
        to: Pair<Float, Float>,
        progress: Float,
    ): Pair<Float, Float> {
        val t = progress.coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        return (from.first + (to.first - from.first) * eased) to
            (from.second + (to.second - from.second) * eased)
    }

    /**
     * Center of the fixed physical yard. Character-reaction portraits use this instead of the
     * old logical 3/12-cell anchors so they stay centered after the road-stretch redesign.
     */
    fun yardReactionAnchor(
        color: String,
        cell: Float,
    ): Pair<Float, Float>? {
        val origin =
            when (color) {
                "RED" -> 0 to 0
                "GREEN" -> 0 to 9
                "YELLOW" -> 9 to 9
                "BLUE" -> 9 to 0
                else -> return null
            }

        val left = axisBoundary(origin.second, cell)
        val top = axisBoundary(origin.first, cell)
        val right = axisBoundary(origin.second + 6, cell)
        val bottom = axisBoundary(origin.first + 6, cell)

        return (left + right) / 2f to
            (top + bottom) / 2f
    }

    private fun yardTokenCenter(
        color: String,
        tokenIndex: Int,
        cell: Float,
        perspectiveQuarterTurns: Int,
    ): Pair<Float, Float>? {
        val origin =
            when (color) {
                "RED" -> 0 to 0
                "GREEN" -> 0 to 9
                "YELLOW" -> 9 to 9
                "BLUE" -> 9 to 0
                else -> return null
            }

        val yardLeft = axisBoundary(origin.second, cell)
        val yardTop = axisBoundary(origin.first, cell)
        val whiteLeft = yardLeft + cell * 0.5f
        val whiteTop = yardTop + cell * 0.5f
        val whiteSize = cell * 4f

        val rowFraction =
            if (tokenIndex < 2) 0.25f else 0.75f
        val colFraction =
            if (tokenIndex % 2 == 0) 0.25f else 0.75f

        // Eight percent of the full five-cell yard, expressed before board rotation.
        // Inverse-rotate screen-down so every perspective still moves feet down on screen.
        val distance = cell * .40f
        val offset = when (Math.floorMod(perspectiveQuarterTurns, 4)) {
            1 -> distance to 0f
            2 -> 0f to -distance
            3 -> -distance to 0f
            else -> 0f to distance
        }
        return (whiteLeft + whiteSize * colFraction + offset.first) to
            (whiteTop + whiteSize * rowFraction + offset.second)
    }

    private fun centerForCell(
        row: Int,
        col: Int,
        cell: Float,
    ): Pair<Float, Float> =
        ((axisBoundary(col, cell) +
            axisBoundary(col + 1, cell)) / 2f) to
            ((axisBoundary(row, cell) +
                axisBoundary(row + 1, cell)) / 2f)

    /**
     * FINAL BOARD GEOMETRY MIRROR — LOCKED.
     *
     * Must remain identical to LudoBoardView.axisBoundary() and
     * AnimalPawnOverlayView.axisBoundary():
     * - first yard span = 5 base cells
     * - stretched three-lane road = 5 base cells
     * - opposite yard span = 5 base cells
     * - each six-logical-cell yard therefore uses a 5-cell physical span
     * - each three-logical-cell road uses a 5-cell physical span
     */
    private fun axisBoundary(
        index: Int,
        cell: Float,
    ): Float {
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

    private val START_OFFSETS =
        mapOf(
            "RED" to 0,
            "GREEN" to 13,
            "YELLOW" to 26,
            "BLUE" to 39,
        )

    private val TRACK =
        listOf(
            6 to 1, 6 to 2, 6 to 3, 6 to 4, 6 to 5,
            5 to 6, 4 to 6, 3 to 6, 2 to 6, 1 to 6,
            0 to 6, 0 to 7, 0 to 8, 1 to 8, 2 to 8,
            3 to 8, 4 to 8, 5 to 8, 6 to 9, 6 to 10,
            6 to 11, 6 to 12, 6 to 13, 6 to 14, 7 to 14,
            8 to 14, 8 to 13, 8 to 12, 8 to 11, 8 to 10,
            8 to 9, 9 to 8, 10 to 8, 11 to 8, 12 to 8,
            13 to 8, 14 to 8, 14 to 7, 14 to 6, 13 to 6,
            12 to 6, 11 to 6, 10 to 6, 9 to 6, 8 to 5,
            8 to 4, 8 to 3, 8 to 2, 8 to 1, 8 to 0,
            7 to 0, 6 to 0,
        )

    private val HOME_LANES =
        mapOf(
            "RED" to listOf(7 to 1, 7 to 2, 7 to 3, 7 to 4, 7 to 5),
            "GREEN" to listOf(1 to 7, 2 to 7, 3 to 7, 4 to 7, 5 to 7),
            "YELLOW" to listOf(7 to 13, 7 to 12, 7 to 11, 7 to 10, 7 to 9),
            "BLUE" to listOf(13 to 7, 12 to 7, 11 to 7, 10 to 7, 9 to 7),
        )
}
