package com.rekixo.ludopaws.feature.growth

import android.content.Context

/** Aggregate device counters only: no IDs, behavioral profiles, network or analytics SDK. */
internal object GrowthMetrics {
    private val allowed = setOf("match_completed", "review_attempt", "referral_prompt", "reward_claim",
        "share_card_opened", "room_link_opened", "spin_claim", "ad_loaded", "ad_load_failed", "ad_shown", "rewarded_earned", "collection_purchase")
    @Synchronized fun increment(context: Context, event: String) {
        if (event !in allowed) return
        val prefs = context.applicationContext.getSharedPreferences("growth_counts_v1", Context.MODE_PRIVATE)
        prefs.edit().putLong(event, (prefs.getLong(event, 0L) + 1).coerceAtMost(1_000_000_000L)).apply()
    }
    fun snapshot(context: Context): Map<String, Long> {
        val prefs = context.applicationContext.getSharedPreferences("growth_counts_v1", Context.MODE_PRIVATE)
        return allowed.associateWith { prefs.getLong(it, 0L) }
    }
}
