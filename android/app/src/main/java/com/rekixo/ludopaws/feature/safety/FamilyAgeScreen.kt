package com.rekixo.ludopaws.feature.safety

import android.app.Activity
import android.content.Context
import android.widget.Toast
import java.util.WeakHashMap

/** Local screening only: neither proof of age nor advertising/parental consent. */
internal object FamilyAgeScreen {
    private const val STORE = "family_privacy_v1"
    private const val KEY = "age_band"
    private const val POLICY_KEY = "entry_policy_version"
    private const val POLICY_VERSION = 1
    private const val TAG = "ludopaws_age_privacy"
    private val pending = WeakHashMap<Activity, MutableList<() -> Unit>>()
    private var failedSave = false

    fun hasChoice(context: Context): Boolean = !failedSave && runCatching {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        prefs.getInt(POLICY_KEY, 0) == POLICY_VERSION && prefs.getInt(KEY, -1) in 0..4
    }.getOrDefault(false)

    fun optionalGoogleAllowed(context: Context): Boolean = hasChoice(context) && runCatching {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getInt(KEY, -1) == 3
    }.getOrDefault(false)

    fun ensureEntryChoice(activity: Activity, onComplete: () -> Unit) {
        if (hasChoice(activity)) {
            val manager = activity.fragmentManager
            val restored = manager.findFragmentByTag(TAG) as? FamilyPrivacyDialog
            if (!manager.isStateSaved && restored?.arguments?.getBoolean("required", false) == true) restored?.dismiss()
            onComplete()
            return
        }
        open(activity, required = true, onComplete = onComplete)
    }

    fun show(activity: Activity, onComplete: () -> Unit = {}) { open(activity, required = false, onComplete = onComplete) }

    private fun open(activity: Activity, required: Boolean, onComplete: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        val manager = activity.fragmentManager
        if (manager.isDestroyed || manager.isStateSaved) return
        pending[activity]?.let { if (onComplete !in it) it.add(onComplete); return }
        pending[activity] = mutableListOf(onComplete)
        // Android restores the fragment/draft after recreation. Attach a new host
        // callback instead of creating a second dialog over the restored one.
        if (manager.findFragmentByTag(TAG) != null) return
        try {
            FamilyPrivacyDialog().apply {
                arguments = android.os.Bundle().apply { putBoolean("required", required) }
            }.show(manager, TAG)
        } catch (_: IllegalStateException) {
            pending.remove(activity)
        }
    }

    fun saveChoice(context: Context, age: Int): Boolean {
        if (age !in 1..100) return false
        val band = when (age) { in 1..12 -> 0; in 13..15 -> 1; in 16..17 -> 2; else -> 3 }
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val oldBand = runCatching { prefs.getInt(KEY, -1) }.getOrDefault(-1)
        val oldPolicy = runCatching { prefs.getInt(POLICY_KEY, 0) }.getOrDefault(0)
        val saved = runCatching {
            // Persist only the band and policy version, never the selected exact age.
            prefs.edit().putInt(KEY, band).putInt(POLICY_KEY, POLICY_VERSION).commit()
        }.getOrDefault(false)
        failedSave = !saved
        if (!saved) {
            // commit(false) can still mutate the in-memory preference map. Restore
            // it and keep Google access closed until a successful save/restart.
            runCatching {
                val rollback = prefs.edit().remove(KEY).remove(POLICY_KEY)
                if (oldBand in 0..4) rollback.putInt(KEY, oldBand)
                if (oldPolicy > 0) rollback.putInt(POLICY_KEY, oldPolicy)
                rollback.commit()
            }
        }
        return saved
    }

    fun completed(activity: Activity, closeDialog: () -> Unit) {
        val callbacks = pending.remove(activity).orEmpty()
        closeDialog()
        if (activity.isFinishing || activity.isDestroyed || !hasChoice(activity)) return
        // Only a durable neutral choice can start PGS; this is Home/popup work,
        // never Application initialization or a splash readiness dependency.
        val playGames = (activity.application as com.rekixo.ludopaws.LudoProofApplication).startupPlayGames
        if (activity is com.rekixo.ludopaws.HomeActivity) playGames.refresh(activity)
        else playGames.onPrivacySettingChanged()
        callbacks.forEach { it() }
    }

    fun detach(activity: Activity) { pending.remove(activity) }

    fun withOptionalGoogle(activity: Activity, action: () -> Unit) {
        ensureEntryChoice(activity) {
            if (optionalGoogleAllowed(activity)) action()
            else Toast.makeText(activity,
                "Google account features are unavailable for this privacy setting. You can continue playing.",
                Toast.LENGTH_LONG).show()
        }
    }
}
