package com.rekixo.ludopaws

import java.util.concurrent.Executor

/** At most one running write and one newest pending write per persistence key.
 * Replacing a pending save with clear (or vice versa) preserves latest intent.
 */
internal class LatestWriteQueue<K>(
    private val executor: Executor,
    private val onFailure: (Throwable) -> Unit,
) {
    private val pending = linkedMapOf<K, () -> Unit>()
    private var running = false

    fun submit(key: K, write: () -> Unit) {
        synchronized(pending) {
            pending[key] = write
            if (running) return
            running = true
            try {
                executor.execute(::drain)
            } catch (error: RuntimeException) {
                running = false
                pending.clear()
                throw error
            }
        }
    }

    private fun drain() {
        while (true) {
            val write = synchronized(pending) {
                val entry = pending.entries.firstOrNull()
                if (entry == null) {
                    running = false
                    return
                }
                pending.remove(entry.key)
                entry.value
            }
            try { write() } catch (error: Exception) { onFailure(error) }
        }
    }
}
