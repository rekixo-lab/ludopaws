package com.rekixo.ludopaws

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflinePlayerLayoutTest {
    @Test
    fun passAndPlayThreePlayersKeepIndividualSelectedColors() {
        val a = OfflinePlayerLayout.selectColorForSeat(3, listOf("BLUE", "RED", "GREEN"), 0, "YELLOW")
        val b = OfflinePlayerLayout.selectColorForSeat(3, a, 1, "BLUE")
        val c = OfflinePlayerLayout.selectColorForSeat(3, b, 2, "RED")
        assertEquals(listOf("YELLOW", "BLUE", "RED"), c)
    }

    @Test
    fun fourPlayersSwapColorsWithoutDuplicates() {
        val result = OfflinePlayerLayout.selectColorForSeat(
            4, listOf("BLUE", "RED", "GREEN", "YELLOW"), 2, "RED",
        )
        assertEquals(listOf("BLUE", "GREEN", "RED", "YELLOW"), result)
        assertEquals(4, result.distinct().size)
    }

    @Test
    fun twoPlayersKeepOppositeColorsWhenEitherPlayerChooses() {
        val a = OfflinePlayerLayout.selectColorForSeat(2, listOf("BLUE", "GREEN"), 0, "RED")
        assertEquals(listOf("RED", "YELLOW"), a)
        val b = OfflinePlayerLayout.selectColorForSeat(2, a, 1, "BLUE")
        assertEquals(listOf("GREEN", "BLUE"), b)
    }

    @Test
    fun savedColorAssignmentsAreNormalizedAcrossPlayerCounts() {
        assertEquals(
            listOf("YELLOW", "RED", "BLUE", "GREEN"),
            OfflinePlayerLayout.normalizeColorsForSeats(
                4, listOf("YELLOW", "RED", "BLUE", "GREEN"),
            ),
        )
        assertEquals(
            listOf("BLUE", "GREEN"),
            OfflinePlayerLayout.normalizeColorsForSeats(
                2, listOf("BLUE", "RED"), "BLUE",
            ),
        )
        assertEquals(
            listOf("BLUE", "RED", "GREEN"),
            OfflinePlayerLayout.normalizeColorsForSeats(3, emptyList(), "BLUE"),
        )
    }

    @Test
    fun twoPlayerUsesOppositeBoardCorners() {
        assertEquals(
            listOf(
                "BLUE",
                "GREEN",
            ),
            OfflinePlayerLayout
                .colorsFor(
                    2,
                    "BLUE",
                ),
        )
        assertEquals(
            listOf(
                "RED",
                "YELLOW",
            ),
            OfflinePlayerLayout
                .colorsFor(
                    2,
                    "RED",
                ),
        )
    }

    @Test
    fun threeAndFourPlayerKeepDeterministicColorOrder() {
        assertEquals(
            listOf(
                "BLUE",
                "RED",
                "GREEN",
            ),
            OfflinePlayerLayout
                .colorsFor(
                    3,
                    "BLUE",
                ),
        )
        assertEquals(
            listOf(
                "RED",
                "GREEN",
                "YELLOW",
                "BLUE",
            ),
            OfflinePlayerLayout
                .colorsFor(
                    4,
                    "RED",
                ),
        )
    }

    @Test
    fun chosenColorAlwaysMapsToBottomLeft() {
        listOf(
            "RED",
            "GREEN",
            "YELLOW",
            "BLUE",
        ).forEach {
                color ->
            assertEquals(
                OfflinePlayerLayout
                    .Slot
                    .BOTTOM_LEFT,
                OfflinePlayerLayout
                    .slotForColor(
                        color =
                            color,
                        preferredBottomLeftColor =
                            color,
                    ),
            )
        }
    }

    @Test
    fun boardRotationQuarterTurnsAreDeterministic() {
        assertEquals(
            3,
            OfflinePlayerLayout
                .rotationQuarterTurns(
                    "RED",
                ),
        )
        assertEquals(
            2,
            OfflinePlayerLayout
                .rotationQuarterTurns(
                    "GREEN",
                ),
        )
        assertEquals(
            1,
            OfflinePlayerLayout
                .rotationQuarterTurns(
                    "YELLOW",
                ),
        )
        assertEquals(
            0,
            OfflinePlayerLayout
                .rotationQuarterTurns(
                    "BLUE",
                ),
        )
    }

    @Test
    fun colorsMapToReferenceBoardCorners() {
        assertEquals(
            OfflinePlayerLayout
                .Slot
                .TOP_LEFT,
            OfflinePlayerLayout
                .slotForColor(
                    "RED",
                ),
        )
        assertEquals(
            OfflinePlayerLayout
                .Slot
                .TOP_RIGHT,
            OfflinePlayerLayout
                .slotForColor(
                    "GREEN",
                ),
        )
        assertEquals(
            OfflinePlayerLayout
                .Slot
                .BOTTOM_LEFT,
            OfflinePlayerLayout
                .slotForColor(
                    "BLUE",
                ),
        )
        assertEquals(
            OfflinePlayerLayout
                .Slot
                .BOTTOM_RIGHT,
            OfflinePlayerLayout
                .slotForColor(
                    "YELLOW",
                ),
        )
    }
}
