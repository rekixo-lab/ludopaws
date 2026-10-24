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
            rimDark = 0xFF7D3947.toInt(),
            rim = LudoPawsBoardPalette.coral,
            rimLight = 0xFFF4B1AD.toInt(),
            bandLight = 0xFFEB9296.toInt(),
            bandDark = 0xFFB95365.toInt(),
            recessShadow = 0xFF713A46.toInt(),
            lipDark = 0xFFA35D65.toInt(),
            lipLight = 0xFFD1978D.toInt(),
            sandTop = LudoPawsBoardPalette.yardTop,
            sandBottom = LudoPawsBoardPalette.yardBottom,
            sandSpeck = 0x18737660,
            leafShade = 0xFF477957.toInt(),
            leafLight = 0xFF9AC17D.toInt(),
        )
        "GREEN" -> LudoPawsYardPalette(
            rimDark = 0xFF285C4C.toInt(),
            rim = LudoPawsBoardPalette.jade,
            rimLight = 0xFF9ED8AF.toInt(),
            bandLight = 0xFF6ABD94.toInt(),
            bandDark = 0xFF307C60.toInt(),
            recessShadow = 0xFF285349.toInt(),
            lipDark = 0xFF437A62.toInt(),
            lipLight = 0xFF88BA96.toInt(),
            sandTop = LudoPawsBoardPalette.yardTop,
            sandBottom = LudoPawsBoardPalette.yardBottom,
            sandSpeck = 0x18737660,
            leafShade = 0xFF477957.toInt(),
            leafLight = 0xFF9AC17D.toInt(),
        )
        "YELLOW" -> LudoPawsYardPalette(
            rimDark = 0xFF81652F.toInt(),
            rim = LudoPawsBoardPalette.honey,
            rimLight = 0xFFF5DE97.toInt(),
            bandLight = 0xFFEDCF78.toInt(),
            bandDark = 0xFFB78E37.toInt(),
            recessShadow = 0xFF715C36.toInt(),
            lipDark = 0xFFA18A50.toInt(),
            lipLight = 0xFFD6BE79.toInt(),
            sandTop = LudoPawsBoardPalette.yardTop,
            sandBottom = LudoPawsBoardPalette.yardBottom,
            sandSpeck = 0x18737660,
            leafShade = 0xFF477957.toInt(),
            leafLight = 0xFF9AC17D.toInt(),
        )
        "BLUE" -> LudoPawsYardPalette(
            rimDark = 0xFF2F607D.toInt(),
            rim = LudoPawsBoardPalette.sky,
            rimLight = 0xFFA8D7E7.toInt(),
            bandLight = 0xFF78BEDB.toInt(),
            bandDark = 0xFF337FA7.toInt(),
            recessShadow = 0xFF345D71.toInt(),
            lipDark = 0xFF537F94.toInt(),
            lipLight = 0xFF94C1CD.toInt(),
            sandTop = LudoPawsBoardPalette.yardTop,
            sandBottom = LudoPawsBoardPalette.yardBottom,
            sandSpeck = 0x18737660,
            leafShade = 0xFF477957.toInt(),
            leafLight = 0xFF9AC17D.toInt(),
        )
        else -> null
    }
}

/** Garden-board experiment: visual roles only; character materials keep their own palette. */
internal object LudoPawsBoardPalette {
    val coral = 0xFFDF707A.toInt()
    val jade = 0xFF43A47C.toInt()
    val honey = 0xFFE7BE54.toInt()
    val sky = 0xFF459FCB.toInt()
    val stone = 0xFFDEDACD.toInt()
    val stoneLight = 0xFFECE8DD.toInt()
    val stoneShade = 0xFFD0CDBF.toInt()
    val joint = 0xFF817C6D.toInt()
    val yardTop = 0xFFE5E3D6.toInt()
    val yardBottom = 0xFFC9CCBA.toInt()
    val ink = 0xFF203844.toInt()
    val inkDeep = 0xFF142B36.toInt()
    val text = 0xFFF9F5E9.toInt()
    val textSecondary = 0xFFD5E5DF.toInt()
    val aqua = 0xFF9CDED4.toInt()
    val safe = 0xFF287A83.toInt()
    val mapleLight = 0xFFC6A274.toInt()
    val maple = 0xFFB38E60.toInt()
    val mapleShade = 0xFFA17C50.toInt()
    val wood = 0xFFAD7A4D.toInt()
    val woodShade = 0xFF704D34.toInt()
    val gold = 0xFFE7BD6A.toInt()

    fun teamColor(name: String?): Int? = when (name?.uppercase()) {
        "RED" -> coral
        "GREEN" -> jade
        "YELLOW" -> honey
        "BLUE" -> sky
        else -> null
    }
}
