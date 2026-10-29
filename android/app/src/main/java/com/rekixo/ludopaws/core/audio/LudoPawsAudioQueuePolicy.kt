package com.rekixo.ludopaws.core.audio

/**
 * Queue admission policy, independent of Android audio APIs for local JVM tests.
 * Late footsteps are more distracting than missing ones; consequential events get
 * a little more time to finish loading, but must never play after a long stall.
 */
object LudoPawsAudioQueuePolicy {
    private const val LOW_PRIORITY_LIMIT = 2
    private const val LOW_PRIORITY_MAX_AGE_MS = 220L
    private const val EVENT_MAX_AGE_MS = 750L

    fun isFresh(queuedAtMillis: Long, nowMillis: Long, priority: Int): Boolean {
        val age = nowMillis - queuedAtMillis
        if (age < 0L) return false
        val limit = if (priority <= LOW_PRIORITY_LIMIT) LOW_PRIORITY_MAX_AGE_MS else EVENT_MAX_AGE_MS
        return age <= limit
    }
}
