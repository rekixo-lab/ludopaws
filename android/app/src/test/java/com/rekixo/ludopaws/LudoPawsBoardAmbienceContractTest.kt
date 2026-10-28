package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.boards.domain.BoardRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoPawsBoardAmbienceContractTest {
    @Test
    fun existingBoardsKeepSilentAmbienceUnlessExplicitlyAuthored() {
        assertTrue(BoardRegistry.all.all { it.ambientSoundPaths.isEmpty() })
        assertEquals("board_classic", BoardRegistry.resolve("unknown").id)
    }

    @Test
    fun perBoardAmbientAssetPathsAreValidatedWithoutARealFile() {
        val classic = BoardRegistry.resolve("board_classic")
        val theme = classic.copy(
            backgroundMusicPaths = listOf("audio/music/theme.ogg"),
            ambientSoundPaths = listOf("audio/ambience/board_classic/birds_loop.ogg"),
        )
        assertEquals(1, theme.backgroundMusicPaths.size)
        assertEquals(1, theme.ambientSoundPaths.size)
        assertTrue(runCatching {
            classic.copy(ambientSoundPaths = listOf("audio/music/not-ambience.ogg"))
        }.isFailure)
    }

    @Test
    fun ambienceIsIndependentFromBackgroundMusicConfiguration() {
        for (board in BoardRegistry.all) {
            assertTrue(board.backgroundMusicPaths.isEmpty())
            assertTrue(board.ambientSoundPaths.isEmpty())
        }
    }
}
