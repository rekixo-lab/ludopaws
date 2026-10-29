package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.boards.domain.BoardRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoPawsBoardAudioOverridesTest {
    @Test
    fun existingBoardMusicDefaultsRemainUnchanged() {
        assertTrue(BoardRegistry.all.all { it.backgroundMusicPaths.isEmpty() })
    }

    @Test
    fun selectedBoardThemeFallbackNeverChangesItsStoreIdentity() {
        for (definition in BoardRegistry.all) {
            assertEquals(definition.id, BoardRegistry.resolve(definition.id).offer.id)
        }
    }
}
