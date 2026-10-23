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
    fun forTeam(color: String): LudoPawsYardPalette? = when (color) {
        "RED" -> LudoPawsYardPalette(
            rimDark = 0xFF7A2830.toInt(),
            rim = 0xFFD74D4F.toInt(),
            rimLight = 0xFFFFA08A.toInt(),
            bandLight = 0xFFF88474.toInt(),
            bandDark = 0xFFBD3E47.toInt(),
            recessShadow = 0xFF742631.toInt(),
            lipDark = 0xFF8E3436.toInt(),
            lipLight = 0xFFD77058.toInt(),
            sandTop = 0xFFFFE8BF.toInt(),
            sandBottom = 0xFFFFD09C.toInt(),
            sandSpeck = 0x21B97B49,
            leafShade = 0xFF397B3A.toInt(),
            leafLight = 0xFF83CC55.toInt(),
        )
        "GREEN" -> LudoPawsYardPalette(
            rimDark = 0xFF145F2B.toInt(),
            rim = 0xFF37B941.toInt(),
            rimLight = 0xFF8DE95D.toInt(),
            bandLight = 0xFF78D752.toInt(),
            bandDark = 0xFF2FAA45.toInt(),
            recessShadow = 0xFF0A582C.toInt(),
            lipDark = 0xFF116D31.toInt(),
            lipLight = 0xFF27953A.toInt(),
            sandTop = 0xFFFFE8B8.toInt(),
            sandBottom = 0xFFFFD39C.toInt(),
            sandSpeck = 0x21B88344,
            leafShade = 0xFF317E36.toInt(),
            leafLight = 0xFF86D451.toInt(),
        )
        "YELLOW" -> LudoPawsYardPalette(
            rimDark = 0xFF886018.toInt(),
            rim = 0xFFE7B932.toInt(),
            rimLight = 0xFFFFED83.toInt(),
            bandLight = 0xFFF9D965.toInt(),
            bandDark = 0xFFD7A634.toInt(),
            recessShadow = 0xFF8B651B.toInt(),
            lipDark = 0xFFA37B26.toInt(),
            lipLight = 0xFFE4B84B.toInt(),
            sandTop = 0xFFFFEAC6.toInt(),
            sandBottom = 0xFFF4D09C.toInt(),
            sandSpeck = 0x1DAB8038,
            leafShade = 0xFF62893D.toInt(),
            leafLight = 0xFFA8CE62.toInt(),
        )
        "BLUE" -> LudoPawsYardPalette(
            rimDark = 0xFF135271.toInt(),
            rim = 0xFF268EBF.toInt(),
            rimLight = 0xFF8CDBF7.toInt(),
            bandLight = 0xFF68C5E7.toInt(),
            bandDark = 0xFF2B91C3.toInt(),
            recessShadow = 0xFF115779.toInt(),
            lipDark = 0xFF176888.toInt(),
            lipLight = 0xFF43A8C8.toInt(),
            sandTop = 0xFFFFEAD0.toInt(),
            sandBottom = 0xFFEED5AF.toInt(),
            sandSpeck = 0x1DA98B65,
            leafShade = 0xFF347E59.toInt(),
            leafLight = 0xFF7DCA80.toInt(),
        )
        else -> null
    }
}
