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
            rimDark = 0xFF842735.toInt(),
            rim = LudoPawsBoardPalette.coral,
            rimLight = 0xFFFFAD9B.toInt(),
            bandLight = 0xFFF67A80.toInt(),
            bandDark = 0xFFBC344F.toInt(),
            recessShadow = 0xFF762D3C.toInt(),
            lipDark = 0xFFA34849.toInt(),
            lipLight = 0xFFE68B70.toInt(),
            sandTop = LudoPawsBoardPalette.yardTop,
            sandBottom = LudoPawsBoardPalette.yardBottom,
            sandSpeck = 0x1C9E703C,
            leafShade = 0xFF2E8543.toInt(),
            leafLight = 0xFF8FD453.toInt(),
        )
        "GREEN" -> LudoPawsYardPalette(
            rimDark = 0xFF155E36.toInt(),
            rim = LudoPawsBoardPalette.jade,
            rimLight = 0xFF9CE86D.toInt(),
            bandLight = 0xFF52D36D.toInt(),
            bandDark = 0xFF098A4C.toInt(),
            recessShadow = 0xFF145331.toInt(),
            lipDark = 0xFF257C44.toInt(),
            lipLight = 0xFF69B86A.toInt(),
            sandTop = LudoPawsBoardPalette.yardTop,
            sandBottom = LudoPawsBoardPalette.yardBottom,
            sandSpeck = 0x1C9E703C,
            leafShade = 0xFF2E8543.toInt(),
            leafLight = 0xFF8FD453.toInt(),
        )
        "YELLOW" -> LudoPawsYardPalette(
            rimDark = 0xFF866019.toInt(),
            rim = LudoPawsBoardPalette.honey,
            rimLight = 0xFFFFE785.toInt(),
            bandLight = 0xFFFFDA49.toInt(),
            bandDark = 0xFFC59712.toInt(),
            recessShadow = 0xFF7E5D22.toInt(),
            lipDark = 0xFFAB842F.toInt(),
            lipLight = 0xFFE5BD55.toInt(),
            sandTop = LudoPawsBoardPalette.yardTop,
            sandBottom = LudoPawsBoardPalette.yardBottom,
            sandSpeck = 0x1C9E703C,
            leafShade = 0xFF2E8543.toInt(),
            leafLight = 0xFF8FD453.toInt(),
        )
        "BLUE" -> LudoPawsYardPalette(
            rimDark = 0xFF124F79.toInt(),
            rim = LudoPawsBoardPalette.sky,
            rimLight = 0xFF8BD8F7.toInt(),
            bandLight = 0xFF45BFF2.toInt(),
            bandDark = 0xFF096AA7.toInt(),
            recessShadow = 0xFF154E74.toInt(),
            lipDark = 0xFF247799.toInt(),
            lipLight = 0xFF65B9D7.toInt(),
            sandTop = LudoPawsBoardPalette.yardTop,
            sandBottom = LudoPawsBoardPalette.yardBottom,
            sandSpeck = 0x1C9E703C,
            leafShade = 0xFF2E8543.toInt(),
            leafLight = 0xFF8FD453.toInt(),
        )
        else -> null
    }
}

/** Garden-board experiment: visual roles only; character materials keep their own palette. */
internal object LudoPawsBoardPalette {
    val coral = 0xFFE85468.toInt()
    val jade = 0xFF18B568.toInt()
    val honey = 0xFFF3C62D.toInt()
    val sky = 0xFF168FDB.toInt()
    val stone = 0xFFD9D5C9.toInt()
    val stoneLight = 0xFFF1EADB.toInt()
    val stoneShade = 0xFFC5BFB0.toInt()
    val joint = 0xFF756956.toInt()
    val yardTop = 0xFFFFE8BD.toInt()
    val yardBottom = 0xFFEBC994.toInt()
    val ink = 0xFF15384C.toInt()
    val inkDeep = 0xFF102536.toInt()
    val text = 0xFFF9F5E9.toInt()
    val textSecondary = 0xFFDEEDF2.toInt()
    val aqua = 0xFF72E2E1.toInt()
    val safe = 0xFF167B98.toInt()
    val mapleLight = 0xFFD7A458.toInt()
    val maple = 0xFFC18A43.toInt()
    val mapleShade = 0xFFA96F32.toInt()
    val wood = 0xFFB37534.toInt()
    val woodShade = 0xFF744425.toInt()
    val gold = 0xFFF5C34C.toInt()

    fun teamColor(name: String?): Int? = when (name?.uppercase()) {
        "RED" -> coral
        "GREEN" -> jade
        "YELLOW" -> honey
        "BLUE" -> sky
        else -> null
    }
}
