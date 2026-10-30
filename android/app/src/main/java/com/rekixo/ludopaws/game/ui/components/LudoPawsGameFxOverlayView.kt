package com.rekixo.ludopaws

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsFxPolicy
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsReaction
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import kotlin.math.max
import kotlin.math.min

/**
 * Phase 10 presentation-only board FX layer.
 *
 * It visualizes already-committed snapshot transitions and reaction cues. It
 * never mutates gameplay state, proof state, persistence, or network state.
 */
internal class LudoPawsGameFxOverlayView(
    context: Context,
) : View(context) {
    private data class TokenTransition(
        val playerId: String,
        val seat: Int,
        val tokenIndex: Int,
        val color: String,
        val fromPosition: Int,
        val toPosition: Int,
    ) {
        val isCaptureReturn: Boolean =
            fromPosition >= 0 && toPosition == -1
        val isHomeArrival: Boolean =
            fromPosition != 57 && toPosition == 57
    }

    private val settingsStore = GameSettingsStore(context)
    private var snapshot: MatchSnapshot? = null
    private var perspectiveColor: String? = null
    private var reactions: List<LudoPawsReaction> = emptyList()
    private var transitions: List<TokenTransition> = emptyList()
    private var captureTransitions: List<TokenTransition> = emptyList()
    private var forwardMotion: LudoPawsPawnMotion? = null
    private var reactionProgress = 0f
    private var transitionProgress = 1f
    private var captureProgress = -1f
    private var captureContactDelayMillis = 0L
    private var captureDurationMillis = 0L
    private var reactionAnimator: ValueAnimator? = null
    private var transitionAnimator: ValueAnimator? = null
    private var captureAnimator: ValueAnimator? = null
    private val painter = LudoPawsFxPainter(context)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bind(
        previous: MatchSnapshot?,
        current: MatchSnapshot?,
        perspectiveColor: String?,
        movementElapsedMillis: Long = 0L,
    ) {
        snapshot = current
        this.perspectiveColor =
            perspectiveColor?.takeIf {
                it in OfflinePlayerLayout.COLORS
            }

        val detected = detectTransitions(previous, current)
        transitions = detected.filterNot(TokenTransition::isCaptureReturn)
        captureTransitions = detected.filter(TokenTransition::isCaptureReturn)

        val settings = settingsStore.snapshot()
        val motions =
            LudoPawsPawnAnimationPolicy.plans(
                previous = previous,
                current = current,
            )
        forwardMotion =
            motions.firstOrNull {
                it.kind == LudoPawsPawnMotionKind.FORWARD
            }
        val movementDurationMillis =
            forwardMotion
                ?.let { motion ->
                    LudoPawsGameplayPacingPolicy.forwardTravelDurationMillis(
                        speed = settings.gameSpeed,
                        visualSteps = motion.visualSteps,
                    )
                }
                ?: 0L

        // Keep the overlay on the same move clock as the 3D pawn. When a
        // network reply confirms an in-flight preview, resume at elapsed progress
        // rather than restarting the trail/capture visuals from frame zero.
        val elapsedMovementMillis =
            if (forwardMotion != null) {
                movementElapsedMillis.coerceIn(0L, movementDurationMillis)
            } else {
                0L
            }
        captureContactDelayMillis =
            (movementDurationMillis - elapsedMovementMillis).coerceAtLeast(0L)
        captureDurationMillis =
            if (captureTransitions.isNotEmpty()) {
                LudoPawsGameplayPacingPolicy
                    .captureReturnDurationMillis(settings.gameSpeed)
            } else {
                0L
            }

        startTransitionAnimationIfNeeded(
            durationMillis = movementDurationMillis
                .takeIf { forwardMotion != null }
                ?: TRANSITION_DURATION_MS,
            elapsedMillis = elapsedMovementMillis,
        )
        startCaptureAnimationIfNeeded()
        invalidate()
    }

    fun play(
        reactions: List<LudoPawsReaction>,
    ) {
        reactionAnimator?.cancel()
        this.reactions = reactions.take(MAX_SIMULTANEOUS_REACTIONS)

        if (this.reactions.isEmpty()) {
            reactionProgress = 0f
            invalidate()
            return
        }

        val duration =
            this.reactions.maxOf {
                LudoPawsFxPolicy
                    .plan(
                        cue = it.animationCue,
                    )
                    .durationMs
            }

        // Any reaction produced by a committed pawn move is destination based in
        // the current snapshot. Delay it until the forward route is finished so
        // the UI never previews the square where the pawn will stop.
        val waitForArrival = forwardMotion != null
        val startDelay =
            if (waitForArrival) {
                captureContactDelayMillis
            } else {
                0L
            }
        reactionProgress = if (startDelay > 0L) -1f else 0f

        reactionAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                this.startDelay = startDelay
                addUpdateListener {
                    reactionProgress = it.animatedValue as Float
                    invalidate()
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            finishReactionAnimation()
                        }

                        override fun onAnimationCancel(animation: Animator) {
                            finishReactionAnimation()
                        }
                    },
                )
                start()
            }
    }

    fun stop() {
        reactionAnimator?.cancel()
        transitionAnimator?.cancel()
        captureAnimator?.cancel()
        reactionAnimator = null
        transitionAnimator = null
        captureAnimator = null
        reactions = emptyList()
        transitions = emptyList()
        captureTransitions = emptyList()
        forwardMotion = null
        reactionProgress = 0f
        transitionProgress = 1f
        captureProgress = -1f
        captureContactDelayMillis = 0L
        captureDurationMillis = 0L
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = snapshot ?: return
        val size = min(width, height).toFloat()
        if (size <= 0f) {
            return
        }
        val cell = size / LudoPawsFxBoardGeometry.BOARD_SIZE
        val turns =
            perspectiveColor
                ?.let(OfflinePlayerLayout::rotationQuarterTurns)
                ?: 0

        if (turns != 0) {
            canvas.save()
            canvas.rotate(
                turns * 90f,
                size / 2f,
                size / 2f,
            )
        }

        drawTransitions(canvas, cell)
        if (reactionProgress >= 0f) {
            reactions.forEach { reaction ->
                drawReaction(
                    canvas = canvas,
                    state = current,
                    cell = cell,
                    reaction = reaction,
                )
            }
        }

        if (turns != 0) {
            canvas.restore()
        }
    }

    private fun startTransitionAnimationIfNeeded(
        durationMillis: Long,
        elapsedMillis: Long = 0L,
    ) {
        transitionAnimator?.cancel()
        if (transitions.isEmpty()) {
            transitionProgress = 1f
            transitionAnimator = null
            return
        }

        val totalDurationMillis = durationMillis.coerceAtLeast(1L)
        val elapsed = elapsedMillis.coerceIn(0L, totalDurationMillis)
        if (elapsed >= totalDurationMillis) {
            transitionProgress = 1f
            transitionAnimator = null
            invalidate()
            return
        }

        transitionProgress = 0f
        transitionAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = totalDurationMillis
                addUpdateListener {
                    transitionProgress = it.animatedValue as Float
                    invalidate()
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            transitionProgress = 1f
                            transitionAnimator = null
                            invalidate()
                        }

                        override fun onAnimationCancel(animation: Animator) {
                            transitionAnimator = null
                        }
                    },
                )
                start()
                if (elapsed > 0L) {
                    setCurrentPlayTime(elapsed)
                }
            }
    }

    private fun startCaptureAnimationIfNeeded() {
        captureAnimator?.cancel()
        if (captureTransitions.isEmpty()) {
            captureProgress = -1f
            captureAnimator = null
            return
        }

        // Negative progress means "not visible yet". This prevents even the
        // captured ghost/paw flash from appearing before physical contact.
        captureProgress = -1f
        captureAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                startDelay = captureContactDelayMillis
                duration = captureDurationMillis.coerceAtLeast(1L)
                addUpdateListener {
                    captureProgress = it.animatedValue as Float
                    invalidate()
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            captureProgress = 1f
                            captureAnimator = null
                            invalidate()
                        }

                        override fun onAnimationCancel(animation: Animator) {
                            captureAnimator = null
                        }
                    },
                )
                start()
            }
    }

    private fun detectTransitions(
        previous: MatchSnapshot?,
        current: MatchSnapshot?,
    ): List<TokenTransition> {
        if (
            previous == null ||
            current == null ||
            previous.matchId != current.matchId
        ) {
            return emptyList()
        }

        val previousById =
            previous.players.associateBy(PlayerSnapshot::playerId)
        return buildList {
            current.players.forEach { player ->
                val before =
                    previousById[player.playerId]
                        ?: return@forEach
                player.tokens.forEachIndexed { tokenIndex, toPosition ->
                    val fromPosition =
                        before.tokens.getOrNull(tokenIndex)
                            ?: return@forEachIndexed
                    if (fromPosition != toPosition) {
                        add(
                            TokenTransition(
                                playerId = player.playerId,
                                seat = player.seat,
                                tokenIndex = tokenIndex,
                                color = player.color,
                                fromPosition = fromPosition,
                                toPosition = toPosition,
                            ),
                        )
                    }
                }
            }
        }.take(MAX_TRANSITIONS)
    }

    private fun drawTransitions(
        canvas: Canvas,
        cell: Float,
    ) {
        transitions.forEach { transition ->
            drawTransition(
                canvas = canvas,
                transition = transition,
                cell = cell,
                progress = transitionProgress,
            )
        }

        if (captureProgress >= 0f) {
            captureTransitions.forEach { transition ->
                drawTransition(
                    canvas = canvas,
                    transition = transition,
                    cell = cell,
                    progress = captureProgress,
                )
            }
        }

        if (transitions.any(TokenTransition::isHomeArrival)) {
            painter.drawHomeStars(
                canvas = canvas,
                cell = cell,
                progress = transitionProgress,
            )
        }
    }

    private fun drawTransition(
        canvas: Canvas,
        transition: TokenTransition,
        cell: Float,
        progress: Float,
    ) {
        val from =
            LudoPawsFxBoardGeometry.tokenCenter(
                color = transition.color,
                tokenIndex = transition.tokenIndex,
                position = transition.fromPosition,
                cell = cell,
                perspectiveQuarterTurns = perspectiveColor?.let(OfflinePlayerLayout::rotationQuarterTurns) ?: 0,
            )
        val to =
            LudoPawsFxBoardGeometry.tokenCenter(
                color = transition.color,
                tokenIndex = transition.tokenIndex,
                position = transition.toPosition,
                cell = cell,
                perspectiveQuarterTurns = perspectiveColor?.let(OfflinePlayerLayout::rotationQuarterTurns) ?: 0,
            )
        if (from == null || to == null) {
            return
        }

        val color = playerColor(transition.color)
        when {
            transition.isCaptureReturn ->
                painter.drawCaptureReturn(
                    canvas = canvas,
                    from = from,
                    to = to,
                    color = color,
                    cell = cell,
                    progress = progress,
                )

            transition.isHomeArrival ->
                painter.drawPawTrail(
                    canvas = canvas,
                    from = from,
                    to = to,
                    color = color,
                    cell = cell,
                    progress = progress,
                    emphasis = 1f,
                )

            else ->
                painter.drawPawTrail(
                    canvas = canvas,
                    from = from,
                    to = to,
                    color = color,
                    cell = cell,
                    progress = progress,
                    emphasis = .55f,
                )
        }
    }

    private fun drawReaction(
        canvas: Canvas,
        state: MatchSnapshot,
        cell: Float,
        reaction: LudoPawsReaction,
    ) {
        val player =
            state.players.firstOrNull {
                it.playerId == reaction.playerId
            } ?: return
        val tokenIndex =
            reaction.tokenIndex
                ?: representativeTokenIndex(player)
        val position =
            player.tokens.getOrNull(tokenIndex)
                ?: return
        val center =
            LudoPawsFxBoardGeometry.tokenCenter(
                color = player.color,
                tokenIndex = tokenIndex,
                position = position,
                cell = cell,
                perspectiveQuarterTurns = perspectiveColor?.let(OfflinePlayerLayout::rotationQuarterTurns) ?: 0,
            ) ?: return
        if (settingsStore.snapshot().reducedMotion) return
        val plan =
            LudoPawsFxPolicy.plan(
                cue = reaction.animationCue,
            )

        painter.drawReaction(
            canvas = canvas,
            center = center,
            cue = reaction.animationCue,
            cell = cell,
            progress = reactionProgress,
            particleCount = if (settingsStore.snapshot().reducedMotion) 0 else plan.particleCount,
            allowConfetti = plan.allowConfetti && !settingsStore.snapshot().reducedMotion,
        )
    }

    private fun representativeTokenIndex(player: PlayerSnapshot): Int =
        player.tokens
            .indexOfFirst { it >= 0 }
            .takeIf { it >= 0 }
            ?: 0

    private fun playerColor(color: String): Int =
        when (color) {
            "RED" -> Color.rgb(229, 57, 53)
            "GREEN" -> Color.rgb(46, 160, 67)
            "YELLOW" -> Color.rgb(255, 193, 7)
            "BLUE" -> Color.rgb(30, 136, 229)
            else -> Color.WHITE
        }

    private fun finishReactionAnimation() {
        reactionAnimator = null
        reactions = emptyList()
        reactionProgress = 0f
        invalidate()
    }

    private companion object {
        const val MAX_SIMULTANEOUS_REACTIONS = 6
        const val MAX_TRANSITIONS = 8
        const val TRANSITION_DURATION_MS = 720L
        const val REDUCED_CAPTURE_FLASH_DURATION_MS = 120L
    }
}
