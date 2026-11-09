package com.rekixo.ludopaws.feature.updates

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.feature.growth.AutomaticPromptGate

/** Google Play is authoritative about update availability. No background polling. */
internal class PlayUpdateCoordinator(private val activity: Activity) {
    companion object {
        const val REQUEST_CODE = 18421
        private const val TAG = "LudoPawsUpdates"
    }

    private val manager by lazy { AppUpdateManagerFactory.create(activity) }
    private val prefs by lazy {
        activity.getSharedPreferences("ludopaws_update_experience_v1", Activity.MODE_PRIVATE)
    }
    private var listening = false
    private var checking = false
    private var gateFinished = false
    private var lastHomeCheckElapsed = 0L
    private var destroyed = false
    private var updateFlowActive = false
    private var activeDialog: AlertDialog? = null
    private var installDialog: AlertDialog? = null
    private var onCheckFinished: (() -> Unit)? = null
    private val listener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            activity.runOnUiThread { showInstallReady() }
        }
    }

    /** Call only after Home and privacy are ready, before referral/review prompts. */
    fun beforePromotions(onComplete: () -> Unit): Boolean {
        // An Activity may stay alive across several Play Store releases.
        // Re-check after six hours whenever Home returns to the foreground.
        if (gateFinished) {
            if (SystemClock.elapsedRealtime() - lastHomeCheckElapsed < 6L * 60 * 60 * 1000)
                return true
            gateFinished = false
        }
        if (checking || destroyed) return false
        checking = true
        onCheckFinished = onComplete
        registerListener()
        if (!showWhatsNewIfUpgraded()) checkPlay()
        return false
    }

    fun onResume() {
        if (destroyed) return
        registerListener()
        runCatching { manager.appUpdateInfo }.getOrNull()?.addOnSuccessListener { info ->
            if (destroyed || activity.isFinishing || activity.isDestroyed) return@addOnSuccessListener
            when {
                info.installStatus() == InstallStatus.DOWNLOADED -> showInstallReady()
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) && !updateFlowActive ->
                    startUpdate(info, AppUpdateType.IMMEDIATE)
            }
        }
    }

    fun onUpdateResult(resultCode: Int) {
        updateFlowActive = false
        if (resultCode != Activity.RESULT_OK && resultCode != Activity.RESULT_CANCELED)
            Log.w(TAG, "Play update result " + resultCode)
    }

    fun onDestroy() {
        destroyed = true
        onCheckFinished = null
        if (listening) runCatching { manager.unregisterListener(listener) }
        activeDialog?.setOnDismissListener(null)
        activeDialog?.dismiss()
        installDialog?.dismiss()
        AutomaticPromptGate.release("play_update")
        AutomaticPromptGate.release("whats_new")
        activeDialog = null
        installDialog = null
    }

    private fun registerListener() {
        if (!listening && !destroyed)
            listening = runCatching { manager.registerListener(listener); true }.getOrDefault(false)
    }

    private fun showWhatsNewIfUpgraded(): Boolean {
        val version = BuildConfig.VERSION_CODE.toLong()
        val prior = prefs.getLong("seen_version", -1L)
        if (prior == version) return false
        val updatedInstall = runCatching {
            val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
            info.lastUpdateTime > info.firstInstallTime + 30_000L
        }.getOrDefault(false)
        if (!UpdateExperiencePolicy.shouldShowWhatsNew(prior, version, updatedInstall)) {
            prefs.edit().putLong("seen_version", version).apply()
            return false
        }
        if (!AutomaticPromptGate.reserve(activity, "whats_new")) return false
        val dialog = AlertDialog.Builder(activity)
            .setTitle("What's New in Ludo Paws")
            .setMessage("Ludo Paws was updated to version " + BuildConfig.VERSION_NAME +
                ". See the exact changes for this release on Google Play.")
            .setPositiveButton("Let's Play", null)
            .setNeutralButton("Release Notes") { _, _ -> openPlayStore() }
            .create()
        dialog.setOnDismissListener {
            activeDialog = null
            AutomaticPromptGate.release("whats_new")
            if (!destroyed) checkPlay()
        }
        return try {
            activeDialog = dialog
            dialog.show()
            prefs.edit().putLong("seen_version", version).apply()
            AutomaticPromptGate.presented(activity, "whats_new")
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot show release notes", e)
            activeDialog = null
            AutomaticPromptGate.release("whats_new")
            false
        }
    }

    private fun checkPlay() {
        if (destroyed) return
        val task = runCatching { manager.appUpdateInfo }.getOrNull()
        if (task == null) { finish(); return }
        task.addOnCompleteListener { result ->
            if (destroyed || activity.isFinishing || activity.isDestroyed) return@addOnCompleteListener
            if (!result.isSuccessful) { finish(); return@addOnCompleteListener }
            val info = result.result
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                showInstallReady()
                finish()
                return@addOnCompleteListener
            }
            if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) {
                finish()
                return@addOnCompleteListener
            }
            val urgent = info.updatePriority() >= 5 &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            val updateType = when {
                urgent -> AppUpdateType.IMMEDIATE
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
                else -> { finish(); return@addOnCompleteListener }
            }
            val now = System.currentTimeMillis()
            if (!UpdateExperiencePolicy.shouldPromptForUpdate(
                    info.availableVersionCode(), prefs.getInt("prompt_version", -1),
                    prefs.getLong("prompt_at", 0L), now, urgent
                ) || !AutomaticPromptGate.reserve(activity, "play_update")) {
                finish()
                return@addOnCompleteListener
            }
            val dialog = AlertDialog.Builder(activity)
                .setTitle("Ludo Paws Update Available")
                .setMessage(if (urgent) "An important update is available on Google Play."
                    else "Download the latest version while you keep playing.")
                .setPositiveButton("Update Now") { _, _ -> startUpdate(info, updateType) }
                .setNegativeButton("Later", null)
                .create()
            dialog.setOnDismissListener {
                activeDialog = null
                AutomaticPromptGate.release("play_update")
                finish()
            }
            try {
                activeDialog = dialog
                dialog.show()
                prefs.edit().putInt("prompt_version", info.availableVersionCode())
                    .putLong("prompt_at", now).apply()
                AutomaticPromptGate.presented(activity, "play_update")
            } catch (e: RuntimeException) {
                Log.w(TAG, "Cannot show update prompt", e)
                activeDialog = null
                AutomaticPromptGate.release("play_update")
                finish()
            }
        }
    }

    private fun startUpdate(info: AppUpdateInfo, type: Int) {
        if (destroyed || updateFlowActive || activity.isFinishing || activity.isDestroyed) return
        updateFlowActive = true
        val launched = runCatching {
            manager.startUpdateFlowForResult(
                info, activity, AppUpdateOptions.newBuilder(type).build(), REQUEST_CODE
            )
        }.onFailure { Log.w(TAG, "Update launch failed", it) }.getOrDefault(false)
        if (!launched) {
            updateFlowActive = false
            Toast.makeText(activity, "Please check Google Play for the update.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showInstallReady() {
        if (destroyed || activity.isFinishing || activity.isDestroyed ||
            !activity.hasWindowFocus() || activeDialog?.isShowing == true ||
            installDialog?.isShowing == true) return
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Update Ready to Install")
            .setMessage("The update is downloaded. Restart Ludo Paws to install it.")
            .setPositiveButton("Restart & Install") { _, _ ->
                runCatching { manager.completeUpdate() }
                    .onFailure { Log.w(TAG, "Update completion failed", it) }
            }
            .setNegativeButton("Later", null)
            .create()
        dialog.setOnDismissListener { if (installDialog === dialog) installDialog = null }
        installDialog = dialog
        runCatching { dialog.show() }.onFailure { installDialog = null }
    }

    private fun openPlayStore() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + activity.packageName))
            .setPackage("com.android.vending")
        runCatching { activity.startActivity(market) }.onFailure {
            runCatching {
                activity.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + activity.packageName)))
            }
        }
    }

    private fun finish() {
        if (gateFinished || destroyed) return
        checking = false
        gateFinished = true
        lastHomeCheckElapsed = SystemClock.elapsedRealtime()
        onCheckFinished?.let { callback ->
            onCheckFinished = null
            callback()
        }
    }
}
