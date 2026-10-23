package com.rekixo.ludopaws

/**
 * Full-color yard quadrant (5 x 5 physical cells), including the former
 * outer plain color strip. The approved art fills these exact bounds.
 *
 * The Ludo board uses a NON-UNIFORM 15x15 logical coordinate layout: each
 * six-logical-cell yard spans five physical cells. Match axisBoundary() rather
 * than calculating pixels from logical indices directly.
 *
 * The environment replaces that entire home background. Road, center and
 * authoritative pawn coordinates remain completely unchanged.
 */
internal data class LudoPawsYardFace(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

internal object LudoPawsYardGeometry {
    fun face(color: String, cell: Float): LudoPawsYardFace? {
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
        // Cover the COMPLETE color quadrant, not just the 4x4 inner pad.
        // Removing all insets is intentional: no visible RED/GREEN/YELLOW/
        // BLUE band should remain between the illustrated scene and the road.
        return LudoPawsYardFace(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
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
