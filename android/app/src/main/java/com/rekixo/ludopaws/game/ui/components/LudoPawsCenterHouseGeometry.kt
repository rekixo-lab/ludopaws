package com.rekixo.ludopaws

/**
 * Visual-only geometry for four house roofs meeting at the approved finish.
 *
 * The existing nonuniform Ludo board projects logical boundaries 6 and 9 to
 * 5 and 10 physical cells. No token, path or game rule can be changed here.
 */
internal data class LudoPawsRoofPoint(val x: Float, val y: Float)

internal data class LudoPawsRoofTriangle(
    val start: LudoPawsRoofPoint,
    val end: LudoPawsRoofPoint,
    val apex: LudoPawsRoofPoint,
)

internal object LudoPawsCenterHouseGeometry {
    fun bounds(cell: Float): LudoPawsYardFace? {
        if (!cell.isFinite() || cell <= 0f) return null
        return LudoPawsYardFace(
            left = cell * 5f,
            top = cell * 5f,
            right = cell * 10f,
            bottom = cell * 10f,
        )
    }

    /** Normalized 100x100 center square; all four roof apexes are (50,50). */
    fun triangle(team: String): LudoPawsRoofTriangle? {
        val p = LudoPawsRoofPoint(50f, 50f)
        return when (team) {
            "GREEN" -> LudoPawsRoofTriangle(
                LudoPawsRoofPoint(0f, 0f), LudoPawsRoofPoint(100f, 0f), p,
            )
            "YELLOW" -> LudoPawsRoofTriangle(
                LudoPawsRoofPoint(100f, 0f), LudoPawsRoofPoint(100f, 100f), p,
            )
            "BLUE" -> LudoPawsRoofTriangle(
                LudoPawsRoofPoint(100f, 100f), LudoPawsRoofPoint(0f, 100f), p,
            )
            "RED" -> LudoPawsRoofTriangle(
                LudoPawsRoofPoint(0f, 100f), LudoPawsRoofPoint(0f, 0f), p,
            )
            else -> null
        }
    }

    /**
     * Only the illustrated ROOF is smaller; the full 100x50 team wedge and
     * its finish apex remain unchanged. The raised base exposes more facade.
     *
     * Original roof: (11.8,15)–(88.2,15)–(50,50), area 1337 normalized².
     * Compact roof:  (25,22)–(75,22)–(50,50), area 700 normalized².
     * Both sloping sides stay inside the team's fixed triangle.
     */
    fun compactRoof(): LudoPawsRoofTriangle = LudoPawsRoofTriangle(
        start = LudoPawsRoofPoint(25f, 22f),
        end = LudoPawsRoofPoint(75f, 22f),
        apex = LudoPawsRoofPoint(50f, 50f),
    )

    /** Rotate one top-facing house clockwise into the existing team wedge. */
    fun rotationDegrees(team: String): Float? = when (team) {
        "GREEN" -> 0f
        "YELLOW" -> 90f
        "BLUE" -> 180f
        "RED" -> 270f
        else -> null
    }
}
