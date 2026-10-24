package com.rekixo.ludopaws

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoPawsFxBoardGeometryTest {
    @Test
    fun `track center follows stretched locked geometry`() {
        val cell = 60f
        val center =
            requireNotNull(
                LudoPawsFxBoardGeometry.tokenCenter(
                    color = "RED",
                    tokenIndex = 0,
                    position = 0,
                    cell = cell,
                ),
            )

        assertEquals(1.25f * cell, center.first, EPSILON)
        assertEquals((35f / 6f) * cell, center.second, EPSILON)
    }

    @Test
    fun `home lane center follows stretched three lane road`() {
        val cell = 60f
        val center =
            requireNotNull(
                LudoPawsFxBoardGeometry.tokenCenter(
                    color = "RED",
                    tokenIndex = 0,
                    position = 52,
                    cell = cell,
                ),
            )

        assertEquals(1.25f * cell, center.first, EPSILON)
        assertEquals(7.5f * cell, center.second, EPSILON)
    }

    @Test
    fun `legacy position 51 renders on first home lane instead of extra white track cell`() {
        val cell = 60f
        val legacy =
            requireNotNull(
                LudoPawsFxBoardGeometry.tokenCenter(
                    color = "BLUE",
                    tokenIndex = 0,
                    position = 51,
                    cell = cell,
                ),
            )
        val firstLane =
            requireNotNull(
                LudoPawsFxBoardGeometry.tokenCenter(
                    color = "BLUE",
                    tokenIndex = 0,
                    position = 52,
                    cell = cell,
                ),
            )

        assertEquals(firstLane.first, legacy.first, EPSILON)
        assertEquals(firstLane.second, legacy.second, EPSILON)
    }

    @Test
    fun `yard token slots retain the approved downward foot offset`() {
        val cell = 60f
        val redTopLeft =
            requireNotNull(
                LudoPawsFxBoardGeometry.tokenCenter(
                    color = "RED",
                    tokenIndex = 0,
                    position = -1,
                    cell = cell,
                ),
            )
        val yellowBottomRight =
            requireNotNull(
                LudoPawsFxBoardGeometry.tokenCenter(
                    color = "YELLOW",
                    tokenIndex = 3,
                    position = -1,
                    cell = cell,
                ),
            )

        assertEquals(1.5f * cell, redTopLeft.first, EPSILON)
        assertEquals(1.9f * cell, redTopLeft.second, EPSILON)
        assertEquals(13.5f * cell, yellowBottomRight.first, EPSILON)
        assertEquals(13.9f * cell, yellowBottomRight.second, EPSILON)
    }

    @Test
    fun `finish courts have four distinct slots inside the center in every perspective`() {
        val cell = 60f
        for (color in listOf("RED", "GREEN", "YELLOW", "BLUE")) {
            for (turns in 0..3) {
                val slots = (0..3).map { token ->
                    requireNotNull(LudoPawsFxBoardGeometry.tokenCenter(
                        color, token, LudoPathEncoding.HOME_POSITION, cell, turns,
                    ))
                }
                assertEquals("$color perspective $turns must not stack finish slots", 4, slots.toSet().size)
                slots.forEach { (x, y) ->
                    assertTrue("Finish slot outside garden", x > 5f * cell && x < 10f * cell)
                    assertTrue("Finish slot outside garden", y > 5f * cell && y < 10f * cell)
                }
                for (i in slots.indices) for (j in i + 1 until slots.size) {
                    val dx = slots[i].first - slots[j].first
                    val dy = slots[i].second - slots[j].second
                    assertTrue("Finish slots must leave space between animals", dx * dx + dy * dy > cell * cell * .25f)
                }
            }
        }
        // First members stand alongside the entrance, not on its central approach.
        val red = finishCenter("RED", cell)
        assertEquals(5.8f * cell, red.first, EPSILON)
        assertEquals(6.225f * cell, red.second, EPSILON)
    }

    @Test
    fun `reaction anchors stay centered in fixed physical yards`() {
        val cell = 60f

        assertPoint(2.5f, 2.5f, "RED", cell)
        assertPoint(12.5f, 2.5f, "GREEN", cell)
        assertPoint(12.5f, 12.5f, "YELLOW", cell)
        assertPoint(2.5f, 12.5f, "BLUE", cell)
        assertNull(
            LudoPawsFxBoardGeometry.yardReactionAnchor(
                color = "UNKNOWN",
                cell = cell,
            ),
        )
    }

    @Test
    fun topRoadMovementRemainsCenteredInAllThreeWideCells() {
        val cell = 60f
        val centers =
            (10..12).map { position ->
                requireNotNull(
                    LudoPawsFxBoardGeometry.tokenCenter(
                        color = "RED",
                        tokenIndex = 0,
                        position = position,
                        cell = cell,
                    ),
                )
            }

        // These top-row cells are 50 px tall on the locked stretched board.
        // The pawn center must stay on the row center at every animation fraction.
        centers.forEach { center ->
            assertEquals(25f, center.second, EPSILON)
        }
        for (segment in 0 until centers.lastIndex) {
            for (progress in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val point =
                    LudoPawsFxBoardGeometry.interpolateMovementCenter(
                        from = centers[segment],
                        to = centers[segment + 1],
                        progress = progress,
                    )
                assertEquals(25f, point.second, EPSILON)
            }
        }
    }

    @Test
    fun straightTrackInterpolationNeverDriftsOffItsRowOrColumnForAnyColor() {
        val cell = 60f
        val colors = listOf("RED", "GREEN", "YELLOW", "BLUE")
        val samples = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

        colors.forEach { color ->
            for (position in 0 until LudoPathEncoding.LAST_TRACK_POSITION) {
                val from =
                    requireNotNull(
                        LudoPawsFxBoardGeometry.tokenCenter(
                            color = color,
                            tokenIndex = 0,
                            position = position,
                            cell = cell,
                        ),
                    )
                val to =
                    requireNotNull(
                        LudoPawsFxBoardGeometry.tokenCenter(
                            color = color,
                            tokenIndex = 0,
                            position = position + 1,
                            cell = cell,
                        ),
                    )
                val staysOnSameRow = kotlin.math.abs(from.second - to.second) < EPSILON
                val staysOnSameColumn = kotlin.math.abs(from.first - to.first) < EPSILON
                if (!staysOnSameRow && !staysOnSameColumn) continue

                samples.forEach { progress ->
                    val point =
                        LudoPawsFxBoardGeometry.interpolateMovementCenter(
                            from = from,
                            to = to,
                            progress = progress,
                        )
                    if (staysOnSameRow) {
                        assertEquals(
                            "row drift for $color at track position $position, progress $progress",
                            from.second,
                            point.second,
                            EPSILON,
                        )
                    }
                    if (staysOnSameColumn) {
                        assertEquals(
                            "column drift for $color at track position $position, progress $progress",
                            from.first,
                            point.first,
                            EPSILON,
                        )
                    }
                }
            }
        }
    }

    private fun finishCenter(
        color: String,
        cell: Float,
    ): Pair<Float, Float> =
        requireNotNull(
            LudoPawsFxBoardGeometry.tokenCenter(
                color = color,
                tokenIndex = 0,
                position = 57,
                cell = cell,
            ),
        )

    private fun assertPoint(
        xCells: Float,
        yCells: Float,
        color: String,
        cell: Float,
    ) {
        val point =
            requireNotNull(
                LudoPawsFxBoardGeometry.yardReactionAnchor(
                    color = color,
                    cell = cell,
                ),
            )
        assertEquals(xCells * cell, point.first, EPSILON)
        assertEquals(yCells * cell, point.second, EPSILON)
    }

    private companion object {
        const val EPSILON = 0.01f
    }
}
