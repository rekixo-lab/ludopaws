package com.rekixo.ludopaws.feature.safety

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import java.util.WeakHashMap

/** Local neutral screening for optional SDKs, never proof of adulthood or consent. */
internal object FamilyAgeScreen {
    private const val STORE = "family_privacy_v1"
    private const val KEY = "age_band"
    private val pending = WeakHashMap<Activity, MutableList<() -> Unit>>()
    private val bands = arrayOf("Under 13", "13–15", "16–17", "18 or older", "Prefer not to say")

    fun hasChoice(context: Context): Boolean =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).contains(KEY)

    fun optionalGoogleAllowed(context: Context): Boolean =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getInt(KEY, -1) == 3

    fun ensureChoice(activity: Activity, onComplete: () -> Unit) {
        if (hasChoice(activity)) { onComplete(); return }
        show(activity, onComplete)
    }

    fun show(activity: Activity, onComplete: () -> Unit = {}) {
        if (activity.isFinishing || activity.isDestroyed) return
        pending[activity]?.let { it.add(onComplete); return }
        pending[activity] = mutableListOf(onComplete)
        AlertDialog.Builder(activity)
            .setTitle("Age and privacy")
            .setView(android.widget.TextView(activity).apply {
                text = "Choose your age group. This stays on this device. All players receive the same family-safe gameplay and child-directed ad treatment. Change this in Profile → Age and privacy."
                setPadding(24, 16, 24, 16)
            })
            .setItems(bands) { _, which ->
                activity.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putInt(KEY, which).apply()
                val callbacks = pending.remove(activity).orEmpty()
                if (!activity.isFinishing && !activity.isDestroyed) callbacks.forEach { it() }
            }
            .setNegativeButton("Not now") { _, _ -> pending.remove(activity) }
            .setOnCancelListener { pending.remove(activity) }
            .setOnDismissListener { pending.remove(activity) }
            .show()
    }

    fun withOptionalGoogle(activity: Activity, action: () -> Unit) {
        ensureChoice(activity) {
            if (optionalGoogleAllowed(activity)) action()
            else AlertDialog.Builder(activity)
                .setTitle("Continue with your game account")
                .setMessage("Optional Google identity services are unavailable for this privacy setting. Offline and online gameplay, Friends, store items and fixed quick-chat remain available. For help with an existing account, contact AI@rekixo.com.")
                .setPositiveButton("OK", null).show()
        }
    }
}
