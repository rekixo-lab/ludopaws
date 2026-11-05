package com.rekixo.ludopaws.feature.store.presentation.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import com.rekixo.ludopaws.DiceView
import com.rekixo.ludopaws.LudoBoardView
import com.rekixo.ludopaws.LudoPaws3DSceneView
import com.rekixo.ludopaws.LudoPawsBoardChromeView
import com.rekixo.ludopaws.MatchSnapshot
import com.rekixo.ludopaws.PlayerSnapshot
import com.rekixo.ludopaws.feature.dice.domain.DiceRegistry
import com.rekixo.ludopaws.feature.dice.rendering.DiceRendererRegistry
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore

internal object StoreCatalogArtwork {
    fun paw(context: Context, characterId: String): View {
        val host = CatalogArtworkHost(context, ArtworkShape.PAW)
        var live: FeaturedPawsPreview? = null
        host.onActiveChanged = { active ->
            if (active) {
                val pawn = FeaturedPawsPreview(
                    context,
                    frameIntervalMillis = 50L,
                    playful = true,
                    phaseOffsetMillis = stagger(characterId, 1800L),
                )
                live = pawn
                pawn.characterId = characterId
                pawn.onReady = {
                    if (live === pawn) host.reveal(pawn)
                }
                pawn.onUnavailable = {
                    if (live === pawn) {
                        live = null
                        host.showUnavailable()
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
        val host = CatalogArtworkHost(context, ArtworkShape.BOARD)
        var live: FrameLayout? = null
        host.onActiveChanged = { active ->
            if (active) {
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
                val scene = LudoPaws3DSceneView(context, catalogArtwork = true).apply {
                    setBoardThemeOverride(boardId)
                    bind(boardDisplayState, playerId = null, perspectiveColor = null,
                        characterIdsBySeat = boardCharacters)
                }
                val board = FrameLayout(context).apply {
                    addView(base, FrameLayout.LayoutParams(-1, -1))
                    addView(chrome, FrameLayout.LayoutParams(-1, -1))
                    addView(scene, FrameLayout.LayoutParams(-1, -1))
                }
                live = board
                var sceneReady = false
                fun revealWhenReady() {
                    if (live === board && chrome.artworkReady && sceneReady) host.reveal(board)
                }
                chrome.onArtworkReadyChanged = { ready ->
                    if (live === board && ready && !chrome.hasArtwork) {
                        live = null
                        host.showUnavailable()
                    } else if (ready) {
                        revealWhenReady()
                    } else {
                        host.cover(board)
                    }
                }
                scene.onOperationalChanged = { ready ->
                    sceneReady = ready
                    if (ready) revealWhenReady() else host.cover(board)
                }
                scene.onStartupFailure = {
                    if (live === board) {
                        live = null
                        host.showUnavailable()
                    }
                }
                host.prepare(board)
            } else {
                live = null
                host.clearArtwork()
            }
        }
        return host
    }

    fun dice(context: Context, diceId: String): View {
        val definition = DiceRegistry.resolve(diceId)
        val host = CatalogArtworkHost(context, ArtworkShape.DICE, needsHardware = false)
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
        host.onActiveChanged = { active ->
            generation++
            if (active) {
                if (die != null) {
                    if (host.motionAllowed) roll.run()
                } else {
                    val requested = generation
                    val resources = context.resources
                    preparation = dicePreparation.submit {
                        val renderer = runCatching { DiceRendererRegistry.create(definition, resources) }
                        mainHandler.post {
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
                                    if (host.motionAllowed) roll.run()
                                }.onFailure {
                                    android.util.Log.w("ShopArtwork", "Dice artwork unavailable for $diceId", it)
                                    host.showUnavailable()
                                }
                            }
                        }
                    }
                }
            } else {
                preparation?.cancel(false)
                preparation = null
                host.removeCallbacks(roll)
                host.removeCallbacks(settle)
                die?.showOutcome(face, animate = false)
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

private enum class ArtworkShape { PAW, BOARD, DICE }

/** A quiet silhouette, replaced only when the selected item's artwork is ready. */
private class ArtworkSkeleton(context: Context, private val shape: ArtworkShape) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2E6D4C34 }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        val saved = canvas.save()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        canvas.scale(size / 100f, size / 100f)
        when (shape) {
            ArtworkShape.PAW -> {
                canvas.drawOval(30f, 43f, 70f, 85f, paint)
                canvas.drawOval(22f, 20f, 78f, 59f, paint)
                canvas.drawOval(20f, 13f, 37f, 39f, paint)
                canvas.drawOval(63f, 13f, 80f, 39f, paint)
            }
            ArtworkShape.BOARD -> canvas.drawRoundRect(4f, 4f, 96f, 96f, 9f, 9f, paint)
            ArtworkShape.DICE -> canvas.drawRoundRect(13f, 13f, 87f, 87f, 18f, 18f, paint)
        }
        canvas.restoreToCount(saved)
    }
}

private class CatalogArtworkHost(
    context: Context,
    shape: ArtworkShape,
    private val needsHardware: Boolean = true,
) : FrameLayout(context) {
    private val visible = Rect()
    private val settings = GameSettingsStore(context)
    private var reducedMotion = settings.snapshot().reducedMotion
    private val skeleton = ArtworkSkeleton(context, shape).apply {
        // Cover incomplete board layers while keeping their GL surface drawable.
        setBackgroundColor(0xFFF0D8B5.toInt())
    }
    private var artwork: View? = null
    private var observer: ViewTreeObserver? = null
    private var active = false
    private var startPosted = false
    var onActiveChanged: (Boolean) -> Unit = {}
    val motionAllowed: Boolean get() = ValueAnimator.areAnimatorsEnabled() && !reducedMotion
    private val start = Runnable {
        startPosted = false
        if (eligible()) setArtworkActive(true)
    }
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { reconcile() }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { reconcile() }

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(skeleton, LayoutParams(-1, -1))
    }

    fun prepare(view: View) {
        clearArtwork()
        artwork = view
        addView(view, 0, LayoutParams(-1, -1))
    }

    fun reveal(view: View) {
        if (artwork === view) skeleton.visibility = View.INVISIBLE
    }

    fun cover(view: View) {
        if (artwork === view) skeleton.visibility = View.VISIBLE
    }

    fun clearArtwork() {
        artwork?.let { removeView(it) }
        artwork = null
        skeleton.visibility = View.VISIBLE
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

    private fun reconcile() {
        if (!eligible()) {
            cancelStart()
            setArtworkActive(false)
        } else if (!active && !startPosted) {
            // Start on the next UI turn, without postponing on later layout events.
            startPosted = true
            post(start)
        }
    }

    private fun setArtworkActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value && needsHardware && !isHardwareAccelerated) showUnavailable()
        else onActiveChanged(value)
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
        cancelStart()
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
