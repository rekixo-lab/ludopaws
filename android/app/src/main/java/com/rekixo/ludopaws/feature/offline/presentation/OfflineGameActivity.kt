package com.rekixo.ludopaws

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsFeedbackLedger
import com.rekixo.ludopaws.feature.offline.data.local.OfflineCharacterSetupStore
import com.rekixo.ludopaws.feature.store.data.ads.PostWinInterstitialAdController
import com.rekixo.ludopaws.feature.store.data.ads.PostWinInterstitialExitGate
import com.rekixo.ludopaws.feature.store.data.local.GemWalletStore
import com.rekixo.ludopaws.ui.offline.common.*
import com.rekixo.ludopaws.ui.offline.gameplay.*
import com.rekixo.ludopaws.ui.offline.setup.*
import com.rekixo.ludopaws.feature.offline.*

class OfflineGameActivity : ComponentActivity() {
    internal val handler = Handler(Looper.getMainLooper())
    internal val offlineRollExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    internal var offlineGeneration = 0L
    internal var offlineRollInFlight = false
    private var offlineForeground = false
    internal var offlineModalPaused = false
    internal val offlineActionsAllowed: Boolean get() = offlineForeground && !offlineModalPaused && !isFinishing && !isDestroyed

    internal fun pauseOfflineActions() {
        offlineGeneration++
        handler.removeCallbacksAndMessages(null)
        offlineRollInFlight = false
        computerActionRevision = null
        singleLegalMoveKey = null
        diceView?.stopRolling()
    }

    internal fun resumeOfflineActions() {
        if (offlineActionsAllowed && boardView != null) session.snapshot()?.let { renderGame(it) }
    }

    override fun onStart() {
        super.onStart()
        offlineForeground = true
        resumeOfflineActions()
    }

    override fun onStop() {
        offlineForeground = false
        pauseOfflineActions()
        super.onStop()
    }

    internal val feedbackLedger = LudoPawsFeedbackLedger()

    internal val gameMode:
        GameMode by lazy {
        GameMode
            .fromWireValue(
                intent.getStringExtra(
                    EXTRA_GAME_MODE,
                )
                    ?: intent.getStringExtra(
                        EXTRA_PLAY_MODE,
                    ),
            )
            ?.takeIf {
                it.isLocal
            }
            ?: GameMode.PASS_AND_PLAY
    }

    internal val shouldResumeSavedGame:
        Boolean by lazy {
        intent.getBooleanExtra(
            GameModeIntent.EXTRA_RESUME_SAVED_MATCH,
            false,
        )
    }

    internal val isComputerMode: Boolean
        get() =
            gameMode ==
                GameMode.COMPUTER

    internal val session by lazy {
        LocalMatchSession(
            context =
                this,
            mode =
                gameMode,
        )
    }

    internal val engine:
        OfflineGameEngine
        get() =
            session.engine

    internal val characterSelectionStore by lazy {
        CharacterSelectionStore(this)
    }

    internal val offlineCharacterSetupStore by lazy {
        OfflineCharacterSetupStore(this)
    }

    internal val setupStateHolder = OfflineSetupStateHolder()

    internal var selectedPlayers: Int
        get() = setupStateHolder.value.selectedPlayers
        set(value) { setupStateHolder.update { it.copy(selectedPlayers = value) } }

    internal var selectedColor: String
        get() = setupStateHolder.value.selectedColor
        set(value) { setupStateHolder.update { it.copy(selectedColor = value) } }

    internal var selectedCharacterSlot: Int = 0
    internal var selectedCharacterIds: List<String> = emptyList()
    internal var selectedColorsBySeat: List<String> = emptyList()
    internal var colorSelectionStatus: TextView? = null
    internal var activeCharacterMatchId: String? = null
    internal var activeCharacterIdsBySeat: List<String> = emptyList()

    internal var playerButtons: Map<Int, View> = emptyMap()
    internal var colorButtons: Map<String, View> = emptyMap()
    internal var characterSlotsRow: LinearLayout? = null
    internal var characterSlotButtons: Map<Int, Button> = emptyMap()
    internal var characterCards: Map<String, LinearLayout> = emptyMap()
    internal var characterSummaryText: TextView? = null

    internal var boardView: LudoPawsReactiveBoardView? = null
    internal var diceView: DiceView? = null
    internal var diceHost: FrameLayout? = null
    // Scheduled moves must never overwrite the dice view's stable lookup tag.
    internal var singleLegalMoveKey: String? = null
    internal var topPlayerRail: LinearLayout? = null
    internal var bottomPlayerRail: LinearLayout? = null
    // Stable portrait instances across repeated renderPlayerRails() calls.
    internal val playerCardViewsBySeat = mutableMapOf<Int, LudoPawsPlayerCardView>()
    internal var turnText: TextView? = null
    internal var infoText: TextView? = null
    internal var statusText: TextView? = null
    internal var computerActionRevision: Int? = null
    internal var presentedDiceEventKey: String? = null
    internal var gameplayActionBlockedUntilMillis: Long = 0L
    internal var gameplayPawsPromotion: com.rekixo.ludopaws.feature.store.presentation.components.GamePawsPromotionView? = null
    internal var exitConfirmationDialog: Dialog? = null
    internal var turnHandoffDialog: Dialog? = null
    internal var quickReactionView: View? = null
    internal var quickReactionLastShownAtMs: Long? = null
    internal lateinit var resultPanel: FrameLayout
    internal lateinit var resultTitleText: TextView
    private var postWinInterstitialAd: PostWinInterstitialAdController? = null
    private var postWinInterstitialExitGate: PostWinInterstitialExitGate? = null
    internal var postWinInterstitialShownForMatchId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LudoProofTheme.configureWindow(this)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    requestOfflineExit()
                }
            },
        )

        if (savedInstanceState != null) {
            intent.putExtra(GameModeIntent.EXTRA_RESUME_SAVED_MATCH, true)
        }
        initializeCharacterSetup()

        if (
            shouldResumeSavedGame &&
            session.hasSavedGame()
        ) {
            showGame(
                session.snapshot(),
            )
        } else {
            showSetup()
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

    internal fun showPostWinInterstitialIfReady(
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

    internal fun prepareOfflineUiTransition() {
        pauseOfflineActions()
        boardView?.let { com.rekixo.ludopaws.ui.quickchat.clearQuickChatReactions(it) }
        handler.removeCallbacksAndMessages(null)
        turnHandoffDialog
            ?.dismiss()
        turnHandoffDialog =
            null
        quickReactionView
            ?.animate()
            ?.cancel()
        (quickReactionView?.parent as? android.view.ViewGroup)
            ?.removeView(quickReactionView)
        quickReactionView =
            null
        quickReactionLastShownAtMs =
            null
        computerActionRevision =
            null
        presentedDiceEventKey =
            null
        gameplayActionBlockedUntilMillis =
            0L
        singleLegalMoveKey = null
        feedbackLedger.clear()
        activeCharacterMatchId =
            null
        activeCharacterIdsBySeat =
            emptyList()
        diceView
            ?.stopRolling()
        boardView =
            null
        diceView =
            null
        diceHost =
            null
        topPlayerRail =
            null
        bottomPlayerRail =
            null
        playerCardViewsBySeat.clear()
        turnText =
            null
        infoText =
            null
        statusText =
            null
        characterSlotsRow =
            null
        characterSlotButtons =
            emptyMap()
        characterCards =
            emptyMap()
        characterSummaryText =
            null
        colorSelectionStatus = null
    }

    override fun onResume() {
        super.onResume()
        if (characterCards.isNotEmpty()) refreshCharacterSelectionUi()
    }

    override fun onDestroy() {
        exitConfirmationDialog
            ?.dismiss()
        exitConfirmationDialog =
            null
        turnHandoffDialog
            ?.dismiss()
        turnHandoffDialog =
            null
        postWinInterstitialExitGate?.close()
        postWinInterstitialExitGate = null
        postWinInterstitialAd?.close()
        postWinInterstitialAd = null
        prepareOfflineUiTransition()
        offlineRollExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GAME_MODE =
            GameModeIntent.EXTRA_GAME_MODE

        // Legacy extras remain readable so installed builds and old intents
        // can identify the requested local mode after upgrade. Resuming now
        // requires the explicit EXTRA_RESUME_SAVED_MATCH flag.
        const val EXTRA_PLAY_MODE =
            "ludoproof_play_mode"
        const val PLAY_MODE_LOCAL =
            "LOCAL"
        const val PLAY_MODE_COMPUTER =
            "COMPUTER"
    }
}
