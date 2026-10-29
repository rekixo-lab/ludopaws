package com.rekixo.ludopaws

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.rekixo.ludopaws.core.audio.UiClickSoundInstaller
import com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore
import com.rekixo.ludopaws.feature.account.data.AccountRepository
import com.rekixo.ludopaws.feature.friends.data.FriendPresenceController
import com.rekixo.ludopaws.feature.settings.data.local.GameMusicController

class LudoProofApplication :
    Application(),
    Application.ActivityLifecycleCallbacks {
    private lateinit var friendPresence:
        FriendPresenceController
    val accountRepository: AccountRepository by lazy { AccountRepository(this) }
    val startupPlayGames by lazy { com.rekixo.ludopaws.feature.startup.StartupPlayGames(this) }
    // One bounded Home asset, reused across the privacy/PGS host recreation.
    var startupArtwork: android.graphics.Bitmap? = null
        internal set
    val startupArtworkExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "LudoPawsStartupArtwork").apply { isDaemon = true }
    }
    val startupSession by lazy {
        com.rekixo.ludopaws.feature.startup.StartupSessionCoordinator(
            worker = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
                Thread(task, "LudoPawsStartupAccount").apply { isDaemon = true }
            },
            dispatch = { action -> lifecycleHandler.post { action() } },
            load = {
                try {
                    val session = accountRepository.ensureGuestSession()
                    com.rekixo.ludopaws.feature.startup.StartupAccountSnapshot(
                        when {
                            session.identityRecoveryPending -> com.rekixo.ludopaws.feature.startup.StartupAccountState.RESTORE_REQUIRED
                            session.guestRecoveryEligible -> com.rekixo.ludopaws.feature.startup.StartupAccountState.GUEST_READY
                            else -> com.rekixo.ludopaws.feature.startup.StartupAccountState.SAVED_READY
                        }, session.accountId,
                    )
                } catch (error: com.rekixo.ludopaws.feature.account.data.AccountApiException) {
                    com.rekixo.ludopaws.feature.startup.StartupAccountSnapshot(
                        if (error.code == "ACCOUNT_SESSION_REQUIRED" || error.code == "REFRESH_TOKEN_INVALID")
                            com.rekixo.ludopaws.feature.startup.StartupAccountState.RESTORE_REQUIRED
                        else com.rekixo.ludopaws.feature.startup.StartupAccountState.RETRY_REQUIRED,
                    )
                }
            },
            isCurrent = { result -> accountRepository.currentSession()?.accountId == result.accountId },
        )
    }
    private val visibility = ActivityVisibilityPolicy()
    private val lifecycleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val enterBackground = Runnable {
        if (visibility.backgroundTimeout()) {
            if (!friendPresencePausedForAccountSwitch) friendPresence.stop()
            GameMusicController.onAppBackground()
        }
    }
    @Volatile
    private var friendPresencePausedForAccountSwitch = false

    override fun onCreate() {
        super.onCreate()
        // Prime the process-local cosmetic mirror before any remote create/join
        // request can be sent. Character selection remains presentation-only.
        CharacterSelectionStore(this).load()
        friendPresence =
            FriendPresenceController(this)
        // Online entry creates a guest only when needed. Offline play does not
        // bootstrap an account or initialize a third-party sign-in SDK.
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(
        activity: Activity,
    ) {
        // OEMs may restore system bars after app switches, transient overlays,
        // or window-focus changes. Reassert the game presentation on resume.
        ImmersiveGameWindow.restore(activity)
        // Apply the shared click SFX to this screen's interactive UI controls.
        UiClickSoundInstaller.install(activity)
        PlayerNavigation.resumed(activity)

    }

    private fun enterForeground() {
            if (
                BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED &&
                !friendPresencePausedForAccountSwitch &&
                accountRepository.canStartFriendPresence()
            ) {
                friendPresence.start()
            }
            GameMusicController
                .onAppForeground(this)
    }

    override fun onActivityPaused(
        activity: Activity,
    ) {
        // A paused Activity may simply be opening another screen or a dialog.
    }

    fun pauseFriendPresenceForAccountSwitch() {
        friendPresencePausedForAccountSwitch = true
        friendPresence.stop()
    }

    fun resumeFriendPresenceAfterAccountSwitch() {
        friendPresencePausedForAccountSwitch = false
        if (BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED &&
            visibility.isForeground && accountRepository.canStartFriendPresence()) {
            friendPresence.start()
        }
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        // Keep every current and future Activity on the same edge-to-edge,
        // cutout-aware fullscreen contract without per-screen duplication.
        ImmersiveGameWindow.configure(activity)
    }

    override fun onActivityStarted(
        activity: Activity,
    ) {
        lifecycleHandler.removeCallbacks(enterBackground)
        if (visibility.started()) enterForeground()
    }

    override fun onActivityStopped(
        activity: Activity,
    ) {
        if (visibility.stopped()) {
            // Covers configuration recreation and Activity transitions without
            // repeatedly abandoning audio focus or restarting presence heartbeats.
            lifecycleHandler.postDelayed(enterBackground, 500L)
        }
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(
        activity: Activity,
    ) { PlayerNavigation.destroyed(activity) }
}
