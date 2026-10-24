package com.rekixo.ludopaws

/**
 * The same approved clean yard layout is shared by every team.
 * Only these colors change; pawn location, chosen animal and home geometry
 * never influence the painted ground or its decorative placement.
 */
internal data class LudoPawsYardPalette(
    val rimDark: Int,
    val rim: Int,
    val rimLight: Int,
    val bandLight: Int,
    val bandDark: Int,
    val recessShadow: Int,
    val lipDark: Int,
    val lipLight: Int,
    val sandTop: Int,
    val sandBottom: Int,
    val sandSpeck: Int,
    val leafShade: Int,
    val leafLight: Int,
)

internal object LudoPawsYardPalettePolicy {
    // Muted sage earth separates pale coats from the ground without competing with team rims.
    private val GROUND_TOP = 0xFF728579.toInt()
    private val GROUND_BOTTOM = 0xFF5E7368.toInt()
    private const val GROUND_SPECK = 0x18324239

    fun forTeam(color: String): LudoPawsYardPalette? = when (color) {
        "RED" -> LudoPawsYardPalette(
            rimDark = 0xFF7A2830.toInt(),
            rim = 0xFFED263E.toInt(),
            rimLight = 0xFFFFA08A.toInt(),
            bandLight = 0xFFFF5359.toInt(),
            bandDark = 0xFFB6152D.toInt(),
            recessShadow = 0xFF742631.toInt(),
            lipDark = 0xFF8E3436.toInt(),
            lipLight = 0xFFD77058.toInt(),
            sandTop = GROUND_TOP,
            sandBottom = GROUND_BOTTOM,
            sandSpeck = GROUND_SPECK,
            leafShade = 0xFF397B3A.toInt(),
            leafLight = 0xFF83CC55.toInt(),
        )
        "GREEN" -> LudoPawsYardPalette(
            rimDark = 0xFF145F2B.toInt(),
            rim = 0xFF12BE43.toInt(),
            rimLight = 0xFF8DE95D.toInt(),
            bandLight = 0xFF45DE45.toInt(),
            bandDark = 0xFF078C35.toInt(),
            recessShadow = 0xFF0A582C.toInt(),
            lipDark = 0xFF116D31.toInt(),
            lipLight = 0xFF27953A.toInt(),
            sandTop = GROUND_TOP,
            sandBottom = GROUND_BOTTOM,
            sandSpeck = GROUND_SPECK,
            leafShade = 0xFF317E36.toInt(),
            leafLight = 0xFF86D451.toInt(),
        )
        "YELLOW" -> LudoPawsYardPalette(
            rimDark = 0xFF886018.toInt(),
            rim = 0xFFEDBF09.toInt(),
            rimLight = 0xFFFFED83.toInt(),
            bandLight = 0xFFFFE232.toInt(),
            bandDark = 0xFFCC970C.toInt(),
            recessShadow = 0xFF8B651B.toInt(),
            lipDark = 0xFFA37B26.toInt(),
            lipLight = 0xFFE4B84B.toInt(),
            sandTop = GROUND_TOP,
            sandBottom = GROUND_BOTTOM,
            sandSpeck = GROUND_SPECK,
            leafShade = 0xFF62893D.toInt(),
            leafLight = 0xFFA8CE62.toInt(),
        )
        "BLUE" -> LudoPawsYardPalette(
            rimDark = 0xFF135271.toInt(),
            rim = 0xFF058EDB.toInt(),
            rimLight = 0xFF8CDBF7.toInt(),
            bandLight = 0xFF16BCF4.toInt(),
            bandDark = 0xFF0067B4.toInt(),
            recessShadow = 0xFF115779.toInt(),
            lipDark = 0xFF176888.toInt(),
            lipLight = 0xFF43A8C8.toInt(),
            sandTop = GROUND_TOP,
            sandBottom = GROUND_BOTTOM,
            sandSpeck = GROUND_SPECK,
            leafShade = 0xFF347E59.toInt(),
            leafLight = 0xFF7DCA80.toInt(),
        )
        else -> null
    }
}
