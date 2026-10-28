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
    fun ambienceIsIndependentFromBackgroundMusicConfiguration() {
        for (board in BoardRegistry.all) {
            assertTrue(board.backgroundMusicPaths.isEmpty())
            assertTrue(board.ambientSoundPaths.isEmpty())
        }
    }
}
