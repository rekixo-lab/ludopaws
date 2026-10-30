package com.rekixo.ludopaws.feature.store.domain

/** Conservative app-wide frequency policy, independent of rewarded ads. */
internal object AdBreakPolicy {
    const val FIRST_SESSION_GRACE_MS = 180_000L
    const val BETWEEN_ADS_MS = 300_000L
    fun eligible(now: Long, startedAt: Long, completedMatches: Int, lastShownAt: Long?): Boolean =
        now >= startedAt && now - startedAt >= FIRST_SESSION_GRACE_MS && completedMatches >= 2 &&
            (lastShownAt == null || now >= lastShownAt && now - lastShownAt >= BETWEEN_ADS_MS)
}
