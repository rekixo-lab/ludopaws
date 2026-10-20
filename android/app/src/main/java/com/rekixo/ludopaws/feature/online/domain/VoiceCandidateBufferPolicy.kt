package com.rekixo.ludopaws.feature.online

/** Bound untrusted ICE candidate buffering before the remote SDP arrives. */
internal object VoiceCandidateBufferPolicy {
    const val MAX_PENDING_PER_PEER = 64

    /** Caller synchronizes the queue; oldest candidates are discarded first. */
    fun <T> enqueue(queue: MutableList<T>, candidate: T) {
        if (queue.size >= MAX_PENDING_PER_PEER) queue.removeAt(0)
        queue.add(candidate)
    }
}
