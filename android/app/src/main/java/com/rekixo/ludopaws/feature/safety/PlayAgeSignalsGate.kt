package com.rekixo.ludopaws.feature.safety

import android.app.Activity
import android.app.AlertDialog
import com.google.android.play.agesignals.AgeSignalsAccessRequest
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.model.AgeSignalsStatus

/**
 * Google Play controls sharing. Do not read DOB, installId or Google identity.
 * Age Signals are used ONLY for age-appropriate feature access, never advertising.
 * Concurrent Home and gameplay requests share one result and one platform prompt.
 */
object PlayAgeSignalsGate {
    private var requestInProgress = false
    private val pendingCallbacks = mutableListOf<(Boolean) -> Unit>()

    fun request(activity: Activity, completed: (Boolean) -> Unit, forceRefresh: Boolean = false) {
        if (requestInProgress) {
            pendingCallbacks.add(completed)
            return
        }
        val store = AgeAccessStore(activity)
        if (!forceRefresh && store.hasValidScreening()) {
            completed(store.canUseConnectedFeatures())
            return
        }
        requestInProgress = true
        pendingCallbacks.add(completed)
        store.invalidate()
        checkPlay(activity)
    }

    private fun finishRequest(allowed: Boolean) {
        requestInProgress = false
        val callbacks = pendingCallbacks.toList()
        pendingCallbacks.clear()
        callbacks.forEach { it(allowed) }
    }

    private fun checkPlay(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) {
            finishRequest(false)
            return
        }
        val manager = try {
            AgeSignalsManagerFactory.create(activity.applicationContext)
        } catch (_: Exception) {
            showOfflineFallback(activity)
            return
        }
        manager.requestAgeSignalsAccess(AgeSignalsAccessRequest.builder().setActivity(activity).build())
            .addOnSuccessListener { result ->
                if (result.ageSignalsStatus() != AgeSignalsStatus.SHARED) {
                    showOfflineFallback(activity)
                } else {
                    manager.checkAgeSignals(AgeSignalsRequest.builder().build())
                        .addOnSuccessListener { signals ->
                            val band = AgeAccessPolicy.fromPlayRange(signals.ageLower(), signals.ageUpper())
                            if (band == AgeBand.UNKNOWN) {
                                showOfflineFallback(activity)
                            } else {
                                AgeAccessStore(activity).saveFromPlay(band)
                                finishRequest(AgeAccessStore(activity).canUseConnectedFeatures())
                            }
                        }
                        .addOnFailureListener { showOfflineFallback(activity) }
                }
            }
            .addOnFailureListener { showOfflineFallback(activity) }
    }

    private fun showOfflineFallback(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) {
            finishRequest(false)
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Play safely")
            .setMessage("Age information was not shared or could not be checked. Play offline without entering your birthday. Online, social and store features require age eligibility verification.")
            .setPositiveButton("Play offline") { _, _ ->
                AgeAccessStore(activity).saveOfflineOnly()
                finishRequest(false)
            }
            .setNegativeButton("Try again") { _, _ -> checkPlay(activity) }
            .setCancelable(false)
            .show()
    }

    fun requireConnectedAccess(activity: Activity, onAllowed: () -> Unit) {
        // Always check the CURRENT Play Store user, not a cached prior account.
        request(activity, { allowed ->
            if (allowed && !activity.isFinishing && !activity.isDestroyed) {
                onAllowed()
            } else if (!allowed && !activity.isFinishing && !activity.isDestroyed) {
                AlertDialog.Builder(activity)
                    .setTitle("Available in offline mode")
                    .setMessage("Connected features need adult age sharing from Google Play. Offline Ludo remains available.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }, forceRefresh = true)
    }
}
