package com.rekixo.ludopaws.core.lifecycle

/** Main-thread delivery: a stopped screen may retain results, never navigate. */
internal class ForegroundResults {
    private var started = false
    private var closed = false
    private val pending = ArrayDeque<() -> Unit>()
    fun start() {
        if (closed) return
        started = true
        while (started && pending.isNotEmpty()) pending.removeFirst().invoke()
    }
    fun stop() { started = false }
    fun close() { stop(); closed = true; pending.clear() }
    fun deliver(result: () -> Unit) {
        if (closed) return
        if (started) result() else pending.addLast(result)
    }
}
