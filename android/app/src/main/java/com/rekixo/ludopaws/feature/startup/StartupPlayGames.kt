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
    private var initialized = false
    val configured: Boolean get() =
        BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED &&
            BuildConfig.APPLICATION_ID == "com.rekixo.ludopaws" &&
            (BuildConfig.PLAY_GAMES_PROJECT_ID.toLongOrNull() ?: 0L) > 0L
    private val allowed: Boolean get() = FamilyAgeScreen.optionalGoogleAllowed(application)

    fun initializeIfAllowed() {
        if (initialized || !allowed || !configured) return
        try {
            // Called from Application.onCreate for returning eligible players,
            // or after neutral age selection for a new player. The provider stays removed.
            PlayGamesSdk.initialize(application)
            initialized = true
        } catch (_: Exception) {
            // Missing Play services/configuration must never prevent offline launch.
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
    }
}
