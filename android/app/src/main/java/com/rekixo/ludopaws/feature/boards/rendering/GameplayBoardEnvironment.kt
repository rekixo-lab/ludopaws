package com.rekixo.ludopaws.feature.boards.rendering

import com.rekixo.ludopaws.feature.boards.domain.BoardRegistry

internal data class GameplayBoardEnvironment(
    val boardId: String,
    val groundTop: Int,
    val groundBottom: Int,
    val accent: Int,
    val detail: Int,
    val panelTop: Int,
    val panelBottom: Int,
    val title: Int = 0xFFFFF2D3.toInt(),
    val price: Int = 0xFFFFE9AA.toInt(),
) {
    companion object {
        private val environments = listOf(
            GameplayBoardEnvironment("board_classic", 0xFF506B3D.toInt(), 0xFF314F35.toInt(),
                0xFFBFE3A1.toInt(), 0xFF698349.toInt(), 0xFF2B4D36.toInt(), 0xFF1C382D.toInt()),
            GameplayBoardEnvironment("board_autumn_forest", 0xFFB58253.toInt(), 0xFF72513B.toInt(),
                0xFFFFD498.toInt(), 0xFFBD6B3E.toInt(), 0xFF65412F.toInt(), 0xFF3E2A24.toInt()),
            GameplayBoardEnvironment("board_blossom_garden", 0xFFD0DDC2.toInt(), 0xFF91B58C.toInt(),
                0xFFFFD5E2.toInt(), 0xFFE5A4B4.toInt(), 0xFF47614F.toInt(), 0xFF2C473D.toInt(),
                title = 0xFFFFF3F2.toInt(), price = 0xFFFFE3B3.toInt()),
            GameplayBoardEnvironment("board_winter_haven", 0xFFC9DCE1.toInt(), 0xFF8AAEB8.toInt(),
                0xFFD6F4FF.toInt(), 0xFF567B80.toInt(), 0xFF335865.toInt(), 0xFF203C4C.toInt(),
                title = 0xFFF4FDFF.toInt(), price = 0xFFFFEAC7.toInt()),
            GameplayBoardEnvironment("board_monsoon_mire", 0xFFA3B09B.toInt(), 0xFF738D83.toInt(),
                0xFFCBE9E0.toInt(), 0xFF806249.toInt(), 0xFF354E47.toInt(), 0xFF203831.toInt()),
            GameplayBoardEnvironment("board_cloud_lagoon", 0xFFE2F0EA.toInt(), 0xFF8DC7C0.toInt(),
                0xFFD5FFF2.toInt(), 0xFF73BFB8.toInt(), 0xFF346B6D.toInt(), 0xFF204B55.toInt(),
                title = 0xFFF4FFF9.toInt()),
            GameplayBoardEnvironment("board_astral", 0xFF12263B.toInt(), 0xFF081525.toInt(),
                0xFFE3C787.toInt(), 0xFF718AAA.toInt(), 0xFF293D59.toInt(), 0xFF152338.toInt(),
                title = 0xFFFFF4DD.toInt()),
        ).associateBy { it.boardId }

        fun resolve(boardId: String): GameplayBoardEnvironment {
            val definition = BoardRegistry.resolve(boardId)
            return environments[definition.id] ?: GameplayBoardEnvironment(definition.id,
                definition.surfaceColor, definition.offer.previewPrimary, definition.offer.previewSecondary,
                definition.offer.previewSecondary, 0xFF304549.toInt(), 0xFF1D3036.toInt())
        }
    }
}
