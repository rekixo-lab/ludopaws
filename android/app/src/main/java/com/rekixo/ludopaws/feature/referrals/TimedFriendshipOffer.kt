package com.rekixo.ludopaws.feature.referrals

import android.os.SystemClock
import org.json.JSONObject
import java.util.Locale

internal class TimedFriendshipOffer private constructor(
    val state: String,
    private val expiresElapsed: Long,
) {
    val remainingMs: Long get() = (expiresElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    val canInvite: Boolean get() = state in setOf("INVITE", "FRIEND_JOINED") && remainingMs > 0
    val countdown: String get() {
        val seconds = (remainingMs + 999) / 1000
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }

    companion object {
        const val CAMPAIGN = "friendship-bundle-v2"
        const val REWARDS = "Giraffe · Sunstone Palace · Prism Royal · 150 Gems"
        const val RULE = "A new friend must install from your link, verify their invitation and finish a real Online or Friends match with you before the timer ends. No win needed."

        fun from(offer: JSONObject?): TimedFriendshipOffer? {
            if (offer == null || offer.optString("campaign") != CAMPAIGN) return null
            val remaining = (offer.optLong("endsAt") - offer.optLong("serverNow")).coerceIn(0, 48 * 60 * 60 * 1000L)
            val expiresElapsed = if (offer.has("localExpiresElapsed")) offer.optLong("localExpiresElapsed")
                else (SystemClock.elapsedRealtime() + remaining).also { offer.put("localExpiresElapsed", it) }
            return TimedFriendshipOffer(offer.optString("state"), expiresElapsed)
        }
    }
}
