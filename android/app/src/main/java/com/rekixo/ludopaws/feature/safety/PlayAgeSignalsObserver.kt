package com.rekixo.ludopaws.feature.safety

import android.app.Activity
import com.google.android.play.agesignals.AgeSignalsAccessRequest
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.model.AgeSignalsStatus

/**
 * Play-managed age-range sharing, without any application DOB screen.
 *
 * The checked range is held only in process memory, never persisted, logged,
 * sent to an API, used for advertising, or treated as server authorization.
 * Google may display its OWN consent sheet. Declines, absent Play services,
 * errors and unknown age ranges all keep gameplay safely offline.
 *
 * Deliberately only one access request per process to avoid repeated pressure.
 */
object PlayAgeSignalsObserver {
    @Volatile private var started = false
    @Volatile private var observedBand: AgeBand = AgeBand.UNKNOWN

    private @Synchronized fun markStarted(): Boolean {
        if (started) return false
        started = true
        observedBand = AgeBand.UNKNOWN
        return true
    }

    fun refreshOnce(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || !markStarted()) return
        val manager = try {
            AgeSignalsManagerFactory.create(activity.applicationContext)
        } catch (_: Exception) {
            return
        }
        try {
            // Google's access step, NOT a DOB form. It may show Google's sharing prompt.
            manager.requestAgeSignalsAccess(
                AgeSignalsAccessRequest.builder().setActivity(activity).build(),
            ).addOnSuccessListener { status ->
                if (status.ageSignalsStatus() == AgeSignalsStatus.SHARED) {
                    manager.checkAgeSignals(AgeSignalsRequest.builder().build())
                        .addOnSuccessListener { signals ->
                            // Ignore installId, source identifiers and approval dates.
                            observedBand = AgeAccessPolicy.fromPlayRange(
                                signals.ageLower(), signals.ageUpper(),
                            )
                        }
                        .addOnFailureListener { observedBand = AgeBand.UNKNOWN }
                } else {
                    // NOT_SHARED or VERIFICATION_REQUIRED: never infer adulthood.
                    observedBand = AgeBand.UNKNOWN
                }
            }.addOnFailureListener { observedBand = AgeBand.UNKNOWN }
        } catch (_: Exception) {
            observedBand = AgeBand.UNKNOWN
        }
    }

    /**
     * Local-only UX explanation; cannot grant multiplayer or purchases.
     * The Android build flag AND the server lockdown remain authoritative.
     */
    fun offlineExplanation(): String = offlineExplanationFor(observedBand)

    internal fun offlineExplanationFor(band: AgeBand): String = when (band) {
        AgeBand.CHILD, AgeBand.TEEN ->
            "Online features aren't available in this release. You can play Ludo offline without entering your birthday."
        AgeBand.ADULT ->
            "Online play remains temporarily disabled until server safety checks are approved. You can play offline without entering your birthday."
        AgeBand.UNKNOWN ->
            "Online play is temporarily unavailable. No birthday is required; you can play offline."
    }
}
