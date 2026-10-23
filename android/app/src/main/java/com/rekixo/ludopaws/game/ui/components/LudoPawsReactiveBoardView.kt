package com.rekixo.ludopaws

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.Rect
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.widget.FrameLayout
import com.rekixo.ludopaws.feature.characters.data.audio.LudoPawsVoicePlayer
import com.rekixo.ludopaws.feature.characters.domain.reaction.GameMomentType
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsIdleReactionPolicy
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsReaction
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsReactionEngine
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Production Ludo Paws board shell.
 *
 * Character visuals are rendered by the shared 3D runtime. The proven board
 * remains authoritative for geometry/touch/game state, while 3D body language,
 * board FX and animal audio are presentation-only.
 */
class LudoPawsReactiveBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val board = LudoPawsBoardView(context)
    private var overflowParent: View? = null
    private var overflowDelegate: TouchDelegate? = null
    private var previousTouchDelegate: TouchDelegate? = null
    private val pawn3DScene = LudoPaws3DSceneView(context)
    private val legalPulse = LudoPaws3DLegalPulseView(context)
    private val gameFxOverlay = LudoPawsGameFxOverlayView(context)
    private val voicePlayer = LudoPawsVoicePlayer(context)

    private var previousSnapshot: MatchSnapshot? = null
    private var currentSnapshot: MatchSnapshot? = null
    private var currentCharacterIdsBySeat: List<String> = emptyList()
    private val captureContactDelaysByEvent = LinkedHashMap<String, Long>()
    private var meaningfulStateKey: String? = null
    private var lastMeaningfulChangeAtMillis: Long = monotonicMillis()
    private var lastIdleReactionKey: String? = null

    private val idleReactionRunnable =
        Runnable {
            playIdleReactionIfEligible()
        }

    var onTokenSelected: ((Int) -> Unit)?
        get() = board.onTokenSelected
        set(value) {
            board.onTokenSelected = value
        }

    init {
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_AUTO

        // The approved board itself remains square and unchanged. Only the
        // transparent 3D pawn surface is allowed to extend above this container
        // so a large animal on the top row is not clipped at the board edge.
        clipChildren = false
        clipToPadding = false

        addView(
            board,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )

        // Normal gameplay is 3D-first. Keep the legacy 2D pawn layer hidden from
        // the first frame so its circular pawn art cannot appear beside, ahead of,
        // or behind an animal while the GL surface is starting or moving.
        board.setClassicPawnFallbackVisible(false)

        // Brighten only the transparent 3D pawn layer, not the board artwork.
        // RGB scaling preserves the existing material palette, highlights,
        // shadows and alpha while making all four animals easier to read.
        val pawnBrightness =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter =
                    ColorMatrixColorFilter(
                        ColorMatrix().apply {
                            setScale(
                                PAWN_BRIGHTNESS_SCALE,
                                PAWN_BRIGHTNESS_SCALE,
                                PAWN_BRIGHTNESS_SCALE,
                                1f,
                            )
                        },
                    )
            }
        pawn3DScene.setLayerType(
            View.LAYER_TYPE_HARDWARE,
            pawnBrightness,
        )

        addView(
            pawn3DScene,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
        // Legal choices use a pawn-local shimmer/sparkle instead of any circular
        // follow halo. The cue marks only selectable pawns and never previews the
        // destination square.
        addView(
            legalPulse,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
        addView(
            gameFxOverlay,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )

        pawn3DScene.onOperationalChanged =
            { available ->
                if (available) {
                    board.setClassicPawnFallbackVisible(false)
                } else if (
                    isShown &&
                    windowVisibility == View.VISIBLE
                ) {
                    // Only expose the proven classic-pawn fallback when the visible
                    // 3D runtime actually becomes unavailable. Lifecycle teardown
                    // while the board is hidden must never flash legacy circles.
                    board.setClassicPawnFallbackVisible(true)
                }
            }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean = board.dispatchTouchEvent(event)

    override fun dispatchHoverEvent(event: MotionEvent): Boolean = board.dispatchHoverEvent(event)

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val host = parent as? View ?: return
        if (overflowParent === host && host.touchDelegate === overflowDelegate) return
        restoreOverflowDelegate()
        overflowParent = host
        previousTouchDelegate = host.touchDelegate
        val prior = previousTouchDelegate
        val delegate = object : TouchDelegate(Rect(), this) {
            private var tracking = false
            override fun onTouchEvent(event: MotionEvent): Boolean {
                val hostLocation = IntArray(2)
                val boardLocation = IntArray(2)
                host.getLocationOnScreen(hostLocation)
                board.getLocationOnScreen(boardLocation)
                val x = event.x + hostLocation[0] - boardLocation[0]
                val y = event.y + hostLocation[1] - boardLocation[1]
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    // Only claim real pawn overhangs, leaving all other controls to the parent.
                    tracking = (y < 0f || x < 0f || x > width || y > height) &&
                        board.hasPawnAt(x, y)
                }
                if (!tracking) return prior?.onTouchEvent(event) ?: false
                val translated = MotionEvent.obtain(event)
                translated.offsetLocation((hostLocation[0] - boardLocation[0]).toFloat(),
                    (hostLocation[1] - boardLocation[1]).toFloat())
                val handled = dispatchTouchEvent(translated)
                translated.recycle()
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL) tracking = false
                return handled
            }
        }
        overflowDelegate = delegate
        host.touchDelegate = delegate
    }

    private fun restoreOverflowDelegate() {
        overflowParent?.let { host ->
            if (host.touchDelegate === overflowDelegate) host.touchDelegate = previousTouchDelegate
        }
        overflowParent = null
        overflowDelegate = null
        previousTouchDelegate = null
    }

    fun bind(
        state: MatchSnapshot?,
        playerId: String?,
        perspectiveColor: String? = null,
        characterIdsBySeat: List<String> = emptyList(),
    ) {
        val nowMillis = monotonicMillis()
        val committedMovePreviewElapsedMillis =
            pawn3DScene.acceptedMovePreviewElapsedMillis(state)
                ?: 0L
        updateIdleClock(
            state = state,
            nowMillis = nowMillis,
        )
        currentSnapshot = state
        currentCharacterIdsBySeat = characterIdsBySeat.take(4)

        val previous = previousSnapshot
        if (previous?.matchId != state?.matchId) {
            captureContactDelaysByEvent.clear()
        }
        val derivedReactions =
            LudoPawsReactionEngine.derive(
                previous = previous,
                current = state,
            )
        recordCaptureContactDelays(
            previous = previous,
            current = state,
            derivedReactions = derivedReactions,
        )
        val reactions =
            LudoPawsReactionEngine.detect(
                previous = previous,
                current = state,
            )
        board.bind(
            state = state,
            playerId = playerId,
            perspectiveColor = perspectiveColor,
            characterIdsBySeat = characterIdsBySeat,
            alreadyPresentedMillis = committedMovePreviewElapsedMillis,
        )
        pawn3DScene.bind(
            state = state,
            playerId = playerId,
            perspectiveColor = perspectiveColor,
            characterIdsBySeat = characterIdsBySeat,
        )
        legalPulse.bind(
            state = state,
            playerId = playerId,
            perspectiveColor = perspectiveColor,
        )
        gameFxOverlay.bind(
            previous = previous,
            current = state,
            perspectiveColor = perspectiveColor,
            movementElapsedMillis = committedMovePreviewElapsedMillis,
        )
        previousSnapshot = state

        playReactions(
            reactions = reactions,
            characterIdsBySeat = characterIdsBySeat,
            captureContactDelayMillis = captureContactDelayFor(reactions),
        )
        scheduleIdleReaction(
            state = state,
            nowMillis = nowMillis,
        )
    }

    fun beginPendingMovePreview(
        state: MatchSnapshot,
        playerId: String,
        tokenIndex: Int,
    ): Boolean =
        pawn3DScene.beginPendingMovePreview(
            state = state,
            playerId = playerId,
            tokenIndex = tokenIndex,
        )

    fun cancelPendingMovePreview(authoritative: MatchSnapshot?) {
        pawn3DScene.cancelPendingMovePreview(authoritative)
    }

    /**
     * Returns the on-screen bounds of the measured square board artwork, not this
     * wrapper's possibly tall flexible viewport. Online rails must target this rect.
     */
    internal fun visibleBoardBoundsOnScreen(): Rect? {
        if (
            board.visibility != View.VISIBLE ||
            board.width <= 0 ||
            board.height <= 0
        ) {
            return null
        }
        val location = IntArray(2)
        board.getLocationOnScreen(location)
        return Rect(
            location[0],
            location[1],
            location[0] + board.width,
            location[1] + board.height,
        )
    }

    fun reloadStyle() {
        board.reloadStyle()
    }

    override fun onDetachedFromWindow() {
        restoreOverflowDelegate()
        removeCallbacks(idleReactionRunnable)
        gameFxOverlay.stop()
        voicePlayer.shutdown()
        previousSnapshot = null
        currentSnapshot = null
        currentCharacterIdsBySeat = emptyList()
        captureContactDelaysByEvent.clear()
        meaningfulStateKey = null
        lastIdleReactionKey = null
        super.onDetachedFromWindow()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        val screenWidth = resources.displayMetrics.widthPixels
        val compactScreen =
            screenWidth <= density(600f).roundToInt()

        // On phones the board is intentionally allowed to outgrow the centered
        // content column and use the physical screen width. The board stage and
        // its ancestors already disable child clipping, so this expands the
        // square equally left/right instead of stretching it. Tablet/expanded
        // layouts keep the existing constrained content width.
        val requestedWidth =
            if (compactScreen) {
                screenWidth
            } else {
                parentWidth
            }
        val resolvedHeight =
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                requestedWidth
            } else {
                parentHeight
            }
        val size =
            min(
                requestedWidth,
                resolvedHeight,
            ).coerceAtLeast(1)

        configurePawnTopOverflow(size)

        val exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        super.onMeasure(exact, exact)
    }

    private fun configurePawnTopOverflow(boardSize: Int) {
        val topOverflow =
            (boardSize * PAWN_TOP_OVERFLOW_FRACTION)
                .roundToInt()
                .coerceAtLeast(0)
        val params = pawn3DScene.layoutParams as LayoutParams
        val targetHeight = boardSize + topOverflow
        if (
            params.height == targetHeight &&
            params.topMargin == -topOverflow
        ) {
            return
        }

        // Negative top margin + matching extra height keeps the bottom edge and
        // every board-space pawn coordinate exactly where they were. The added
        // pixels exist only above the board for heads/ears/horns to render into.
        params.width = LayoutParams.MATCH_PARENT
        params.height = targetHeight
        params.topMargin = -topOverflow
        pawn3DScene.layoutParams = params
    }

    private fun updateIdleClock(
        state: MatchSnapshot?,
        nowMillis: Long,
    ) {
        val nextKey =
            LudoPawsIdleReactionPolicy
                .meaningfulStateKey(state)

        if (
            state == null ||
            state.status != "ACTIVE"
        ) {
            removeCallbacks(idleReactionRunnable)
            meaningfulStateKey = nextKey
            lastMeaningfulChangeAtMillis = nowMillis
            lastIdleReactionKey = null
            return
        }

        if (nextKey != meaningfulStateKey) {
            meaningfulStateKey = nextKey
            lastMeaningfulChangeAtMillis = nowMillis
            lastIdleReactionKey = null
        }
    }

    private fun scheduleIdleReaction(
        state: MatchSnapshot?,
        nowMillis: Long,
    ) {
        removeCallbacks(idleReactionRunnable)
        val delay =
            LudoPawsIdleReactionPolicy
                .delayUntilEligibleMillis(
                    state = state,
                    lastMeaningfulChangeAtMillis = lastMeaningfulChangeAtMillis,
                    nowMillis = nowMillis,
                )
                ?: return
        postDelayed(
            idleReactionRunnable,
            delay,
        )
    }

    private fun playIdleReactionIfEligible() {
        val state = currentSnapshot ?: return
        val nowMillis = monotonicMillis()
        val remaining =
            LudoPawsIdleReactionPolicy
                .delayUntilEligibleMillis(
                    state = state,
                    lastMeaningfulChangeAtMillis = lastMeaningfulChangeAtMillis,
                    nowMillis = nowMillis,
                )
                ?: return
        if (remaining > 0L) {
            postDelayed(
                idleReactionRunnable,
                remaining,
            )
            return
        }

        val idleKey =
            LudoPawsIdleReactionPolicy
                .idleReactionKey(state)
                ?: return
        if (idleKey == lastIdleReactionKey) return

        val reactions =
            LudoPawsReactionEngine
                .deriveIdle(
                    current = state,
                    nowMillis = nowMillis,
                    lastMeaningfulChangeAtMillis = lastMeaningfulChangeAtMillis,
                    thresholdMillis = LudoPawsIdleReactionPolicy.IDLE_THRESHOLD_MILLIS,
                )
        if (reactions.isEmpty()) return
        lastIdleReactionKey = idleKey

        playReactions(
            reactions = reactions,
            characterIdsBySeat = currentCharacterIdsBySeat,
        )
    }

    private fun playReactions(
        reactions: List<LudoPawsReaction>,
        characterIdsBySeat: List<String>,
        captureContactDelayMillis: Long = 0L,
    ) {
        if (reactions.isEmpty()) return

        // Body language is separate from overlay FX. The scene itself decides
        // how Dog/Goat/Duck/Cat anatomy should express each animation cue.
        pawn3DScene.playReactions(reactions)
        gameFxOverlay.play(
            reactions = reactions,
        )
        voicePlayer.playHighestPriority(
            reactions = reactions,
            characterIdsBySeat = characterIdsBySeat,
            captureContactDelayMillis = captureContactDelayMillis,
        )
    }

    private fun recordCaptureContactDelays(
        previous: MatchSnapshot?,
        current: MatchSnapshot?,
        derivedReactions: List<LudoPawsReaction>,
    ) {
        val capture =
            derivedReactions.firstOrNull {
                it.momentType == GameMomentType.CAPTURE_MADE
            } ?: return
        val motion =
            LudoPawsPawnAnimationPolicy
                .plans(
                    previous = previous,
                    current = current,
                )
                .firstOrNull {
                    it.kind == LudoPawsPawnMotionKind.FORWARD &&
                        it.playerId == capture.playerId &&
                        it.tokenIndex == capture.tokenIndex
                } ?: return
        val speed = GameSettingsStore(context).snapshot().gameSpeed
        val key = "${capture.matchId}:${capture.eventIndex}"
        val fullContactDelay =
            LudoPawsGameplayPacingPolicy.captureContactDelayMillis(
                speed = speed,
                visualSteps = motion.visualSteps,
            )
        val alreadyPresented = pawn3DScene
            .pendingMovePreviewElapsedMillis(capture.eventIndex)
            ?: 0L
        captureContactDelaysByEvent[key] =
            (fullContactDelay - alreadyPresented).coerceAtLeast(0L)
        while (captureContactDelaysByEvent.size > MAX_CAPTURE_DELAY_EVENTS) {
            val oldestKey = captureContactDelaysByEvent.keys.firstOrNull() ?: break
            captureContactDelaysByEvent.remove(oldestKey)
        }
    }

    private fun captureContactDelayFor(
        reactions: List<LudoPawsReaction>,
    ): Long {
        val capture =
            reactions.firstOrNull {
                it.momentType == GameMomentType.CAPTURE_MADE ||
                    it.momentType == GameMomentType.TOKEN_CAPTURED
            } ?: return 0L
        return captureContactDelaysByEvent[
            "${capture.matchId}:${capture.eventIndex}"
        ] ?: 0L
    }

    private fun monotonicMillis(): Long =
        System.nanoTime() / 1_000_000L

    private fun density(value: Float): Float =
        value * resources.displayMetrics.density

    private companion object {
        // About 1.8 board cells on a 15x15 Ludo board. This is transparent
        // presentation space only; the approved square board remains unchanged.
        const val PAWN_TOP_OVERFLOW_FRACTION = 0.12f
        const val PAWN_BRIGHTNESS_SCALE = 1.10f
        const val MAX_CAPTURE_DELAY_EVENTS = 24
    }
}
