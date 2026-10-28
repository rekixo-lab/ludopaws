package com.rekixo.ludopaws

import com.rekixo.ludopaws.core.audio.LudoPawsAudioQueuePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoPawsAudioQueuePolicyTest {
    @Test
    fun queuedFootstepsExpireBeforeTheirNextVisualMove() {
        assertTrue(LudoPawsAudioQueuePolicy.isFresh(100L, 320L, priority = 1))
        assertFalse(LudoPawsAudioQueuePolicy.isFresh(100L, 321L, priority = 1))
    }

    @Test
    fun consequentialEventsExpireInsteadOfPlayingAfterLongLoadingStall() {
        assertTrue(LudoPawsAudioQueuePolicy.isFresh(100L, 850L, priority = 9))
        assertFalse(LudoPawsAudioQueuePolicy.isFresh(100L, 851L, priority = 9))
        assertFalse(LudoPawsAudioQueuePolicy.isFresh(100L, 99L, priority = 9))
    }
}
