package com.rekixo.ludopaws

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.android.gms.games.PlayGamesSdk
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
    private var resumedActivities = 0
    private val lifecycleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var appForeground = false
    private val backgroundTask = Runnable {
        if (resumedActivities == 0 && appForeground) {
            appForeground = false
            if (!friendPresencePausedForAccountSwitch) friendPresence.stop()
            GameMusicController.onAppBackground()
        }
    }
    @Volatile
    private var friendPresencePausedForAccountSwitch = false

    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.LUDOPAWS_FAMILY_MODE && BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED) {
            PlayGamesSdk.initialize(this)
        }
        // Prime the process-local cosmetic mirror before any remote create/join
        // request can be sent. Character selection remains presentation-only.
        CharacterSelectionStore(this).load()
        friendPresence =
            FriendPresenceController(this)
        // Home's bounded startup prepares the game account on a worker when online.
        // This does not opt anyone into Google identity or advertising SDKs.
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

        lifecycleHandler.removeCallbacks(backgroundTask)
        resumedActivities += 1
        if (!appForeground) {
            appForeground = true
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
    }

    override fun onActivityPaused(
        activity: Activity,
    ) {
        resumedActivities =
            (resumedActivities - 1)
                .coerceAtLeast(0)
        if (resumedActivities == 0) lifecycleHandler.postDelayed(backgroundTask, 350L)
    }

    fun pauseFriendPresenceForAccountSwitch() {
        friendPresencePausedForAccountSwitch = true
        friendPresence.stop()
    }

    fun resumeFriendPresenceAfterAccountSwitch() {
        friendPresencePausedForAccountSwitch = false
        if (BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED &&
            resumedActivities > 0 && !accountRepository.requiresIdentityRecovery()) {
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
    ) = Unit

    override fun onActivityStopped(
        activity: Activity,
    ) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityDestroyed(
        activity: Activity,
    ) = Unit
}
