package com.rekixo.ludopaws.feature.store.presentation.components

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.rekixo.ludopaws.DiceView
import com.rekixo.ludopaws.LudoBoardView
import com.rekixo.ludopaws.LudoPaws3DSceneState
import com.rekixo.ludopaws.LudoPawsBoardChromeView
import com.rekixo.ludopaws.LudoPawsBoardPalette
import com.rekixo.ludopaws.LudoPawsCharacterPortraitBitmapCache
import com.rekixo.ludopaws.MatchSnapshot
import com.rekixo.ludopaws.PlayerSnapshot
import com.rekixo.ludopaws.feature.boards.domain.BoardRegistry
import com.rekixo.ludopaws.feature.dice.domain.DiceRegistry
import com.rekixo.ludopaws.feature.dice.rendering.DiceRendererRegistry
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore

internal object StoreCatalogArtwork {
    fun paw(context: Context, characterId: String): View {
        val host = CatalogArtworkHost(context)
        var live: FeaturedPawsPreview? = null
        host.onScrollPauseChanged = { paused -> live?.setActive(!paused) }
        host.onActiveChanged = { active ->
            if (active) {
                val pawn = FeaturedPawsPreview(
                    context,
                    frameIntervalMillis = 50L,
                    playful = true,
                    fitFullBody = true,
                    phaseOffsetMillis = stagger(characterId, 1800L),
                )
                live = pawn
                pawn.characterId = characterId
                pawn.onReady = {
                    host.whenStill {
                        if (live === pawn) host.reveal(pawn)
                    }
                }
                pawn.onUnavailable = {
                    host.whenStill {
                        if (live === pawn) {
                            live = null
                            host.showUnavailable()
                        }
                    }
                }
                host.prepare(pawn)
                pawn.setActive(true)
            } else {
                live?.setActive(false)
                live = null
                host.clearArtwork()
            }
        }
        return host
    }

    fun board(context: Context, boardId: String): View {
        val host = CatalogArtworkHost(context, needsHardware = false)
        val definition = BoardRegistry.resolve(boardId)
        var live: FrameLayout? = null
        var complete = false
        host.onActiveChanged = { active ->
            if (active && live == null) {
                val base = LudoBoardView(context).apply {
                    setBoardThemeOverride(boardId)
                    setClassicTokenDrawingEnabled(false)
                    setLudoPawsYardFootprintsEnabled(true)
                    bind(boardDisplayState, playerId = null, characterIdsBySeat = boardCharacters)
                }
                val chrome = LudoPawsBoardChromeView(context, maxArtworkPixels = 256).apply {
                    setBoardThemeOverride(boardId)
                    setFootprintSource(base)
                }
                val scene = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY }
                val board = FrameLayout(context).apply {
                    addView(base, FrameLayout.LayoutParams(-1, -1))
                    addView(chrome, FrameLayout.LayoutParams(-1, -1))
                    addView(scene, FrameLayout.LayoutParams(-1, -1))
                }
                live = board
                var sceneReady = false
                fun unavailable() {
                    live = null
                    complete = false
                    host.showUnavailable()
                }
                fun revealWhenReady() {
                    if (live === board && chrome.hasArtwork && sceneReady) {
                        complete = true
                        host.reveal(board)
                    }
                }
                chrome.onArtworkReadyChanged = { ready ->
                    host.whenStill {
                        if (live === board) {
                            when {
                                !ready -> {
                                    complete = false
                                    host.cover(board)
                                }
                                !chrome.hasArtwork -> unavailable()
                                else -> revealWhenReady()
                            }
                        }
                    }
                }
                host.prepare(board)
                LudoPawsCharacterPortraitBitmapCache.requestShopBoardLayer(
                    LudoPaws3DSceneState(
                        snapshot = boardDisplayState,
                        stoneRoadOverlayEnabled = definition.stoneRoadOverlayEnabled,
                        classicRoadReliefEnabled = definition.classicRoadReliefEnabled,
                        monsoonEffectsEnabled = definition.monsoonEffectsEnabled,
                        monsoonAnimationEnabled = false,
                        characterIdsBySeat = boardCharacters,
                        reducedMotion = true,
                    ),
                ) { bitmap ->
                    host.whenStill {
                        if (live === board) {
                            if (bitmap == null) {
                                unavailable()
                            } else {
                                scene.setImageBitmap(bitmap)
                                sceneReady = true
                                revealWhenReady()
                            }
                        }
                    }
                }
            } else if (!active && !complete) {
                live = null
                host.clearArtwork()
            }
        }
        return host
    }

    fun dice(context: Context, diceId: String): View {
        val definition = DiceRegistry.resolve(diceId)
        val host = CatalogArtworkHost(context, needsHardware = false)
        var die: DiceView? = null
        var preparation: java.util.concurrent.Future<*>? = null
        var generation = 0
        var face = definition.previewFace
        val settle = Runnable {
            face = face % 6 + 1
            die?.showOutcome(face, animate = true)
        }
        val roll = object : Runnable {
            override fun run() {
                val current = die ?: return
                current.startRolling()
                host.postDelayed(settle, 380L)
                host.postDelayed(this, 5100L + stagger(diceId, 1400L))
            }
        }
        fun scheduleRoll() {
            if (die != null && host.motionAllowed) {
                host.postDelayed(roll, 350L + stagger(diceId, 1400L))
            }
        }
        fun stopRolling() {
            host.removeCallbacks(roll)
            host.removeCallbacks(settle)
            die?.showOutcome(face, animate = false)
        }
        host.onScrollPauseChanged = { paused ->
            if (paused) stopRolling() else scheduleRoll()
        }
        host.onActiveChanged = { active ->
            generation++
            if (active) {
                if (die != null) {
                    scheduleRoll()
                } else {
                    host.clearArtwork()
                    val requested = generation
                    val resources = context.resources
                    preparation = dicePreparation.submit {
                        val renderer = runCatching { DiceRendererRegistry.create(definition, resources) }
                        mainHandler.post {
                            if (requested != generation) return@post
                            host.whenStill {
                                if (requested == generation) {
                                    preparation = null
                                    renderer.onSuccess { ready ->
                                        val view = DiceView(context).apply {
                                            setPreparedPreviewStyle(diceId, ready)
                                            setPlayerTeamColor(null)
                                            showOutcome(face, animate = false)
                                        }
                                        die = view
                                        host.prepare(view)
                                        host.reveal(view)
                                        scheduleRoll()
                                    }.onFailure {
                                        android.util.Log.w("ShopArtwork", "Dice artwork unavailable for $diceId", it)
                                        host.showUnavailable()
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                preparation?.cancel(false)
                preparation = null
                stopRolling()
            }
        }
        return host
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val dicePreparation = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "LudoPawsShopDice").apply { isDaemon = true }
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

private class CatalogArtworkHost(
    context: Context,
    private val needsHardware: Boolean = true,
) : FrameLayout(context) {
    private val visible = Rect()
    private val settings = GameSettingsStore(context)
    private var reducedMotion = settings.snapshot().reducedMotion
    private val loading = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
        isIndeterminate = true
        indeterminateTintList = ColorStateList.valueOf(LudoPawsBoardPalette.woodShade)
        visibility = View.INVISIBLE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var artwork: View? = null
    private var scroll: StoreCatalogScrollView? = null
    private var standaloneObserver: ViewTreeObserver? = null
    private val deferredArtwork = ArrayDeque<() -> Unit>()
    private var active = false
    private var paused = false
    private var loadingRequested = false
    private var startPosted = false
    var onActiveChanged: (Boolean) -> Unit = {}
    var onScrollPauseChanged: (Boolean) -> Unit = {}
    val motionAllowed: Boolean get() = !paused && ValueAnimator.areAnimatorsEnabled() && !reducedMotion
    private val start = Runnable {
        startPosted = false
        if (!paused && eligible()) setArtworkActive(true)
    }
    private val scrollListener = { reconcile() }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { reconcile() }

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val indicatorSize = (18f * resources.displayMetrics.density + .5f).toInt()
        addView(loading, LayoutParams(indicatorSize, indicatorSize, Gravity.CENTER))
    }

    fun prepare(view: View) {
        clearArtwork()
        artwork = view
        view.alpha = 0f
        addView(view, 0, LayoutParams(-1, -1))
    }

    fun reveal(view: View) {
        if (artwork !== view) return
        view.alpha = 1f
        loadingRequested = false
        updateLoading()
    }

    fun cover(view: View) {
        if (artwork !== view) return
        view.alpha = 0f
        loadingRequested = true
        updateLoading()
    }

    fun clearArtwork() {
        artwork?.let { removeView(it) }
        artwork = null
        loadingRequested = true
        updateLoading()
    }

    fun showUnavailable() {
        prepare(TextView(context).apply {
            text = "Artwork unavailable"
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF65442F.toInt())
        })
        artwork?.let(::reveal)
    }

    // Artwork leaves taps to the surrounding purchase card.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean = false

    private fun eligible(): Boolean =
        isAttachedToWindow && isShown && hasWindowFocus() && windowVisibility == View.VISIBLE &&
            width > 0 && height > 0 && getLocalVisibleRect(visible) &&
            visible.height() * 2 >= height && visible.width() * 2 >= width

    private fun cancelStart() {
        if (startPosted) removeCallbacks(start)
        startPosted = false
    }

    fun whenStill(update: () -> Unit) {
        if (!isAttachedToWindow) return
        if (paused) deferredArtwork.addLast(update) else update()
    }

    private fun updateLoading() {
        loading.visibility = if (loadingRequested && active && !paused) View.VISIBLE else View.INVISIBLE
    }

    private fun reconcile() {
        val moving = scroll?.isMoving == true
        val pauseChanged = paused != moving
        paused = moving
        if (paused) {
            cancelStart()
            if (pauseChanged && active) {
                onScrollPauseChanged(true)
                updateLoading()
            }
            return
        }

        if (!eligible()) {
            cancelStart()
            setArtworkActive(false)
            deferredArtwork.clear()
        } else if (active) {
            if (pauseChanged) {
                onScrollPauseChanged(false)
                while (deferredArtwork.isNotEmpty()) deferredArtwork.removeFirst().invoke()
                updateLoading()
            }
        } else if (!startPosted) {
            startPosted = true
            post(start)
        }
    }

    private fun setArtworkActive(value: Boolean) {
        if (active == value) return
        active = value
        updateLoading()
        if (value && needsHardware && !isHardwareAccelerated) showUnavailable()
        else onActiveChanged(value)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reducedMotion = settings.snapshot().reducedMotion
        var ancestor = parent
        while (ancestor != null && ancestor !is StoreCatalogScrollView) ancestor = ancestor.parent
        scroll = ancestor as? StoreCatalogScrollView
        val catalog = scroll
        if (catalog != null) {
            catalog.addArtworkListener(scrollListener)
        } else {
            standaloneObserver = viewTreeObserver.also { it.addOnGlobalLayoutListener(layoutListener) }
        }
        reconcile()
    }

    override fun onDetachedFromWindow() {
        scroll?.removeArtworkListener(scrollListener)
        scroll = null
        standaloneObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        standaloneObserver = null
        cancelStart()
        setArtworkActive(false)
        paused = false
        deferredArtwork.clear()
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
