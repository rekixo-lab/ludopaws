package com.rekixo.ludopaws.feature.store.presentation.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import com.rekixo.ludopaws.DiceView
import com.rekixo.ludopaws.LudoBoardView
import com.rekixo.ludopaws.LudoPaws3DSceneView
import com.rekixo.ludopaws.LudoPawsBoardChromeView
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.MatchSnapshot
import com.rekixo.ludopaws.PlayerSnapshot
import com.rekixo.ludopaws.feature.dice.domain.DiceRegistry
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore

internal object StoreCatalogArtwork {
    fun paw(context: Context, characterId: String): View {
        val portrait = LudoPawsCharacterAvatarView(context).apply {
            bind(characterId, showCircularChrome = false)
        }
        val host = CatalogArtworkHost(context, portrait, characterId)
        var live: FeaturedPawsPreview? = null
        var failed = false
        host.onActiveChanged = { active ->
            if (active && !failed) {
                val pawn = FeaturedPawsPreview(
                    context,
                    frameIntervalMillis = 50L,
                    playful = true,
                    phaseOffsetMillis = stagger(characterId, 1800L),
                )
                live = pawn
                pawn.characterId = characterId
                pawn.alpha = 0f
                pawn.onReady = {
                    if (live === pawn) {
                        pawn.alpha = 1f
                        portrait.visibility = View.INVISIBLE
                    }
                }
                pawn.onUnavailable = {
                    if (live === pawn) {
                        failed = true
                        live = null
                        portrait.visibility = View.VISIBLE
                        host.removeView(pawn)
                    }
                }
                host.addView(pawn, FrameLayout.LayoutParams(-1, -1))
                pawn.setActive(true)
            } else {
                val pawn = live
                live = null
                portrait.visibility = View.VISIBLE
                if (pawn != null) {
                    pawn.setActive(false)
                    host.removeView(pawn)
                }
            }
        }
        return host
    }

    fun board(context: Context, boardId: String): View {
        val base = LudoBoardView(context).apply {
            setBoardThemeOverride(boardId)
            setClassicTokenDrawingEnabled(false)
            setLudoPawsYardFootprintsEnabled(true)
            bind(boardDisplayState, playerId = null, characterIdsBySeat = boardCharacters)
        }
        val chrome = LudoPawsBoardChromeView(context).apply {
            setBoardThemeOverride(boardId)
            setFootprintSource(base)
        }
        val board = FrameLayout(context).apply {
            addView(base, FrameLayout.LayoutParams(-1, -1))
            addView(chrome, FrameLayout.LayoutParams(-1, -1))
        }
        val host = CatalogArtworkHost(context, board, boardId, renderWithReducedMotion = true)
        var live: LudoPaws3DSceneView? = null
        var failed = false
        host.onActiveChanged = { active ->
            if (active && !failed) {
                val scene = LudoPaws3DSceneView(context, catalogArtwork = true).apply {
                    setBoardThemeOverride(boardId)
                    bind(boardDisplayState, playerId = null, perspectiveColor = null,
                        characterIdsBySeat = boardCharacters)
                }
                live = scene
                scene.onStartupFailure = {
                    if (live === scene) {
                        failed = true
                        live = null
                        board.removeView(scene)
                    }
                }
                board.addView(scene, FrameLayout.LayoutParams(-1, -1))
            } else {
                live?.let { board.removeView(it) }
                live = null
            }
        }
        return host
    }

    fun dice(context: Context, diceId: String): View {
        var face = DiceRegistry.resolve(diceId).previewFace
        val dice = DiceView(context).apply {
            setPreviewStyle(diceId)
            setPlayerTeamColor(null)
            showOutcome(face, animate = false)
        }
        val host = CatalogArtworkHost(context, dice, diceId)
        val settle = Runnable {
            face = face % 6 + 1
            dice.showOutcome(face, animate = true)
        }
        val roll = object : Runnable {
            override fun run() {
                dice.startRolling()
                host.postDelayed(settle, 380L)
                host.postDelayed(this, 5100L + stagger(diceId, 1400L))
            }
        }
        host.onActiveChanged = { active ->
            if (active) {
                host.postDelayed(roll, 500L + stagger(diceId, 1400L))
            } else {
                host.removeCallbacks(roll)
                host.removeCallbacks(settle)
                dice.showOutcome(face, animate = false)
            }
        }
        return host
    }

    private val boardCharacters = listOf("dog", "duck")
    // The match renderer receives an immutable display pose without a match session.
    private val boardDisplayState = MatchSnapshot(
        matchId = "shop-board-artwork",
        status = "ACTIVE",
        hostPlayerId = "shop-red",
        targetPlayerCount = 2,
        matchMode = "PASS_AND_PLAY",
        players = listOf(
            PlayerSnapshot("shop-red", "Red Paw", "RED", 0, listOf(3, -1, -1, -1), characterId = "dog"),
            PlayerSnapshot("shop-yellow", "Yellow Paw", "YELLOW", 1, listOf(7, -1, -1, -1), characterId = "duck"),
        ),
        turnSeat = 0,
        randomEventIndex = 0,
        pendingRoll = null,
        winnerPlayerId = null,
        rulesetId = "ludoproof-standard-v7",
        history = emptyList(),
    )

    private fun stagger(key: String, range: Long): Long = key.hashCode().toUInt().toLong() % range
}

/** Delay live allocation until a card has settled in the viewport. */
private class CatalogArtworkHost(
    context: Context,
    artwork: View,
    key: String,
    private val renderWithReducedMotion: Boolean = false,
) : FrameLayout(context) {
    private val visible = Rect()
    private val settings = GameSettingsStore(context)
    private var reducedMotion = settings.snapshot().reducedMotion
    private val startDelay = 120L + key.hashCode().toUInt().toLong() % 130L
    private var observer: ViewTreeObserver? = null
    private var active = false
    var onActiveChanged: (Boolean) -> Unit = {}
    private val start = Runnable { if (eligible()) setArtworkActive(true) }
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { reconcile() }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { reconcile() }

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(artwork, LayoutParams(-1, -1))
    }

    // Board and dice artwork must leave every tap to the surrounding purchase card.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false

    private fun eligible(): Boolean =
        isAttachedToWindow && isHardwareAccelerated && isShown && hasWindowFocus() && windowVisibility == View.VISIBLE &&
            width > 0 && height > 0 && getLocalVisibleRect(visible) &&
            visible.height() * 2 >= height && visible.width() * 2 >= width &&
            (renderWithReducedMotion || (ValueAnimator.areAnimatorsEnabled() && !reducedMotion))

    private fun reconcile() {
        removeCallbacks(start)
        if (!eligible()) {
            setArtworkActive(false)
        } else if (!active) {
            postDelayed(start, startDelay)
        }
    }

    private fun setArtworkActive(value: Boolean) {
        if (active == value) return
        active = value
        onActiveChanged(value)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reducedMotion = settings.snapshot().reducedMotion
        observer = viewTreeObserver.also {
            it.addOnScrollChangedListener(scrollListener)
            it.addOnGlobalLayoutListener(layoutListener)
        }
        reconcile()
    }

    override fun onDetachedFromWindow() {
        observer?.takeIf { it.isAlive }?.let {
            it.removeOnScrollChangedListener(scrollListener)
            it.removeOnGlobalLayoutListener(layoutListener)
        }
        observer = null
        removeCallbacks(start)
        setArtworkActive(false)
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) reducedMotion = settings.snapshot().reducedMotion
        if (isAttachedToWindow) reconcile()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) reconcile()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) reconcile()
    }
}
