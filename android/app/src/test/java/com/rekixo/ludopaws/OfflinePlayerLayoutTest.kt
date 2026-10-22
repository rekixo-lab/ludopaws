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
    fun occupiedColorDoesNotChangeOtherSeatsWithoutConfirmation() {
        val original = listOf("BLUE", "RED", "GREEN", "YELLOW")
        val blocked = OfflinePlayerLayout.selectColorForSeat(4, original, 2, "RED")
        assertEquals(original, blocked)
        val swapped = OfflinePlayerLayout.swapColorForSeat(4, original, 2, "RED")
        assertEquals(listOf("BLUE", "GREEN", "RED", "YELLOW"), swapped)
        assertEquals(4, swapped.distinct().size)
    }

    @Test
    fun firstPlayersRedRemainsRedWhenSecondChoosesBlue() {
        val first = OfflinePlayerLayout.selectColorForSeat(2, listOf("BLUE", "GREEN"), 0, "RED")
        assertEquals(listOf("RED", "GREEN"), first)
        val second = OfflinePlayerLayout.selectColorForSeat(2, first, 1, "BLUE")
        assertEquals(listOf("RED", "BLUE"), second)
        assertEquals(second, OfflinePlayerLayout.normalizeColorsForSeats(2, second, "RED"))
        assertEquals(second, OfflinePlayerLayout.selectColorForSeat(2, second, 1, "RED"))
    }

    @Test
    fun changingP2NeverAltersP1AcrossThreeAndFourPlayerModes() {
        for (playerCount in 3..4) {
            val baseline = OfflinePlayerLayout.normalizeColorsForSeats(
                playerCount, emptyList(), "RED",
            )
            val available = OfflinePlayerLayout.COLORS.firstOrNull { it !in baseline }
            if (available != null) {
                val updated = OfflinePlayerLayout.selectColorForSeat(
                    playerCount, baseline, 1, available,
                )
                assertEquals("RED", updated.first())
                assertEquals(available, updated[1])
                assertEquals(playerCount, updated.distinct().size)
            }
            val duplicate = OfflinePlayerLayout.selectColorForSeat(
                playerCount, baseline, 1, "RED",
            )
            assertEquals(baseline, duplicate)
        }
    }

    @Test
    fun confirmedSwapCanExchangeColorsButNeverDuplicatesThem() {
        val initial = listOf("RED", "GREEN", "YELLOW", "BLUE")
        assertEquals(initial, OfflinePlayerLayout.selectColorForSeat(4, initial, 1, "RED"))
        val confirmed = OfflinePlayerLayout.swapColorForSeat(4, initial, 1, "BLUE")
        assertEquals(listOf("RED", "BLUE", "YELLOW", "GREEN"), confirmed)
        assertEquals(confirmed.size, confirmed.distinct().size)
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
            listOf("BLUE", "RED"),
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
