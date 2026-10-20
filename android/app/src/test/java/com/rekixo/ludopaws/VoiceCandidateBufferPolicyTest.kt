package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.online.VoiceCandidateBufferPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCandidateBufferPolicyTest {
    @Test fun excessiveUntrustedCandidatesStayMemoryBounded() {
        val pending = mutableListOf<Int>()
        repeat(1_000) { VoiceCandidateBufferPolicy.enqueue(pending, it) }
        assertEquals(VoiceCandidateBufferPolicy.MAX_PENDING_PER_PEER, pending.size)
        assertEquals(936, pending.first())
        assertEquals(999, pending.last())
    }
    @Test fun ordinaryCandidatesKeepTheirOrder() {
        val pending = mutableListOf("first", "second")
        VoiceCandidateBufferPolicy.enqueue(pending, "third")
        assertEquals(listOf("first", "second", "third"), pending)
    }
}
