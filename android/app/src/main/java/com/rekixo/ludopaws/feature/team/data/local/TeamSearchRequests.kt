package com.rekixo.ludopaws.feature.team.data.local

/** Register before dispatch so cancellation cannot overtake a queued create request. */
internal object TeamSearchRequests {
    private val pending = mutableMapOf<String, Int>()
    @Synchronized fun begin(id: String) { pending[id] = (pending[id] ?: 0) + 1 }
    @Synchronized fun finish(id: String) {
        val count = pending[id] ?: return
        if (count <= 1) pending.remove(id) else pending[id] = count - 1
    }
    @Synchronized fun contains(id: String): Boolean = id in pending
}
