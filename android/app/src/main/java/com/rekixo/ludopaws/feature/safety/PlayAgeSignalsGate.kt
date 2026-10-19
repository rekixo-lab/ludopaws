package com.rekixo.ludopaws.feature.safety

import android.app.Activity
import android.app.AlertDialog
import com.google.android.play.agesignals.AgeSignalsAccessRequest
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.model.AgeSignalsStatus

/**
 * Google's age-sharing choice is optional; no DOB, Google ID or install ID is read.
 * Never use this result for ads or analytics. Non-adult/unknown users retain offline play.
 */
object PlayAgeSignalsGate {
    fun request(activity: Activity, completed: (Boolean) -> Unit) {
        val store = AgeAccessStore(activity)
        if (store.hasValidScreening()) {
            completed(store.canUseConnectedFeatures())
            return
        }
        val manager = try { AgeSignalsManagerFactory.create(activity.applicationContext) }
            catch (_: Exception) { showOfflineFallback(activity, completed); return }
        manager.requestAgeSignalsAccess(AgeSignalsAccessRequest.builder().setActivity(activity).build())
            .addOnSuccessListener { result ->
                if (result.ageSignalsStatus() != AgeSignalsStatus.SHARED) {
                    showOfflineFallback(activity, completed)
                } else {
                    manager.checkAgeSignals(AgeSignalsRequest.builder().build())
                        .addOnSuccessListener { signals ->
                            val band = AgeAccessPolicy.fromPlayRange(signals.ageLower(), signals.ageUpper())
                            if (band == AgeBand.UNKNOWN) {
                                showOfflineFallback(activity, completed)
                            } else {
                                store.saveFromPlay(band)
                                completed(store.canUseConnectedFeatures())
                            }
                        }
                        .addOnFailureListener { showOfflineFallback(activity, completed) }
                }
            }
            .addOnFailureListener { showOfflineFallback(activity, completed) }
    }

    private fun showOfflineFallback(activity: Activity, completed: (Boolean) -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) { completed(false); return }
        AlertDialog.Builder(activity)
            .setTitle("Play safely")
            .setMessage("Age information was not shared or could not be checked. You can play offline without entering your birthday. Online, social and store features require age eligibility verification.")
            .setPositiveButton("Play offline") { _, _ ->
                AgeAccessStore(activity).saveOfflineOnly()
                completed(false)
            }
            .setNegativeButton("Try again") { _, _ -> request(activity, completed) }
            .setCancelable(false)
            .show()
    }

    fun requireConnectedAccess(activity: Activity, onAllowed: () -> Unit) {
        if (AgeAccessStore(activity).canUseConnectedFeatures()) {
            onAllowed()
            return
        }
        request(activity) { allowed ->
            if (allowed) onAllowed() else if (!activity.isFinishing && !activity.isDestroyed) {
                AlertDialog.Builder(activity)
                    .setTitle("Available in offline mode")
                    .setMessage("Connected features are restricted until adult eligibility is confirmed through Google Play age sharing. Offline Ludo remains available.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
