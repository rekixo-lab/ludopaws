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
                LudoPathEncoding.HOME_POSITION -> 0.35f
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

    /**
     * Builds one deterministic stack layout for the complete board snapshot.
     * Pawns sharing the same rendered cell are ordered by seat and token index,
     * so renderer, legal halo and hit-testing always agree on the same geometry.
     */
    internal fun stackPlacements(
        snapshot: MatchSnapshot,
        cell: Float,
    ): Map<LudoPawsPawnVisualKey, LudoPawsPawnStackPlacement> {
        val cellBits = java.lang.Float.floatToIntBits(cell)
        val cached = stackPlacementCache.get()
        if (
            cached != null &&
            cached.snapshotRef.get() === snapshot &&
            cached.cellBits == cellBits
        ) {
            return cached.placements
        }

        val groups = linkedMapOf<String, MutableList<LudoPawsPawnVisualKey>>()

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
                    groups
                        .getOrPut(centerKey(center)) { mutableListOf() }
                        .add(key)
                }
            }

        val placements =
            buildMap {
                groups.values.forEach { occupants ->
                    val occupancy = occupants.size
                    occupants.forEachIndexed { slot, key ->
                        val offset =
                            stackOffsetFraction(
                                slot = slot,
                                occupancy = occupancy,
                            )
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
