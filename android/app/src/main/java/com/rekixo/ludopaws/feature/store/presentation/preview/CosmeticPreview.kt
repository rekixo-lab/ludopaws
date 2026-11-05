package com.rekixo.ludopaws.feature.store.presentation.preview

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.DiceView
import com.rekixo.ludopaws.HistoryEventSnapshot
import com.rekixo.ludopaws.LudoBoardView
import com.rekixo.ludopaws.LudoPathEncoding
import com.rekixo.ludopaws.LudoPawsBoardChromeView
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.LudoPawsReactiveBoardView
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.MatchSnapshot
import com.rekixo.ludopaws.PendingRollSnapshot
import com.rekixo.ludopaws.PlayerSnapshot
import com.rekixo.ludopaws.LudoPawsGameplayPacingPolicy
import com.rekixo.ludopaws.ui.dialogs.PlywoodAlertDialogBuilder
import com.rekixo.ludopaws.feature.boards.domain.BoardRegistry
import com.rekixo.ludopaws.feature.characters.data.audio.LudoPawsVoicePlayer
import com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.characters.rendering.CharacterMotion
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.dice.domain.DiceRegistry
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.feature.settings.data.local.GameSoundFeedback
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.components.FeaturedPawsPreview

/** One identity and one preview entry point across catalog, spotlight and bundles. */
internal class CosmeticPreview private constructor(
    val category: CosmeticCategory,
    val id: String,
    val title: String,
) {
    companion object {
        fun forOffer(offer: StoreCosmetic): CosmeticPreview? {
            if (!offer.contentAvailable) return null
            return when (offer.category) {
                CosmeticCategory.BOARD -> board(offer.id)
                CosmeticCategory.DICE -> dice(offer.id)
                CosmeticCategory.CHARACTER_PACK -> CharacterDefinitions.all.firstOrNull {
                    StoreCosmeticCatalog.characterUnlock(it.character.id)?.id == offer.id
                }?.let { character(it.character.id) }
                CosmeticCategory.AVATAR -> null
            }
        }

        fun board(id: String): CosmeticPreview? = BoardRegistry.find(id)
            ?.offer?.takeIf { it.contentAvailable }
            ?.let { CosmeticPreview(CosmeticCategory.BOARD, it.id, it.title) }

        fun dice(id: String): CosmeticPreview? = DiceRegistry.find(id)
            ?.offer?.takeIf { it.contentAvailable }
            ?.let { CosmeticPreview(CosmeticCategory.DICE, it.id, it.title) }

        fun character(id: String): CosmeticPreview? = LudoPawsCharacterCatalog.character(id)
            ?.takeIf { CharacterRegistry.find(it.id) != null }
            ?.let { CosmeticPreview(CosmeticCategory.CHARACTER_PACK, it.id, it.displayName) }
    }

    fun artwork(context: Context, animatePaw: Boolean = false): View = when (category) {
        CosmeticCategory.BOARD -> FrameLayout(context).apply {
            val base = LudoBoardView(context).apply {
                setBoardThemeOverride(this@CosmeticPreview.id)
                setClassicTokenDrawingEnabled(false)
            }
            addView(base, FrameLayout.LayoutParams(-1, -1))
            addView(LudoPawsBoardChromeView(context).apply {
                setBoardThemeOverride(this@CosmeticPreview.id)
                setFootprintSource(base)
            }, FrameLayout.LayoutParams(-1, -1))
        }
        CosmeticCategory.DICE -> diceView(context)
        else -> if (animatePaw && animationsAllowed(context)) {
            LivePawArtwork(context, id)
        } else {
            LudoPawsCharacterAvatarView(context).apply { bind(this@CosmeticPreview.id, showCircularChrome = false) }
        }
    }

    fun attachTo(view: View, actionLabel: CharSequence? = null, onAction: (() -> Unit)? = null) {
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        view.contentDescription = "Preview $title"
        view.isFocusable = true
        view.setOnClickListener { show(view.context, actionLabel, onAction) }
    }

    fun show(context: Context, actionLabel: CharSequence? = null, onAction: (() -> Unit)? = null) {
        val activity = generateSequence(context) { current ->
            (current as? ContextWrapper)?.baseContext?.takeUnless { it === current }
        }.filterIsInstance<Activity>().firstOrNull() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val settings = GameSettingsStore(context).snapshot()
        val motion = animationsAllowed(context)
        val density = context.resources.displayMetrics.density
        fun dp(n: Int) = (n * density + .5f).toInt()
        val configuration = context.resources.configuration
        val side = minOf(dp(if (category == CosmeticCategory.BOARD) 312 else 218),
            dp((configuration.screenWidthDp - 60).coerceAtLeast(120)),
            dp((configuration.screenHeightDp * .52f - 94).toInt().coerceAtLeast(108)))
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(6), dp(4), dp(4))
        }
        val stage = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        content.addView(stage, LinearLayout.LayoutParams(side, side))
        val hint = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(0xFF523B28.toInt())
            textSize = 13f
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }
        content.addView(hint, LinearLayout.LayoutParams(-1, -2))
        var cleanup: () -> Unit = {}
        fun control(row: LinearLayout, label: String, action: () -> Unit): Button =
            Button(context).apply {
                text = label
                isAllCaps = false
                textSize = 13f
                LudoProofTheme.secondary(this)
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(4), dp(6), dp(4), dp(6))
                setOnClickListener { action() }
                row.addView(this, LinearLayout.LayoutParams(0, -2, 1f))
            }
        val controls = LinearLayout(context).apply { gravity = Gravity.CENTER }
        content.addView(controls, LinearLayout.LayoutParams(-1, -2))
        when (category) {
            CosmeticCategory.BOARD -> {
                hint.text = "Practice board · Tap the highlighted Paw or Move Paw"
                val board = LudoPawsReactiveBoardView(context).apply {
                    cosmeticPreview = true
                    setBoardThemeOverride(this@CosmeticPreview.id)
                }
                stage.addView(board, FrameLayout.LayoutParams(-1, -1))
                val demo = BoardPreviewDemo(board)
                control(controls, "Move Paw") { demo.move() }
                control(controls, "Reset") { demo.reset() }
            }
            CosmeticCategory.DICE -> {
                hint.text = "Practice roll"
                val die = diceView(context)
                stage.addView(die, FrameLayout.LayoutParams(-1, -1))
                var face = DiceRegistry.resolve(id).previewFace
                var rolling = false
                val settle = Runnable {
                    die.showOutcome(face, animate = motion)
                    GameSoundFeedback.diceSettle(context, diceStyleId = id)
                    hint.text = "Practice roll · $face"
                    rolling = false
                }
                control(controls, "Roll") {
                    if (!rolling && die.remainingOutcomePresentationMillis(0L) == 0L) {
                        rolling = true
                        face = kotlin.random.Random.nextInt(1, 7)
                        GameSoundFeedback.roll(context, diceStyleId = id)
                        if (motion) {
                            die.startRolling()
                            hint.text = "Rolling…"
                            die.postDelayed(settle, 460L)
                        } else settle.run()
                    }
                }
                control(controls, "Next face") {
                    die.removeCallbacks(settle)
                    rolling = false
                    face = face % 6 + 1
                    die.showOutcome(face, animate = false)
                    hint.text = "Face $face of 6"
                }
                cleanup = { die.removeCallbacks(settle); die.stopRolling() }
            }
            CosmeticCategory.CHARACTER_PACK -> {
                hint.text = "In-game Paw"
                val live = if (motion) LivePawArtwork(context, id) {
                    hint.text = "Live preview unavailable on this device"
                } else null
                stage.addView(live ?: artwork(context), FrameLayout.LayoutParams(-1, -1))
                live?.let {
                    control(controls, "Idle") { it.playMotion(CharacterMotion.IDLE) }
                    control(controls, "Hop") { it.playMotion(CharacterMotion.HOP) }
                    control(controls, "Home") { it.playMotion(CharacterMotion.HOME) }
                }
                val voice = LudoPawsVoicePlayer(context)
                if (settings.animalVoicesEnabled) control(controls, "Sound") { voice.playPreview(id) }
                cleanup = { voice.shutdown() }
            }
            CosmeticCategory.AVATAR -> return
        }
        PlywoodAlertDialogBuilder(context)
            .setTitle(title)
            .enableHardwareAcceleration()
            .setView(content)
            .setOnDismissListener { cleanup() }
            .apply {
                if (actionLabel != null && onAction != null) {
                    setPositiveButton(actionLabel) { dialog, _ ->
                        dialog.dismiss()
                        onAction()
                    }
                }
            }.show()
    }

    private fun diceView(context: Context) = DiceView(context).apply {
        setPreviewStyle(this@CosmeticPreview.id)
        setPlayerTeamColor(null)
        showOutcome(DiceRegistry.resolve(this@CosmeticPreview.id).previewFace, animate = false)
    }
}

private fun animationsAllowed(context: Context) =
    !GameSettingsStore(context).snapshot().reducedMotion &&
        android.animation.ValueAnimator.areAnimatorsEnabled()

private class LivePawArtwork(
    context: Context,
    characterKey: String,
    unavailable: () -> Unit = {},
) : FrameLayout(context) {
    private val portrait = LudoPawsCharacterAvatarView(context).apply {
        bind(characterKey, showCircularChrome = false)
    }
    private val live = FeaturedPawsPreview(context, frameIntervalMillis = 66L, playful = true).apply {
        characterId = characterKey
        alpha = 0f
        onReady = {
            alpha = 1f
            portrait.visibility = View.INVISIBLE
        }
        onUnavailable = {
            alpha = 0f
            portrait.visibility = View.VISIBLE
            unavailable()
        }
    }

    init {
        addView(portrait, LayoutParams(-1, -1))
        addView(live, LayoutParams(-1, -1))
    }

    fun playMotion(motion: CharacterMotion) = live.playMotion(motion)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        live.setActive(true)
    }

    override fun onDetachedFromWindow() {
        live.setActive(false)
        super.onDetachedFromWindow()
    }
}

/** Presentation-only snapshots, owned by a single dialog. */
private class BoardPreviewDemo(private val board: LudoPawsReactiveBoardView) {
    private val paw = "preview-paw"
    private val opponent = "preview-opponent"
    private val characterId = CharacterRegistry.resolve(
        CharacterSelectionStore(board.context).load().characterId,
    ).id
    private val opponentCharacterId = if (characterId == "rabbit") "dog" else "rabbit"
    private var matchId = ""
    private var progress = -1
    private var step = 0
    private var nextMoveAt = 0L

    init {
        board.onTokenSelected = { if (it == 0) move() }
        reset()
    }

    fun reset() {
        matchId = "preview-${java.util.UUID.randomUUID()}"
        progress = -1
        step = 0
        nextMoveAt = 0
        bind()
    }

    fun move() {
        val now = SystemClock.uptimeMillis()
        if (now < nextMoveAt) return
        if (progress >= 15) {
            reset()
            return
        }
        val from = progress
        progress = if (from < 0) 0 else from + 3
        step++
        val speed = GameSettingsStore(board.context).snapshot().gameSpeed
        val visualSteps = LudoPathEncoding.visualStepCount(from, progress).coerceAtLeast(1)
        nextMoveAt = now + LudoPawsGameplayPacingPolicy.forwardAnimationDurationMillis(speed, visualSteps)
        bind()
    }

    private fun bind() {
        board.bind(
            state = MatchSnapshot(
                matchId = matchId,
                status = "ACTIVE",
                hostPlayerId = paw,
                targetPlayerCount = 2,
                matchMode = "PASS_AND_PLAY",
                players = listOf(
                    PlayerSnapshot(paw, "Practice Paw", "RED", 0, listOf(progress, -1, -1, -1), characterId = characterId),
                    PlayerSnapshot(opponent, "Practice Friend", "YELLOW", 1, listOf(5, -1, -1, -1), characterId = opponentCharacterId),
                ),
                turnSeat = 0,
                randomEventIndex = step + 1,
                pendingRoll = PendingRollSnapshot(
                    status = "RESOLVED", seat = 0, eventIndex = step + 1,
                    eventId = null, roundId = null, serverCommitment = null,
                    clientCommitment = null, revealDeadlineAt = null, proofDigest = null,
                    outcome = if (progress < 0) 6 else 3, legalTokenIndexes = setOf(0),
                ),
                winnerPlayerId = null,
                rulesetId = "ludoproof-standard-v7",
                history = if (step == 0) emptyList() else listOf(
                    HistoryEventSnapshot(step, paw, null, null, if (step == 1) 6 else 3, 0, 0),
                ),
                revision = step.toLong(),
            ),
            playerId = paw,
            perspectiveColor = "RED",
            characterIdsBySeat = listOf(characterId, opponentCharacterId),
        )
    }
}
