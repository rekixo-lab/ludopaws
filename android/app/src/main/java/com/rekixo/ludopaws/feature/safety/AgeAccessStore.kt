package com.rekixo.ludopaws.feature.safety

import android.content.Context

/**
 * Minimal data: only an age BAND, source and expiry. Never keep DOB,
 * Google Play's install ID or raw signals. A Play-derived grant is rechecked
 * after every process restart so another device account cannot inherit it.
 */
class AgeAccessStore(context: Context) {
    private val prefs = context.getSharedPreferences("ludopaws_age_access_v1", Context.MODE_PRIVATE)

    fun band(): AgeBand {
        if (!hasValidScreening()) return AgeBand.UNKNOWN
        return runCatching {
            AgeBand.valueOf(prefs.getString("band", "UNKNOWN") ?: "UNKNOWN")
        }.getOrDefault(AgeBand.UNKNOWN)
    }

    fun hasValidScreening(): Boolean {
        val time = prefs.getLong("screened_at", 0L)
        val source = prefs.getString("source", null)
        if (time <= 0L || time > System.currentTimeMillis()) return false
        if (System.currentTimeMillis() - time > VALID_FOR_MS) return false
        return source != "PLAY" || playValidatedThisProcess
    }

    fun canUseConnectedFeatures(): Boolean =
        hasValidScreening() && AgeAccessPolicy.canUseConnectedFeatures(band())

    /**
     * Ads require independently declared adult status, NOT Play age signals:
     * Play Age Signals terms prohibit advertising uses.
     */
    fun canRequestAds(): Boolean =
        hasValidScreening() && prefs.getString("source", null) == "DECLARED" &&
            band() == AgeBand.ADULT

    fun saveFromPlay(band: AgeBand) {
        playValidatedThisProcess = true
        save(band, "PLAY")
    }

    fun saveNeutralDeclaration(band: AgeBand) {
        require(band != AgeBand.UNKNOWN)
        save(band, "DECLARED")
    }

    fun saveOfflineOnly() = save(AgeBand.UNKNOWN, "OFFLINE")

    private fun save(band: AgeBand, source: String) {
        check(prefs.edit().putString("band", band.name)
            .putString("source", source)
            .putLong("screened_at", System.currentTimeMillis())
            .commit()) { "Age preference could not be stored securely" }
    }

    companion object {
        private const val VALID_FOR_MS = 30L * 24 * 60 * 60 * 1000
        @Volatile private var playValidatedThisProcess = false
    }
}
