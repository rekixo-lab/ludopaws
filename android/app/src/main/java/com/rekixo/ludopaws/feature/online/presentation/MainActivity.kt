package com.rekixo.ludopaws

import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import org.json.JSONObject
import java.util.concurrent.Executors
import com.rekixo.ludopaws.ui.game.addGameTopActions
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.ui.online.*
import com.rekixo.ludopaws.feature.online.*
import com.rekixo.ludopaws.feature.settings.data.local.GameSoundFeedback
import com.rekixo.ludopaws.feature.store.data.ads.PostWinInterstitialAdController
import com.rekixo.ludopaws.feature.store.data.ads.PostWinInterstitialExitGate
import com.rekixo.ludopaws.feature.store.data.local.GemWalletStore
import com.rekixo.ludopaws.feature.store.domain.PostWinInterstitialPolicy

class MainActivity : ComponentActivity() {
    internal var gameplayPawsPromotion:
        com.rekixo.ludopaws.feature.store.presentation.components.GamePawsPromotionSlot? = null
    internal val gameMode:
        GameMode by lazy {
        GameMode
            .fromWireValue(
                intent.getStringExtra(
                    GameModeIntent
                        .EXTRA_GAME_MODE,
                ),
            )
            ?.takeIf {
                it.isRemote
            }
            ?: secureSessionStore
                .load()
                ?.modeWire
                ?.let {
                    GameMode
                        .fromWireValue(
                            it,
                        )
                }
                ?.takeIf {
                    it.isRemote
                }
            ?: GameMode.ONLINE
    }

    internal val shouldRestoreSavedSession:
        Boolean by lazy {
        gameMode !=
            GameMode.ONLINE ||
            intent.getBooleanExtra(
                GameModeIntent
                    .EXTRA_RESUME_SAVED_MATCH,
                false,
            )
    }

    internal val api = GameApi(commandChannel = { realtimeClient })
    // Keep room snapshots and background synchronization from queueing ahead of
    // a user's roll/move. Game mutations still serialize on this dedicated lane.
    internal val executor = Executors.newSingleThreadExecutor()
    internal val gameplayExecutor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ludo-paws-gameplay-network").apply { isDaemon = true }
        }
    internal val mainHandler =
        Handler(Looper.getMainLooper())
    internal var lastGemWalletSyncMatchId: String? = null

    internal fun refreshMatchGemWalletAfterFinish(completedMatchId: String) {
        if (lastGemWalletSyncMatchId == completedMatchId) return
        lastGemWalletSyncMatchId = completedMatchId
        // Match settlement may be asynchronous; never claim payment until the
        // server wallet snapshot confirms it. Opening the store also refreshes.
        mainHandler.postDelayed({
            executor.execute {
                runCatching {
                    val repository = (application as LudoProofApplication).accountRepository
                    val session = repository.currentSession() ?: return@runCatching
                    val snapshot = repository.wallet()
                    GemWalletStore(this).applyServerSnapshot(snapshot, session.accountId)
                }
            }
        }, 3_000L)
    }


    internal lateinit var arcadeRootView: FrameLayout
    internal lateinit var lobbyPanel: LinearLayout
    internal var onlineHeroPanel: View? = null
    internal lateinit var matchStatusPanel: LinearLayout
    internal lateinit var resultPanel: FrameLayout
    internal lateinit var resultTitleText: TextView
    internal lateinit var resultSubtitleText: TextView
    internal lateinit var boardFrame: FrameLayout
    internal lateinit var actionPanel: LinearLayout
    internal lateinit var nameInput: EditText
    internal lateinit var matchInput: EditText
    internal lateinit var matchInfoText: TextView
    internal lateinit var turnText: TextView
    internal lateinit var playersText: TextView
    internal lateinit var verificationText: TextView
    internal lateinit var verificationPanel: LinearLayout
    internal lateinit var proofDetailsText: TextView
    internal lateinit var statusText: TextView
    internal val connectionTexts = mutableListOf<TextView>()
    internal lateinit var boardView: LudoBoardView
    // Test seam for state-contract instrumentation: these tests exercise authoritative
    // state retention and action gating, not the GL-heavy board/portrait presentation.
    // Production leaves this null and always uses OnlineLudoPawsPresentation.
    internal var onlinePresentationRendererOverrideForTests:
        ((MatchSnapshot?, MatchSnapshot) -> Unit)? = null
    internal lateinit var diceView: DiceView
    internal lateinit var findMatchButton: LinearLayout
    internal lateinit var cancelMatchmakingButton: Button
    internal lateinit var twoPlayerButton: Button
    internal lateinit var fourPlayerButton: Button
    internal lateinit var matchmakingSetupPanel: LinearLayout
    internal lateinit var matchmakingSearchPanel: LinearLayout
    internal lateinit var matchmakingOpponentRail: MatchmakingOpponentRailView
    internal lateinit var matchmakingStatusText: TextView
    internal lateinit var matchmakingSlotsText: TextView
    internal lateinit var matchmakingTimerText: TextView
    internal lateinit var createButton: Button
    internal lateinit var joinButton: Button
    internal lateinit var startButton: Button
    internal lateinit var refreshButton: Button
    internal lateinit var shareButton: Button
    internal lateinit var rollButton: Button
    internal lateinit var proofButton: Button

    internal val uiStateHolder = OnlineGameStateHolder()

    internal val session by lazy {
        RemoteMatchSession(
            mode =
                gameMode,
            snapshotProvider = {
                currentState
            },
            playerIdProvider = {
                playerId
            },
            api = api,
        )
    }

    internal var matchId: String?
        get() = uiStateHolder.value.matchId
        set(value) { uiStateHolder.update { it.copy(matchId = value) } }

    internal var playerToken: String?
        get() = uiStateHolder.value.playerToken
        set(value) { uiStateHolder.update { it.copy(playerToken = value) } }

    internal var playerId: String?
        get() = uiStateHolder.value.playerId
        set(value) { uiStateHolder.update { it.copy(playerId = value) } }

    internal var currentState: MatchSnapshot?
        get() = uiStateHolder.value.currentState
        set(value) { uiStateHolder.update { it.copy(currentState = value) } }

    internal var isOnline: Boolean
        get() = uiStateHolder.value.isOnline
        set(value) { uiStateHolder.update { it.copy(isOnline = value) } }

    internal var selectedPublicPlayerCount =
        2

    @Volatile
    internal var matchmakingRequestInFlight =
        false

    @Volatile
    internal var realtimeConnected =
        false
    // Stops repeated HTTP polling while the server has explicitly rejected this session.
    internal var remoteSessionUnavailable = false

    internal var friendsVoiceEnabled =
        false

    internal var friendsMicEnabled =
        true

    internal var friendsVoiceStatus =
        "Voice is off"

    internal var remoteExitConfirmationDialog: android.app.Dialog? = null
    private var postWinInterstitialAd: PostWinInterstitialAdController? = null
    private var postWinInterstitialExitGate: PostWinInterstitialExitGate? = null
    private var postWinInterstitialShownForMatchId: String? = null

    @Volatile
    internal var screenStarted =
        false

    internal var lastRealtimeRevision =
        -1
    internal var lastAppliedAuthoritativeRevision =
        -1L

    internal var lastTurnTimeoutNoticeAt = 0L
    internal var timeoutExitScheduled = false

    internal var onlineGameplayActionBlockedUntilMillis = 0L
    internal var lastOnlineGameplayPacingKey: String? = null
    internal var onlineRollRequestInFlight = false
    internal var onlineMoveRequestInFlight = false
    internal var onlineMovePreviewStartedAtMillis: Long? = null
    internal var scheduledAutoMoveEventKey: String? = null
    internal var scheduledAutoMoveRunnable: Runnable? = null
    internal var lastAutoMoveAttemptEventKey: String? = null
    internal var autoMoveAttemptCount: Int = 0

    internal fun canSubmitOnlineGameplayAction(): Boolean =
        SystemClock.uptimeMillis() >= onlineGameplayActionBlockedUntilMillis &&
            !onlineRollRequestInFlight &&
            !onlineMoveRequestInFlight

    internal fun releaseOnlineGameplayRequest() {
        if (onlineMovePreviewStartedAtMillis != null) {
            OnlineLudoPawsPresentation.cancelPendingMovePreview(this, currentState)
        }
        onlineMovePreviewStartedAtMillis = null
        onlineRollRequestInFlight = false
        onlineMoveRequestInFlight = false
        if (!canRenderUi()) return
        currentState?.let { state ->
            updateRollButton()
            OnlineLudoPawsPresentation.refreshActionControls(this, state)
            scheduleOnlineAutoMove(state)
        }
    }

    internal fun scheduleOnlineGameplayUnlock() {
        val remaining =
            (onlineGameplayActionBlockedUntilMillis - SystemClock.uptimeMillis())
                .coerceAtLeast(0L)
        if (!screenStarted || remaining == 0L) return
        mainHandler.removeCallbacks(onlineGameplayUnlockRunnable)
        mainHandler.postDelayed(onlineGameplayUnlockRunnable, remaining)
    }

    internal val onlineGameplayUnlockRunnable =
        object : Runnable {
            override fun run() {
                if (!screenStarted) return
                val remaining =
                    (onlineGameplayActionBlockedUntilMillis - SystemClock.uptimeMillis())
                        .coerceAtLeast(0L)
                if (remaining > 0L) {
                    mainHandler.postDelayed(this, remaining)
                    return
                }

                currentState?.let { state ->
                    updateControls(state)
                    updateRollButton()
                    OnlineLudoPawsPresentation.refreshActionControls(this@MainActivity, state)
                }
            }
        }

    internal val realtimeRefreshRunnable =
        Runnable {
            if (
                canRenderUi() &&
                isOnline &&
                playerToken != null
            ) {
                refreshState(
                    silent = true,
                )
            }
        }

    internal val matchmakingPollRunnable =
        object : Runnable {
            override fun run() {
                if (
                    canRenderUi() &&
                    isOnline &&
                    (
                        matchId == null ||
                        currentState
                            ?.status ==
                        "FINISHED"
                    ) &&
                    publicMatchmakingStore.load() !=
                    null
                ) {
                    pollPublicMatchmaking()
                }
                mainHandler.postDelayed(
                    this,
                    MATCHMAKING_POLL_MS,
                )
            }
        }

    internal val realtimeReconnectRunnable =
        object : Runnable {
            override fun run() {
                if (remoteSessionUnavailable) return
                if (
                    canRenderUi() &&
                    isOnline &&
                    playerToken != null &&
                    !realtimeConnected
                ) {
                    connectRealtimeIfPossible()
                }
                mainHandler.postDelayed(
                    this,
                    REALTIME_RECONNECT_MS,
                )
            }
        }

    internal val statePollRunnable =
        object : Runnable {
            override fun run() {
                if (remoteSessionUnavailable) return
                val state = currentState
                if (
                    isOnline &&
                    playerToken != null &&
                    ::refreshButton.isInitialized &&
                    refreshButton.isEnabled &&
                    state?.status != "FINISHED"
                ) {
                    refreshState(
                        silent = true,
                    )
                }
                mainHandler.postDelayed(
                    this,
                    if (realtimeConnected) {
                        STATE_POLL_LIVE_MS
                    } else {
                        STATE_POLL_RECOVERY_MS
                    },
                )
            }
        }

    internal val secureSessionStore by lazy {
        SecureSessionStore(this)
    }

    internal var secureSessionPersistenceFailed =
        false

    internal val pendingOperationStore by lazy {
        PendingOperationStore(this)
    }

    internal val cachedMatchStore by lazy {
        CachedMatchStore(this)
    }

    internal val publicMatchmakingStore by lazy {
        PublicMatchmakingStore(this)
    }

    internal val realtimeClient: MatchRealtimeClient by lazy {
        createRealtimeClient(STATE_POLL_LIVE_MS, STATE_RECONCILE_DELAY_MS)
    }

    internal val connectivityMonitor by lazy {
        ConnectivityMonitor(this) { online ->
            mainHandler.post {
                if (
                    !canRenderUi()
                ) {
                    return@post
                }

                val changed =
                    isOnline != online
                isOnline = online

                if (!online) {
                    realtimeConnected =
                        false
                    realtimeClient.disconnect()
                }
                updateConnectionLabel()

                currentState?.let {
                    updateControls(it)
                } ?: setNetworkControls(
                    enabled = online,
                )

                if (!online) {
                    showStatus(
                        "Offline — last online state stays visible. Verified rolls and moves resume when internet returns.",
                    )
                } else if (
                    changed &&
                    playerToken != null
                ) {
                    refreshState(
                        silent = true,
                    )
                    connectRealtimeIfPossible()
                } else if (
                    changed &&
                    publicMatchmakingStore.load() !=
                    null
                ) {
                    pollPublicMatchmaking()
                }
            }
        }
    }

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(
            savedInstanceState,
        )
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    requestRemoteExitAfterWinInterstitial()
                }
            },
        )
        LudoProofTheme.configureWindow(
            this,
        )
        if (savedInstanceState != null) {
            intent.putExtra(GameModeIntent.EXTRA_RESUME_SAVED_MATCH, true)
        }
        GameSoundFeedback.preloadDiceFeedback(this)

        if (
            shouldRestoreSavedSession
        ) {
            secureSessionStore
                .load()
                ?.let {
                        session ->
                    matchId =
                        session.matchId
                    playerToken =
                        session.playerToken
                    playerId =
                        session.playerId
                }
        } else {
            matchId =
                null
            playerToken =
                null
            playerId =
                null
            currentState =
                null
        }

        buildOnlineMatchScreen()
        updateRollButton()
        restorePublicMatchmakingUi()

        if (
            matchId != null &&
            playerToken != null
        ) {
            matchInput.setText(
                matchId,
            )
            cachedMatchStore
                .load()
                ?.let {
                    applyResponse(
                        it,
                        announce = false,
                    )
                }
        }
        preparePostWinInterstitialAd()
    }

    internal fun preparePostWinInterstitialAd() {
        if (!BuildConfig.LUDOPROOF_INTERSTITIAL_ADS_ENABLED || postWinInterstitialAd != null) return
        postWinInterstitialAd = PostWinInterstitialAdController(
            activity = this,
            enabled = true,
            adUnitId = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID,
        ).also { it.prepare() }
    }

    private fun showPostWinInterstitialIfReady(
        matchId: String,
        onSkipAd: () -> Unit,
    ): Boolean {
        if (postWinInterstitialShownForMatchId == matchId) return false
        val interstitial = postWinInterstitialAd ?: return false
        val gate = postWinInterstitialExitGate ?: PostWinInterstitialExitGate(
            activity = this,
            accountRepository = (application as LudoProofApplication).accountRepository,
            walletStore = GemWalletStore(this),
            interstitial = interstitial,
        ).also { postWinInterstitialExitGate = it }
        return gate.interceptIfReady(
            onAdDismissed = {
                postWinInterstitialShownForMatchId = matchId
                onSkipAd()
            },
            onSkipAd = onSkipAd,
        )
    }

    internal fun requestRemoteExitAfterWinInterstitial() {
        val state = currentState
        val currentPlayer = state?.players?.firstOrNull { it.playerId == playerId }
        val didCurrentPlayerWin = PostWinInterstitialPolicy.shouldShowRemote(
            matchStatus = state?.status,
            teamBased = gameMode.teamBased,
            winnerPlayerId = state?.winnerPlayerId,
            playerId = playerId,
            winnerTeamId = state?.winnerTeamId,
            playerTeamId = currentPlayer?.teamId,
        )
        val resultIsAuthoritative =
            uiStateHolder.value.currentStateSource == OnlineStateSource.AUTHORITATIVE
        val finishedMatchId = state?.matchId
        if (
            resultIsAuthoritative &&
            didCurrentPlayerWin &&
            finishedMatchId != null &&
            showPostWinInterstitialIfReady(
                matchId = finishedMatchId,
                onSkipAd = { requestRemoteExit() },
            )
        ) {
            return
        }
        requestRemoteExit()
    }

    override fun onStart() {
        super.onStart()
        // A new foreground session may have refreshed permissions or credentials.
        remoteSessionUnavailable = false
        screenStarted = true
        connectivityMonitor.start()
        scheduleOnlineGameplayUnlock()
        currentState?.let(::scheduleOnlineAutoMove)
        mainHandler.removeCallbacks(
            statePollRunnable,
        )
        mainHandler.post(
            statePollRunnable,
        )
        mainHandler.removeCallbacks(
            matchmakingPollRunnable,
        )
        mainHandler.post(
            matchmakingPollRunnable,
        )
        mainHandler.removeCallbacks(
            realtimeReconnectRunnable,
        )
        mainHandler.post(
            realtimeReconnectRunnable,
        )
        connectRealtimeIfPossible()
    }

    override fun onStop() {
        stopFriendsVoice("Voice paused when you left the match.")
        screenStarted = false
        cancelScheduledOnlineAutoMove()
        mainHandler.removeCallbacks(onlineGameplayUnlockRunnable)
        uiStateHolder.update {
            it.copy(
                currentStateSource = null,
            )
        }
        realtimeConnected = false
        connectivityMonitor.stop()
        mainHandler.removeCallbacks(
            statePollRunnable,
        )
        mainHandler.removeCallbacks(
            matchmakingPollRunnable,
        )
        mainHandler.removeCallbacks(
            realtimeReconnectRunnable,
        )
        mainHandler.removeCallbacks(
            realtimeRefreshRunnable,
        )
        realtimeClient.disconnect()
        super.onStop()
    }

    internal fun canRenderUi(): Boolean =
        !isFinishing &&
            !isDestroyed

    override fun onDestroy() {
        stopFriendsVoice("Voice is off")
        cancelScheduledOnlineAutoMove()
        remoteExitConfirmationDialog?.dismiss()
        remoteExitConfirmationDialog = null
        postWinInterstitialExitGate?.close()
        postWinInterstitialExitGate = null
        postWinInterstitialAd?.close()
        postWinInterstitialAd = null
        mainHandler.removeCallbacksAndMessages(
            null,
        )
        realtimeClient.shutdown()
        executor.shutdownNow()
        gameplayExecutor.shutdownNow()
        super.onDestroy()
    }

    internal fun toggleFriendsVoice() {
        showStatus("Voice is unavailable in the family edition. Use fixed quick-chat reactions.")
    }

    internal fun toggleFriendsMic() = Unit

    internal fun stopFriendsVoice(status: String = "Voice is unavailable") {
        friendsVoiceEnabled = false
        updateFriendsVoiceStatus(status)
    }

    internal fun updateFriendsVoiceStatus(status: String) {
        friendsVoiceStatus = status
        currentState?.let { OnlineLudoPawsPresentation.refreshVoiceControls(this, it) }
    }

    internal fun isMatchmakingUiReady(): Boolean =
        ::findMatchButton.isInitialized &&
            ::cancelMatchmakingButton.isInitialized &&
            ::matchmakingSetupPanel.isInitialized &&
            ::matchmakingSearchPanel.isInitialized &&
            ::matchmakingOpponentRail.isInitialized &&
            ::matchmakingStatusText.isInitialized &&
            ::matchmakingSlotsText.isInitialized &&
            ::matchmakingTimerText.isInitialized &&
            ::twoPlayerButton.isInitialized &&
            ::fourPlayerButton.isInitialized

    internal fun isPlayerCountUiReady(): Boolean =
        ::twoPlayerButton.isInitialized &&
            ::fourPlayerButton.isInitialized

    internal fun updateConnectionLabel() {
        if (connectionTexts.isEmpty()) return

        val (label, tint) = when {
            !isOnline -> "OFFLINE" to LudoProofTheme.GOLD
            realtimeConnected -> "LIVE" to 0xFF65EF55.toInt()
            else -> "ONLINE" to 0xFFD4FFB2.toInt()
        }
        connectionTexts.forEach { statusLabel ->
            statusLabel.text = label
            statusLabel.setTextColor(tint)
        }
    }

    private companion object {
        const val STATE_POLL_RECOVERY_MS =
            3_000L
        const val STATE_POLL_LIVE_MS =
            30_000L
        const val STATE_RECONCILE_DELAY_MS =
            250L
        const val MATCHMAKING_POLL_MS =
            1_500L
        const val REALTIME_RECONNECT_MS =
            10_000L
    }
}
