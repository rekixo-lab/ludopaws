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
    fun occupiedColorAutomaticallySwapsExactlyTwoSeats() {
        val original = listOf("BLUE", "RED", "GREEN", "YELLOW")
        val swapped = OfflinePlayerLayout.selectColorForSeat(4, original, 2, "RED")
        assertEquals(listOf("BLUE", "GREEN", "RED", "YELLOW"), swapped)
        assertEquals(original, OfflinePlayerLayout.selectColorForSeat(4, swapped, 2, "GREEN"))
        assertEquals(4, swapped.distinct().size)
        assertEquals(original, listOf("BLUE", "RED", "GREEN", "YELLOW"))
    }

    @Test
    fun firstPlayersRedRemainsRedWhenSecondChoosesBlue() {
        val first = OfflinePlayerLayout.selectColorForSeat(2, listOf("BLUE", "GREEN"), 0, "RED")
        assertEquals(listOf("RED", "GREEN"), first)
        val second = OfflinePlayerLayout.selectColorForSeat(2, first, 1, "BLUE")
        assertEquals(listOf("RED", "BLUE"), second)
        assertEquals(second, OfflinePlayerLayout.normalizeColorsForSeats(2, second, "RED"))

        // If P2 deliberately taps P1's RED, swap only those two assignments.
        val swapped = OfflinePlayerLayout.selectColorForSeat(2, second, 1, "RED")
        assertEquals(listOf("BLUE", "RED"), swapped)
    }

    @Test
    fun threeAndFourPlayerSelectionsOnlyAffectTheChooserAndCurrentOwner() {
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
            val swapped = OfflinePlayerLayout.selectColorForSeat(
                playerCount, baseline, 1, "RED",
            )
            assertEquals(baseline[1], swapped[0])
            assertEquals("RED", swapped[1])
            assertEquals(baseline.drop(2), swapped.drop(2))
        }
    }

    @Test
    fun fourPlayerYellowAndRedSwapWithoutChangingOtherPlayers() {
        val initial = listOf("BLUE", "YELLOW", "RED", "GREEN")
        val result = OfflinePlayerLayout.selectColorForSeat(4, initial, 1, "RED")
        assertEquals(listOf("BLUE", "RED", "YELLOW", "GREEN"), result)
        assertEquals(result.size, result.distinct().size)
        assertEquals(result, OfflinePlayerLayout.normalizeColorsForSeats(4, result, "BLUE"))
        assertEquals(result, OfflinePlayerLayout.selectColorForSeat(4, result, 1, "RED"))
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
    fun everyColorPermutationPreservesUnrelatedPlayerSeats() {
        val palette = OfflinePlayerLayout.COLORS
        for (count in 2..4) {
            fun verifyArrangement(assigned: List<String>) {
                if (assigned.size < count) {
                    for (color in palette.filterNot(assigned::contains)) {
                        verifyArrangement(assigned + color)
                    }
                    return
                }
                for (seat in assigned.indices) {
                    for (requested in palette) {
                        val owner = assigned.indexOf(requested)
                        val selected = OfflinePlayerLayout.selectColorForSeat(
                            count, assigned, seat, requested,
                        )
                        val expected = assigned.toMutableList()
                        val oldColor = expected[seat]
                        expected[seat] = requested
                        if (owner >= 0 && owner != seat) expected[owner] = oldColor
                        assertEquals(
                            "count=$count, original=$assigned, seat=$seat, color=$requested",
                            expected, selected,
                        )
                        assertEquals(count, selected.distinct().size)
                        assertEquals(
                            selected,
                            OfflinePlayerLayout.normalizeColorsForSeats(
                                count, selected, selected.first(),
                            ),
                        )
                        for (other in assigned.indices) {
                            if (other != seat && other != owner) {
                                assertEquals(assigned[other], selected[other])
                            }
                        }
                    }
                }
            }
            verifyArrangement(emptyList())
        }
    }

    @Test
    fun robotColorPickerKeepsHumanChoiceForTwoThreeAndFourPlayers() {
        for (players in 2..4) {
            var assigned = OfflinePlayerLayout.colorsFor(players, "BLUE")
            for (requested in OfflinePlayerLayout.COLORS) {
                assigned = OfflinePlayerLayout.selectColorForSeat(
                    playerCount = players,
                    currentColors = assigned,
                    selectedSeat = 0,
                    requestedColor = requested,
                )
                assertEquals(requested, assigned.first())
                assertEquals(players, assigned.size)
                assertEquals(players, assigned.distinct().size)
                assertEquals(
                    assigned,
                    OfflinePlayerLayout.normalizeColorsForSeats(players, assigned, requested),
                )
                // Robot match creation consumes the selected human color.
                assertEquals(requested, OfflinePlayerLayout.colorsFor(players, requested).first())
            }
        }
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
