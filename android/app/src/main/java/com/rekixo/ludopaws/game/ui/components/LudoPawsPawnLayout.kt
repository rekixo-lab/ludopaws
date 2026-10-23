package com.rekixo.ludopaws

import java.lang.ref.WeakReference
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class LudoPawsPawnVisualKey(
    val playerId: String,
    val tokenIndex: Int,
)

internal data class LudoPawsPawnStackPlacement(
    val slot: Int,
    val occupancy: Int,
    val offsetXFraction: Float,
    val offsetYFraction: Float,
)

/**
 * Presentation-only sizing and placement rules for Ludo Paws animal pawns.
 *
 * This policy deliberately contains no match-state mutation. It only converts
 * already-authoritative token positions into deterministic visual geometry.
 */
object LudoPawsPawnLayout {
    /** Uniform in-game animal enlargement; shared by renderer and selection geometry. */
    private const val ANIMAL_SIZE_MULTIPLIER = 1.32f
    private data class StackPlacementCache(
        val snapshotRef: WeakReference<MatchSnapshot>,
        val cellBits: Int,
        val turns: Int,
        val placements: Map<LudoPawsPawnVisualKey, LudoPawsPawnStackPlacement>,
    )

    /**
     * Renderer and UI geometry can ask for the same immutable snapshot many
     * times while an animation is running. Keep one tiny cache per calling
     * thread so repeated frame ticks reuse the deterministic stack map without
     * cross-thread synchronization. A weak snapshot key avoids retaining a
     * finished match from a long-lived UI thread.
     */
    private val stackPlacementCache =
        ThreadLocal<StackPlacementCache?>()

    fun characterIdForSeat(
        characterIdsBySeat: List<String>,
        seat: Int,
    ): String? =
        characterIdsBySeat
            .getOrNull(seat)
            ?.trim()
            ?.takeIf(String::isNotBlank)

    /**
     * Keeps single animal pawns prominent enough to read as characters rather
     * than generic dots while still shrinking deterministic shared-cell stacks.
     * These values affect presentation only; board coordinates never change.
     */
    fun radiusScale(
        position: Int,
        occupancy: Int,
    ): Float {
        val base =
            when (position) {
                -1 -> 0.40f
                // Finished animals use four separate courtyard slots, not a shared stack.
                LudoPathEncoding.HOME_POSITION -> 0.28f
                else -> 0.37f
            }
        val crowdScale =
            when {
                occupancy <= 1 -> 1f
                occupancy == 2 -> 0.92f
                occupancy == 3 -> 0.86f
                occupancy == 4 -> 0.80f
                occupancy <= 6 -> 0.72f
                occupancy <= 9 -> 0.64f
                else -> 0.56f
            }
        return base * crowdScale * ANIMAL_SIZE_MULTIPLIER
    }

    internal data class RoadPresentation(val radius: Float, val footDrop: Float)

    /** Shared foot placement; preserve the approved animal size on every road. */
    internal fun roadPresentation(
        x: Float, y: Float, cell: Float, position: Int, occupancy: Int,
        characterId: String, turns: Int,
    ): RoadPresentation {
        val original = cell * radiusScale(position, occupancy)
        if (position !in 0 until LudoPathEncoding.HOME_POSITION ||
            (x !in cell * 5f..cell * 10f && y !in cell * 5f..cell * 10f)) {
            return RoadPresentation(original, 0f)
        }
        val logicalWidth = if (x in cell * 5f..cell * 10f) cell * 5f / 3f else cell * 5f / 6f
        val logicalHeight = if (y in cell * 5f..cell * 10f) cell * 5f / 3f else cell * 5f / 6f
        val height = if (turns % 2 == 0) logicalHeight else logicalWidth
        val width = if (turns % 2 == 0) logicalWidth else logicalHeight
        val screenRows = roadRows(occupancy, width >= height).size
        val heightRatio = silhouetteHeight(characterId)
        // Move the feet toward the lower edge of their slot without shrinking the animal.
        val footDrop = minOf(original * heightRatio * .44f, height / screenRows * .38f)
        return RoadPresentation(original, footDrop)
    }

    private fun roadRows(occupancy: Int, wide: Boolean): List<Int> = when (occupancy) {
        1 -> listOf(1)
        2 -> if (wide) listOf(2) else listOf(1, 1)
        3 -> if (wide) listOf(3) else listOf(1, 2)
        4 -> listOf(2, 2)
        5 -> if (wide) listOf(3, 2) else listOf(2, 1, 2)
        else -> {
            val columns = if (wide) ceil(sqrt(occupancy * 2.0)).toInt()
                else ceil(sqrt(occupancy / 2.0)).toInt()
            val full = occupancy / columns
            List(full) { columns } + if (occupancy % columns > 0) listOf(occupancy % columns) else emptyList()
        }
    }

    private fun roadStackOffset(slot: Int, occupancy: Int, width: Float, height: Float): Pair<Float, Float> {
        val rows = roadRows(occupancy, width >= height)
        var remaining = slot
        var row = 0
        while (row < rows.lastIndex && remaining >= rows[row]) {
            remaining -= rows[row]
            row++
        }
        val count = rows[row]
        // Center each row independently, with a small stagger so rear faces peek through.
        val stagger = if (count > 1 && rows.size > 1) (if (row % 2 == 0) -.035f else .035f) * width else 0f
        val x = ((remaining + .5f) / count - .5f) * width * .94f + stagger
        val y = ((row + .5f) / rows.size - .5f) * height * .84f +
            if (count > 1 && width < height) (if (remaining % 2 == 0) -.025f else .025f) * height else 0f
        return x to y
    }

    internal fun silhouetteHeight(characterId: String): Float = when (characterId) {
        "donkey" -> 3.35f
        "goat" -> 3.15f
        "goat_lumi" -> 3.3f
        "duck" -> 2.85f
        else -> 3f
    }

    /**
     * Bind the animated 3D model's foot-ground pivot to the REAL tile center.
     *
     * Model local Y is positive up; projection mirrors it into screen Y which
     * is positive down. At the grounded foot point the projection is:
     *   screenFootY = rootY - modelScale * groundPivotY
     *
     * Solve for rootY rather than positioning the MODEL ORIGIN at the cell
     * center. This keeps the paw planted as the animal grows, breathes, reacts
     * or changes size in a crowded cell. No logical token path is changed.
     */
    fun rootYForFootContact(
        boardCenterY: Float,
        modelScale: Float,
        groundPivotY: Float,
    ): Float = boardCenterY + modelScale * groundPivotY

    /**
     * A tight contact mark shows the exact occupied board cell, even when
     * enlarged ears, heads and tails visually overhang adjacent cells.
     * Avoid the old, nearly two-cell-wide oval that suggested a false square.
     */
    fun contactShadowRadius(
        cell: Float,
        pawnRadius: Float,
        presentationScale: Float,
    ): Float =
        minOf(
            cell * 0.30f,
            pawnRadius * presentationScale * 0.70f,
        )

    /**
     * Builds one deterministic stack layout for the complete board snapshot.
     * Pawns sharing the same rendered cell are ordered by seat and token index,
     * so renderer, legal halo and hit-testing always agree on the same geometry.
     */
    internal fun stackPlacements(
        snapshot: MatchSnapshot,
        cell: Float,
        perspectiveQuarterTurns: Int = 0,
    ): Map<LudoPawsPawnVisualKey, LudoPawsPawnStackPlacement> {
        val turns = (perspectiveQuarterTurns % 4 + 4) % 4
        val cellBits = java.lang.Float.floatToIntBits(cell)
        val cached = stackPlacementCache.get()
        if (
            cached != null &&
            cached.snapshotRef.get() === snapshot &&
            cached.cellBits == cellBits && cached.turns == turns
        ) {
            return cached.placements
        }

        val groups = linkedMapOf<String, MutableList<LudoPawsPawnVisualKey>>()
        val centers = mutableMapOf<String, Pair<Float, Float>>()

        snapshot.players
            .sortedWith(
                compareBy<PlayerSnapshot> { it.seat }
                    .thenBy { it.playerId },
            )
            .forEach { player ->
                player.tokens.forEachIndexed { tokenIndex, position ->
                    val center =
                        LudoPawsFxBoardGeometry.tokenCenter(
                            color = player.color,
                            tokenIndex = tokenIndex,
                            position = position,
                            cell = cell,
                        ) ?: return@forEachIndexed
                    val key =
                        LudoPawsPawnVisualKey(
                            playerId = player.playerId,
                            tokenIndex = tokenIndex,
                        )
                    centers[centerKey(center)] = center
                    groups
                        .getOrPut(centerKey(center)) { mutableListOf() }
                        .add(key)
                }
            }

        val placements =
            buildMap {
                groups.forEach { (groupKey, occupants) ->
                    val occupancy = occupants.size
                    occupants.forEachIndexed { slot, key ->
                        val center = centers.getValue(groupKey)
                        val onRoad = center.first in cell * 5f..cell * 10f ||
                            center.second in cell * 5f..cell * 10f
                        val width = if (center.first in cell * 5f..cell * 10f) 5f / 3f else 5f / 6f
                        val height = if (center.second in cell * 5f..cell * 10f) 5f / 3f else 5f / 6f
                        val offset = if (onRoad && occupancy > 1) {
                            val screenWidth = if (turns % 2 == 0) width else height
                            val screenHeight = if (turns % 2 == 0) height else width
                            val screen = roadStackOffset(slot, occupancy, screenWidth, screenHeight)
                            // Return canonical offsets; renderer/touch apply the board rotation once.
                            when (turns) {
                                1 -> screen.second to -screen.first
                                2 -> -screen.first to -screen.second
                                3 -> -screen.second to screen.first
                                else -> screen
                            }
                        } else stackOffsetFraction(slot, occupancy)
                        put(
                            key,
                            LudoPawsPawnStackPlacement(
                                slot = slot,
                                occupancy = occupancy,
                                offsetXFraction = offset.first,
                                offsetYFraction = offset.second,
                            ),
                        )
                    }
                }
            }
        stackPlacementCache.set(
            StackPlacementCache(
                snapshotRef = WeakReference(snapshot),
                cellBits = cellBits,
                turns = turns,
                placements = placements,
            ),
        )
        return placements
    }

    internal fun stackOffsetFraction(
        slot: Int,
        occupancy: Int,
    ): Pair<Float, Float> {
        if (occupancy <= 1) return 0f to 0f

        val normalizedSlot =
            Math.floorMod(
                slot,
                occupancy,
            )
        return when (occupancy) {
            2 ->
                if (normalizedSlot == 0) {
                    -0.18f to 0f
                } else {
                    0.18f to 0f
                }

            3 ->
                when (normalizedSlot) {
                    0 -> 0f to -0.18f
                    1 -> -0.17f to 0.14f
                    else -> 0.17f to 0.14f
                }

            4 ->
                when (normalizedSlot) {
                    0 -> -0.16f to -0.16f
                    1 -> 0.16f to -0.16f
                    2 -> -0.16f to 0.16f
                    else -> 0.16f to 0.16f
                }

            else ->
                gridOffsetFraction(
                    slot = normalizedSlot,
                    occupancy = occupancy,
                )
        }
    }

    /**
     * Retained only for capture-return animation, where the captured pawn is
     * already in transit and no longer belongs to the current static stack.
     * Static board placement must use [stackPlacements].
     */
    fun tokenOffsetFraction(
        slot: Int,
        position: Int,
    ): Pair<Float, Float> {
        if (position !in 0..57) {
            return 0f to 0f
        }

        return when (Math.floorMod(slot, 4)) {
            0 -> -0.13f to -0.13f
            1 -> 0.13f to -0.13f
            2 -> -0.13f to 0.13f
            else -> 0.13f to 0.13f
        }
    }

    private fun gridOffsetFraction(
        slot: Int,
        occupancy: Int,
    ): Pair<Float, Float> {
        val columns =
            ceil(
                sqrt(occupancy.toDouble()),
            ).toInt().coerceAtLeast(2)
        val rows =
            ceil(
                occupancy.toDouble() /
                    columns.toDouble(),
            ).toInt()
        val column = slot % columns
        val row = slot / columns
        val spacing =
            when {
                occupancy <= 6 -> 0.16f
                occupancy <= 9 -> 0.14f
                else -> 0.12f
            }
        val x =
            (column - (columns - 1) / 2f) *
                spacing
        val y =
            (row - (rows - 1) / 2f) *
                spacing
        return x to y
    }

    private fun centerKey(
        center: Pair<Float, Float>,
    ): String =
        "${(center.first * 1_000f).roundToInt()}:" +
            "${(center.second * 1_000f).roundToInt()}"
}
