package com.rekixo.ludopaws

import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Test

class LatestWriteQueueTest {
    @Test fun burstKeepsNewestPerKeyAndClearOrdering() {
        val tasks = mutableListOf<Runnable>()
        val writes = mutableListOf<String>()
        val queue = LatestWriteQueue<String>(Executor { tasks.add(it) }) { throw AssertionError(it) }
        repeat(1000) { n -> queue.submit("online") { writes.add("save-$n") } }
        queue.submit("offline") { writes.add("offline") }
        queue.submit("online") { writes.add("clear") }
        assertEquals(1, tasks.size)
        tasks.removeAt(0).run()
        assertEquals(listOf("clear", "offline"), writes)
        queue.submit("online") { writes.add("new-match") }
        tasks.removeAt(0).run()
        assertEquals("new-match", writes.last())
    }

    @Test fun updateDuringWriteAndFailedWriteDoNotLoseLatestState() {
        val tasks = mutableListOf<Runnable>()
        val writes = mutableListOf<Int>()
        var failures = 0
        val queue = LatestWriteQueue<String>(Executor { tasks.add(it) }) { failures++ }
        queue.submit("match") {
            queue.submit("match") { writes.add(2) }
            throw IllegalStateException("disk unavailable")
        }
        tasks.removeAt(0).run()
        assertEquals(1, failures)
        assertEquals(listOf(2), writes)
    }
}
