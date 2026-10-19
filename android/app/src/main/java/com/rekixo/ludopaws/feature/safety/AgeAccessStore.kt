package com.rekixo.ludopaws.feature.safety

import android.content.Context

/**
 * Age Signals never leave this process: neither DOB, age bounds, age band,
 * installId nor a Google account identifier are written to disk.
 * The only persisted preference is the player's choice to use offline mode.
 */
class AgeAccessStore(context: Context) {
    private val prefs = context.getSharedPreferences("ludopaws_age_access_v1", Context.MODE_PRIVATE)

    fun band(): AgeBand = currentPlayBand ?: AgeBand.UNKNOWN

    fun hasValidScreening(): Boolean =
        currentPlayBand != null || prefs.getBoolean("offline_preferred", false)

    fun canUseConnectedFeatures(): Boolean =
        currentPlayBand == AgeBand.ADULT

    /** Never use Play Age Signals for advertising; no ads until independent compliance. */
    fun canRequestAds(): Boolean = false

    fun saveFromPlay(band: AgeBand) {
        // Reset a previous adult classification before applying a new response.
        currentPlayBand = band
        // Clear older branch data; no Play-derived ages or identifiers remain on disk.
        prefs.edit().remove("band").remove("source").remove("screened_at")
            .remove("offline_preferred").apply()
    }

    fun saveOfflineOnly() {
        currentPlayBand = null
        prefs.edit().remove("band").remove("source").remove("screened_at")
            .putBoolean("offline_preferred", true).apply()
    }

    fun invalidate() {
        currentPlayBand = null
        prefs.edit().clear().apply()
    }

    companion object {
        @Volatile private var currentPlayBand: AgeBand? = null
    }
}
