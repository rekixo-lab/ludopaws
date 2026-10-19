package com.rekixo.ludopaws

import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
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
import com.rekixo.ludopaws.feature.online.FriendsVoiceChatSession
import com.rekixo.ludopaws.feature.store.data.ads.PostWinInterstitialAdController
import com.rekixo.ludopaws.feature.store.data.ads.PostWinInterstitialExitGate
import com.rekixo.ludopaws.feature.store.data.local.GemWalletStore
import com.rekixo.ludopaws.feature.store.domain.PostWinInterstitialPolicy

class MainActivity : ComponentActivity() {
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

    internal var friendsVoiceEnabled =
        false

    internal var friendsMicEnabled =
        true

    internal var friendsVoiceStatus =
        "Voice is off"

    private var friendsVoiceSession: FriendsVoiceChatSession? = null
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
        MatchRealtimeClient(
            onSnapshot = { response ->
                mainHandler.post {
                    val incomingMatchId = response.optJSONObject("state")?.optString("matchId")
                    if (canRenderUi() && screenStarted && incomingMatchId == matchId) {
                        mainHandler.removeCallbacks(realtimeRefreshRunnable)
                        applyResponse(response, announce = false, silentValidationFailure = false)
                    }
                }
            },
            onRevision = {
                    revision,
                    _,
                ->
                mainHandler.post {
                    if (
                        !canRenderUi()
                    ) {
                        return@post
                    }

                    if (revision > lastRealtimeRevision) {
                        // Roll HTTP responses apply authoritative snapshots directly.
                        // Avoid another GET if this socket revision was already applied.
                        if (revision.toLong() <= lastAppliedAuthoritativeRevision) {
                            lastRealtimeRevision = maxOf(lastRealtimeRevision, revision)
                        } else {
                            lastRealtimeRevision = revision
                            mainHandler.removeCallbacks(realtimeRefreshRunnable)
                            mainHandler.postDelayed(realtimeRefreshRunnable, 40L)
                        }
                    }
                }
            },
            onConnectionChanged = {
                    connected ->
                mainHandler.post {
                    if (
                        !canRenderUi()
                    ) {
                        return@post
                    }
                    realtimeConnected =
                        connected
                    if (connected && friendsVoiceEnabled) {
                        if (!realtimeClient.sendVoiceJoin()) {
                            setFriendsVoiceStatus("Voice signaling is reconnecting…")
                        }
                    }
                    updateConnectionLabel()
                    if (screenStarted && playerToken != null) {
                        mainHandler.removeCallbacks(statePollRunnable)
                        if (connected) {
                            refreshState(silent = true)
                            mainHandler.postDelayed(
                                statePollRunnable,
                                STATE_POLL_LIVE_MS,
                            )
                        } else {
                            mainHandler.postDelayed(
                                statePollRunnable,
                                STATE_RECONCILE_DELAY_MS,
                            )
                        }
                    }
                }
            },
            onQuickChat = { senderPlayerId, displayName, emoji ->
                mainHandler.post {
                    if (!canRenderUi()) return@post
                    OnlineLudoPawsPresentation.presentRemoteQuickChat(
                        activity = this,
                        senderPlayerId = senderPlayerId,
                        displayName = displayName,
                        emoji = emoji,
                    )
                }
            },
            onVoicePeers = { playerIds ->
                mainHandler.post {
                    if (friendsVoiceEnabled) friendsVoiceSession?.updatePeers(playerIds)
                }
            },
            onVoiceSignal = { senderPlayerId, signal ->
                mainHandler.post {
                    if (friendsVoiceEnabled) {
                        friendsVoiceSession?.handleSignal(senderPlayerId, signal)
                    }
                }
            },
            onVoiceEnded = {
                mainHandler.post {
                    stopFriendsVoice("Voice ended with the match.")
                }
            },
        )
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

        val (root, host) =
            LudoProofTheme
                .arcadeRoot(this)
        arcadeRootView = root

        val scroll =
            ScrollView(this).apply {
                isFillViewport =
                    true
                overScrollMode =
                    View.OVER_SCROLL_NEVER
            }

        val contentHost =
            FrameLayout(this)
        scroll.addView(
            contentHost,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val horizontalPaddingDp =
            LudoProofTheme
                .pageHorizontalPaddingDp(this)
        val isExpandedWidth =
            LudoProofTheme
                .isExpandedWidth(this)
        val contentWidth =
            if (isExpandedWidth) {
                minOf(
                    resources.displayMetrics.widthPixels,
                    dp(
                        LudoProofTheme
                            .pageMaxContentWidthDp(this),
                    ),
                )
            } else {
                resources.displayMetrics.widthPixels
            }
        val sectionSideMargin =
            if (isExpandedWidth) {
                0
            } else {
                dp(horizontalPaddingDp)
            }

        val content =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setPadding(
                    0,
                    dp(14),
                    0,
                    dp(28),
                )
        }

        contentHost.addView(
            content,
            FrameLayout.LayoutParams(
                contentWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or
                    Gravity.CENTER_HORIZONTAL,
            ),
        )

        host.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        registerOnlineBackCallback()

        if (
            gameMode !=
            GameMode.ONLINE
        ) {
            val hero = onlineHero()
            onlineHeroPanel = hero
            content.addView(
                hero,
                onlineSectionParams(
                    if (isCompactOnline()) 12 else 16,
                ).apply {
                    leftMargin =
                        sectionSideMargin
                    rightMargin =
                        sectionSideMargin
                },
            )
        }

        lobbyPanel =
            onlineLobbyPanel()
        content.addView(
            lobbyPanel,
            onlineSectionParams(
                if (isCompactOnline()) 12 else 16,
            ).apply {
                leftMargin =
                    sectionSideMargin
                rightMargin =
                    sectionSideMargin
            },
        )

        matchStatusPanel =
            onlineMatchStatusPanel()
                .apply {
                    visibility =
                        View.GONE
                }
        content.addView(
            matchStatusPanel,
            onlineSectionParams(
                if (isCompactOnline()) 4 else 6,
            ).apply {
                leftMargin =
                    sectionSideMargin
                rightMargin =
                    sectionSideMargin
            },
        )

        resultPanel =
            onlineResultPanel()
                .apply {
                    visibility =
                        View.GONE
                }
        content.addView(
            resultPanel,
            onlineSectionParams(
                if (isCompactOnline()) 12 else 16,
            ).apply {
                leftMargin =
                    sectionSideMargin
                rightMargin =
                    sectionSideMargin
            },
        )

        boardView =
            LudoBoardView(this).apply {
                onTokenSelected = {
                        tokenIndex ->
                    moveToken(
                        tokenIndex,
                    )
                }
            }

        boardFrame =
            FrameLayout(this).apply {
                visibility =
                    View.GONE
                clipChildren =
                    false
                clipToPadding =
                    false
            }
        boardFrame.addView(
            boardView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(
            boardFrame,
            onlineSectionParams(
                if (isCompactOnline()) 4 else 6,
            ).apply {
                leftMargin = dp(1)
                rightMargin = dp(1)
            },
        )

        actionPanel =
            onlineActionPanel()
                .apply {
                    visibility =
                        View.GONE
                }
        content.addView(
            actionPanel,
            onlineSectionParams(
                if (isCompactOnline()) 2 else 4,
            ),
        )

        val topActionsBar =
            addGameTopActions(
                host = host,
                context = this,
                onBack = { requestRemoteExitAfterWinInterstitial() },
                onSettings = {
                    ArcadeDialogs.showSettings(this) {
                        currentState?.let { state ->
                            OnlineLudoPawsPresentation.render(
                                activity = this,
                                previous = state,
                                current = state,
                            )
                        }
                    }
                },
            ).apply {
                // Use the shared plywood skin only for the Online screen's Back and Settings.
                for (index in 0 until childCount) {
                    getChildAt(index).background =
                        plywoodBackground(
                            context = this@MainActivity,
                            cornerRadiusDp = 999f,
                        )
                }
            }

        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, insets ->
            val cutoutTopInset =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    insets.displayCutout?.safeInsetTop ?: 0
                } else {
                    0
                }
            val statusBarTopInset =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetTop
                }
            val safeTopInset = maxOf(cutoutTopInset, statusBarTopInset)
            content.setPadding(
                content.paddingLeft,
                dp(14) + safeTopInset,
                content.paddingRight,
                content.paddingBottom,
            )
            topActionsBar.translationY = safeTopInset.toFloat()
            insets
        }
        root.requestApplyInsets()
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

    private fun requestRemoteExitAfterWinInterstitial() {
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
        if (!com.rekixo.ludopaws.feature.safety.AgeAccessStore(this).canUseConnectedFeatures()) {
            finish()
            return
        }
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
        val state = currentState
        if (gameMode != GameMode.FRIENDS || state?.status != "ACTIVE") {
            showStatus("Voice chat is available during an active Friends match.")
            return
        }
        if (friendsVoiceEnabled) {
            stopFriendsVoice("Voice is off")
            return
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                FRIENDS_VOICE_PERMISSION_REQUEST,
            )
            setFriendsVoiceStatus("Allow microphone access to use voice.")
            return
        }
        startFriendsVoice()
    }

    internal fun toggleFriendsMic() {
        if (!friendsVoiceEnabled) return
        friendsMicEnabled = !friendsMicEnabled
        friendsVoiceSession?.setMicEnabled(friendsMicEnabled)
        setFriendsVoiceStatus(if (friendsMicEnabled) "Microphone is on" else "Microphone muted")
        currentState?.let { OnlineLudoPawsPresentation.refreshVoiceControls(this, it) }
    }

    private fun startFriendsVoice() {
        if (friendsVoiceEnabled) return
        val localPlayerId = playerId
        if (localPlayerId.isNullOrBlank()) {
            setFriendsVoiceStatus("Your player identity is not ready yet.")
            return
        }
        try {
            friendsVoiceSession =
                FriendsVoiceChatSession(
                    context = this,
                    localPlayerId = localPlayerId,
                    sendSignal = { peerId, signal ->
                        realtimeClient.sendVoiceSignal(peerId, signal)
                    },
                    onStatusChanged = { status ->
                        mainHandler.post {
                            if (friendsVoiceEnabled && canRenderUi()) {
                                setFriendsVoiceStatus(status)
                            }
                        }
                    },
                ).also { it.setMicEnabled(friendsMicEnabled) }
            friendsVoiceEnabled = true
            friendsVoiceStatus = "Connecting to Friends voice…"
            if (realtimeConnected) {
                if (!realtimeClient.sendVoiceJoin()) {
                    friendsVoiceStatus = "Voice signaling is reconnecting…"
                }
            } else {
                connectRealtimeIfPossible()
                friendsVoiceStatus = "Waiting for a secure match connection…"
            }
        } catch (error: Exception) {
            friendsVoiceSession?.close()
            friendsVoiceSession = null
            friendsVoiceEnabled = false
            friendsVoiceStatus = "Could not start voice: ${error.message ?: "audio setup failed"}"
        }
        currentState?.let { OnlineLudoPawsPresentation.refreshVoiceControls(this, it) }
    }

    internal fun stopFriendsVoice(status: String = "Voice is off") {
        if (friendsVoiceEnabled) realtimeClient.sendVoiceLeave()
        friendsVoiceEnabled = false
        friendsVoiceSession?.close()
        friendsVoiceSession = null
        setFriendsVoiceStatus(status)
    }

    private fun setFriendsVoiceStatus(status: String) {
        friendsVoiceStatus = status
        currentState?.let { OnlineLudoPawsPresentation.refreshVoiceControls(this, it) }
    }

    @Deprecated("Use the activity result API for new permission requests")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != FRIENDS_VOICE_PERMISSION_REQUEST) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startFriendsVoice()
        } else {
            setFriendsVoiceStatus("Microphone permission denied. Voice chat is off.")
        }
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
        const val FRIENDS_VOICE_PERMISSION_REQUEST = 731
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
