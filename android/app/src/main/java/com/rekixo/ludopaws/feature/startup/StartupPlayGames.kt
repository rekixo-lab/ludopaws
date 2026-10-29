package com.rekixo.ludopaws.feature.startup

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.feature.safety.FamilyAgeScreen

/** Optional PGS v2 platform authentication; no Google-save, wallet or server-auth request. */
class StartupPlayGames(private val application: Application) {
    val status = PlayGamesStartupStateMachine()
    private val handler = Handler(Looper.getMainLooper())
    var initialized = false
        private set
    private var needsHostCreation = false
    private var recreationQueued = false
    val authenticationBootstrapPending: Boolean get() = needsHostCreation || recreationQueued
    val configured: Boolean get() =
        BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED &&
            BuildConfig.APPLICATION_ID == "com.rekixo.ludopaws" &&
            (BuildConfig.PLAY_GAMES_PROJECT_ID.toLongOrNull() ?: 0L) > 0L
    private val allowed: Boolean get() = FamilyAgeScreen.optionalGoogleAllowed(application)

    private fun initializeIfAllowed() {
        if (initialized || !allowed || !configured) return
        try {
            // Home/popup only, after a durable neutral privacy choice.
            // The provider stays removed; Application and splash never initialize PGS.
            PlayGamesSdk.initialize(application)
            initialized = true
            needsHostCreation = true
        } catch (_: Exception) {
            // Missing Play services/configuration must never prevent offline launch.
        }
    }

    fun onPrivacySettingChanged() {
        if (!allowed) status.refresh(false, configured) { }
    }

    /** Explicit Profile sign-in triggers authentication itself, without a host restart. */
    fun initializeForExplicitSignIn(): Boolean {
        initializeIfAllowed()
        if (initialized && allowed) needsHostCreation = false
        return initialized && allowed && configured
    }

    private fun bootstrapAutomaticAuthentication(activity: Activity) {
        if (!needsHostCreation || recreationQueued) return
        recreationQueued = true
        // Use the process main queue: an unattached/destroyed decor View must
        // not retain this work forever and leave recreationQueued stuck true.
        handler.post {
            recreationQueued = false
            if (!needsHostCreation || !allowed || activity.isFinishing || activity.isDestroyed ||
                activity.isChangingConfigurations || activity.fragmentManager.isStateSaved) return@post
            if (activity is com.rekixo.ludopaws.HomeActivity && !activity.isHomeReadyForPlatformConnection()) return@post
            // SDK 22.1.0 registers its one-shot automatic authenticator for the
            // next onActivityCreated, not the current Activity's onResume.
            // Recreate Home once AFTER SDK initialization; its existing ready
            // state restores Home without another splash or another age popup.
            needsHostCreation = false
            try { activity.recreate() } catch (_: IllegalStateException) { needsHostCreation = true }
        }
    }

    fun refresh(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        status.refresh(allowed, configured) { complete ->
            initializeIfAllowed()
            if (!initialized) {
                complete(false)
            } else {
                // This reads the SDK's automatic attempt, even offline. Do not call
                // signIn() here: manual profile creation belongs to an explicit action.
                val timeout = Runnable { complete(false) }
                handler.postDelayed(timeout, StartupReadiness.ACCOUNT_GRACE_MS)
                try {
                    PlayGames.getGamesSignInClient(activity).isAuthenticated()
                        .addOnCompleteListener { task ->
                            handler.removeCallbacks(timeout)
                            if (!allowed) status.refresh(false, configured) { }
                            else complete(task.isSuccessful && task.result?.isAuthenticated == true)
                        }
                } catch (_: Exception) {
                    handler.removeCallbacks(timeout)
                    complete(false)
                }
            }
        }
        // Retry a deferred lifecycle bootstrap on foreground return even when
        // the status request is still in flight and has been coalesced.
        if (initialized && allowed && activity is com.rekixo.ludopaws.HomeActivity) bootstrapAutomaticAuthentication(activity)
    }
}
