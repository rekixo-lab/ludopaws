package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.dice.domain.DiceRegistry
import com.rekixo.ludopaws.feature.dice.rendering.*
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory

class DiceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    init {
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription =
            "Dice. No verified outcome yet."
        // Avoid forcing software rasterization on every rolling animation frame.
        setLayerType(
            LAYER_TYPE_NONE,
            null,
        )
    }

    private val turnTimerTrackPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(4.4f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = 0xFF8A8A8A.toInt()
            alpha = 62
        }
    private val turnTimerProgressPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(4.4f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = 0xFF8A8A8A.toInt()
        }

    private var turnDeadlineEpochMillis: Long? = null
    private var turnDeadlineElapsedRealtimeMillis: Long? = null
    private var turnCountdownDurationMillis = DiceTurnCountdownPolicy.TURN_DURATION_MILLIS

    // Reuse all geometry used by the 15-second countdown stroke. Creating Paths
    // and PathMeasures on every frame causes avoidable allocation/GC pressure.
    private val turnTimerPath = Path()
    private val turnTimerProgressPath = Path()
    private val turnTimerPathMeasure = PathMeasure()
    private val turnTimerPathBounds = RectF()
    private var turnTimerPathSize = Float.NaN
    private var turnTimerPathRadius = Float.NaN
    private var turnTimerPathLength = 0f
    private var animationTickerScheduled = false

    private var diceStyleId =
        "dice_classic"
    private var rendererKey = "classic"
    private var renderer = DiceRendererRegistry.create(DiceRegistry.resolve("dice_classic"), resources.displayMetrics.density)
    private var previewStyleId: String? = null
    private var face = 1
    private var rolling = false
    private var settling = false
    private var rollStartedAtMillis = 0L
    private var settleStartedAtMillis = 0L
    private var settleStartRotationDegrees = 18f
    private var targetOutcome = 1
    private var rotationDegrees = 0f
    private var scale = 1f
    private var translationYFraction = 0f
    private var borderPulse = 0f
    private var playerTeamColor: Int? = null
    private var attentionPulsing = false
    private var attentionPhaseStartedAtMillis = 0L
    private var lastPresentedOutcomeEventKey: String? = null
    private var deferredSettleRunnable: Runnable? = null

    private val animationTicker =
        object : Runnable {
            override fun run() {
                // A delivered frame frees the gate before deciding whether another
                // frame is needed. Timer and attention updates share one VSYNC tick.
                animationTickerScheduled = false
                val now = SystemClock.uptimeMillis()
                when {
                    rolling -> {
                        val elapsed =
                            now - rollStartedAtMillis
                        applyFrame(
                            DiceRollAnimationPolicy
                                .rollingFrame(elapsed),
                        )
                    }

                    settling -> {
                        val frame =
                            DiceRollAnimationPolicy
                                .settleFrame(
                                    elapsedMillis =
                                        now - settleStartedAtMillis,
                                    outcome = targetOutcome,
                                    startRotationDegrees =
                                        settleStartRotationDegrees,
                                )
                        applyFrame(frame)
                        if (frame.finished) {
                            settling = false
                            resetTransform()
                        }
                    }

                    attentionPulsing -> {
                        val elapsed =
                            (now - attentionPhaseStartedAtMillis)
                                .coerceAtLeast(0L)
                        applyFrame(
                            DiceAttentionAnimationPolicy.frame(
                                elapsedMillis = elapsed,
                            ),
                        )
                    }

                    turnDeadlineElapsedRealtimeMillis != null -> Unit

                    else -> return
                }

                invalidate()
                // The attention cue is a real animation, not a one-shot callback.
                // Keep scheduling its frames while enabled, just like rolling and
                // settling, and let every phase share this single ticker.
                if (rolling || settling || attentionPulsing || turnCountdownActive()) {
                    scheduleAnimationTicker()
                }
            }
        }

    /**
     * Binds this dice's outline to the owning player's stable team color.
     * The same color is used by idle, attention-pulse, and rolling strokes.
     */
    fun setPlayerTeamColor(colorName: String?) {
        playerTeamColor =
            LudoPawsTeamIdentityPolicy
                .resolve(colorName)
                ?.argb
        invalidate()
    }

    /**
     * Binds countdown to the server's absolute epoch-millisecond deadline.
     * Repeated snapshots with the same deadline do not restart its progress.
     */
    fun setTurnDeadlineAt(
        deadlineEpochMillis: Long?,
        serverNowEpochMillis: Long? = null,
        timeoutDurationMillis: Long = DiceTurnCountdownPolicy.TURN_DURATION_MILLIS,
    ) {
        val safeDuration = timeoutDurationMillis.takeIf { it > 0L }
            ?: DiceTurnCountdownPolicy.TURN_DURATION_MILLIS
        if (
            turnDeadlineEpochMillis == deadlineEpochMillis &&
            turnCountdownDurationMillis == safeDuration
        ) return
        turnDeadlineEpochMillis = deadlineEpochMillis
        turnCountdownDurationMillis = safeDuration
        turnDeadlineElapsedRealtimeMillis =
            deadlineEpochMillis?.let { deadline ->
                val remaining = serverNowEpochMillis?.let { serverNow ->
                    deadline - serverNow
                } ?: (deadline - System.currentTimeMillis())
                SystemClock.elapsedRealtime() + remaining.coerceAtLeast(0L)
            }
        if (turnCountdownActive()) scheduleAnimationTicker()
        else if (!rolling && !settling && !attentionPulsing) cancelAnimationTicker()
        invalidate()
    }

    private fun turnCountdownActive(): Boolean =
        turnDeadlineElapsedRealtimeMillis?.let {
            SystemClock.elapsedRealtime() < it
        } == true

    /**
     * All countdown, attention and roll phases share one pending VSYNC callback.
     * Duplicate posts while a match activates can cause redundant frame work.
     */
    private fun scheduleAnimationTicker() {
        if (animationTickerScheduled) return
        animationTickerScheduled = true
        postOnAnimation(animationTicker)
    }

    private fun cancelAnimationTicker() {
        removeCallbacks(animationTicker)
        animationTickerScheduled = false
    }

    fun setAttentionEnabled(enabled: Boolean) {
        val shouldPulse =
            enabled &&
                !rolling &&
                !settling
        if (shouldPulse == attentionPulsing) {
            return
        }

        attentionPulsing = shouldPulse
        if (shouldPulse) {
            attentionPhaseStartedAtMillis = SystemClock.uptimeMillis()
            scheduleAnimationTicker()
        } else {
            if (!turnCountdownActive() && !rolling && !settling) {
                cancelAnimationTicker()
            }
            resetTransform()
            invalidate()
            if (turnCountdownActive()) scheduleAnimationTicker()
        }
    }

    fun startRolling() {
        if (rolling) return
        cancelDeferredSettle()
        attentionPulsing = false
        cancelAnimationTicker()
        rolling = true
        settling = false
        rollStartedAtMillis =
            SystemClock.uptimeMillis()
        contentDescription =
            "Dice verification in progress."
        scheduleAnimationTicker()
    }

    fun showOutcome(
        outcome: Int,
        animate: Boolean = true,
        eventKey: String? = null,
        minimumRollDurationMillis: Long = 0L,
    ) {
        if (outcome !in 1..6) return
        if (eventKey != null && eventKey == lastPresentedOutcomeEventKey && targetOutcome == outcome) {
            return
        }
        cancelDeferredSettle()
        attentionPulsing = false

        if (!animate) {
            rolling = false
            settling = false
            cancelAnimationTicker()
            targetOutcome = outcome
            face = outcome
            contentDescription = "Dice outcome $outcome."
            if (eventKey != null) lastPresentedOutcomeEventKey = eventKey
            resetTransform()
            invalidate()
            if (turnCountdownActive()) scheduleAnimationTicker()
            return
        }

        // Keep offline Robot / Pass & Play transitions intact.
        if (eventKey == null) {
            targetOutcome = outcome
            face = outcome
            contentDescription = "Dice outcome $outcome."
            beginSettle(outcome)
            return
        }

        // A remote poll can skip COMMITTED/RESOLVING snapshots. Show a real
        // spin for the verified event instead of flashing directly to a face.
        if (!rolling) startRolling()
        lastPresentedOutcomeEventKey = eventKey
        targetOutcome = outcome
        contentDescription = "Verified dice outcome $outcome; settling."

        val remainingSpinMillis = DiceRollAnimationPolicy.remainingVisibleRollMillis(
            elapsedMillis = SystemClock.uptimeMillis() - rollStartedAtMillis,
            minimumVisibleRollMillis = minimumRollDurationMillis,
        )
        if (remainingSpinMillis > 0L) {
            val expectedEventKey = eventKey
            deferredSettleRunnable = Runnable {
                deferredSettleRunnable = null
                if (lastPresentedOutcomeEventKey != expectedEventKey || targetOutcome != outcome) {
                    return@Runnable
                }
                beginSettle(outcome)
            }
            postDelayed(requireNotNull(deferredSettleRunnable), remainingSpinMillis)
        } else {
            beginSettle(outcome)
        }
    }

    fun remainingOutcomePresentationMillis(minimumRollDurationMillis: Long): Long {
        val now = SystemClock.uptimeMillis()
        return when {
            rolling ->
                DiceRollAnimationPolicy.remainingVisibleRollMillis(
                    now - rollStartedAtMillis,
                    minimumRollDurationMillis,
                ) + DiceRollAnimationPolicy.SETTLE_DURATION_MILLIS
            settling ->
                (DiceRollAnimationPolicy.SETTLE_DURATION_MILLIS - (now - settleStartedAtMillis))
                    .coerceAtLeast(0L)
            else -> 0L
        }
    }

    private fun beginSettle(outcome: Int) {
        val wasRolling = rolling
        rolling = false
        cancelAnimationTicker()
        targetOutcome = outcome
        face = outcome
        contentDescription = "Dice outcome $outcome."
        settleStartRotationDegrees =
            if (wasRolling && kotlin.math.abs(rotationDegrees) > 1f) {
                rotationDegrees.coerceIn(-20f, 20f)
            } else {
                18f
            }
        settling = true
        settleStartedAtMillis = SystemClock.uptimeMillis()
        scheduleAnimationTicker()
    }

    private fun cancelDeferredSettle() {
        deferredSettleRunnable?.let { removeCallbacks(it) }
        deferredSettleRunnable = null
    }

    fun stopRolling() {
        cancelDeferredSettle()
        rolling = false
        settling = false
        attentionPulsing = false
        cancelAnimationTicker()
        resetTransform()
        contentDescription =
            "Dice verification stopped."
        invalidate()
        if (turnCountdownActive()) scheduleAnimationTicker()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reloadStyle()
        if (turnCountdownActive()) scheduleAnimationTicker()
    }

    fun reloadStyle() {
        diceStyleId =
            previewStyleId
                ?: CosmeticInventoryStore(
                    context,
                )
                    .selectedId(
                        CosmeticCategory.DICE,
                    )
        invalidate()
    }

    /** Applies a store-only preview style without changing equipment or ownership. */
    fun setPreviewStyle(styleId: String) {
        previewStyleId = styleId
        diceStyleId = styleId
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelDeferredSettle()
        rolling = false
        settling = false
        attentionPulsing = false
        cancelAnimationTicker()
        resetTransform()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val desired =
            dp(96f).toInt()
        val width =
            resolveSize(
                desired,
                widthMeasureSpec,
            )
        val height =
            resolveSize(
                desired,
                heightMeasureSpec,
            )
        val size =
            minOf(
                width,
                height,
            )
        setMeasuredDimension(
            size,
            size,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size =
            minOf(
                width,
                height,
            ).toFloat()
        if (size <= 0f) return

        val center = size * .5f
        val saveCount = canvas.save()
        canvas.translate(
            0f,
            size * translationYFraction,
        )
        canvas.scale(
            scale,
            scale,
            center,
            center,
        )
        canvas.rotate(
            rotationDegrees,
            center,
            center,
        )

        val definition = DiceRegistry.resolve(diceStyleId)
        if (rendererKey != definition.rendererKey) {
            renderer = DiceRendererRegistry.create(definition, resources.displayMetrics.density)
            rendererKey = definition.rendererKey
        }
        val palette = dicePalette()
        val rect = renderer.draw(canvas, size, palette,
            DiceRenderFrame(face, rolling, settling, attentionPulsing, borderPulse,
                turnDeadlineElapsedRealtimeMillis != null))
        drawTurnCountdown(canvas, rect, size, palette.borderColor)

        canvas.restoreToCount(saveCount)
    }

    private fun drawTurnCountdown(
        canvas: Canvas,
        rect: RectF,
        size: Float,
        teamColor: Int,
    ) {
        val deadline = turnDeadlineElapsedRealtimeMillis ?: return
        val fraction = DiceTurnCountdownPolicy.remainingFraction(
            deadlineElapsedRealtimeMillis = deadline,
            nowElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            timeoutDurationMillis = turnCountdownDurationMillis,
        ) ?: return

        val radius = (size * .17f - dp(2.2f)).coerceAtLeast(0f)
        // Geometry depends only on the dice bounds. Build the outline once per
        // size/radius and reuse it for each timer frame.
        if (
            turnTimerPathSize != size ||
            turnTimerPathRadius != radius ||
            turnTimerPathBounds != rect
        ) {
            turnTimerPathBounds.set(rect)
            turnTimerPath.reset()
            turnTimerPath.addRoundRect(
                turnTimerPathBounds,
                radius,
                radius,
                Path.Direction.CW,
            )
            turnTimerPathMeasure.setPath(turnTimerPath, false)
            turnTimerPathLength = turnTimerPathMeasure.length
            turnTimerPathSize = size
            turnTimerPathRadius = radius
        }
        val length = turnTimerPathLength
        if (length <= 0f) return

        turnTimerTrackPaint.color = teamColor
        turnTimerProgressPaint.color = teamColor
        turnTimerTrackPaint.alpha = 62
        turnTimerProgressPaint.alpha = if (fraction <= 0f) 0 else 255
        canvas.drawPath(turnTimerPath, turnTimerTrackPaint)

        if (fraction > 0f) {
            // getSegment appends to its destination; rewind the cached path rather
            // than allocating a new Path every time onDraw runs.
            turnTimerProgressPath.rewind()
            turnTimerPathMeasure.getSegment(
                0f,
                length * fraction.coerceIn(0f, 1f),
                turnTimerProgressPath,
                true,
            )
            canvas.drawPath(turnTimerProgressPath, turnTimerProgressPaint)
        }
    }

    private fun applyFrame(
        frame: DiceRollAnimationPolicy.Frame,
    ) {
        face = frame.face
        rotationDegrees = frame.rotationDegrees
        scale = frame.scale
        translationYFraction = frame.translationYFraction
        borderPulse = frame.borderPulse
    }

    private fun resetTransform() {
        rotationDegrees = 0f
        scale = 1f
        translationYFraction = 0f
        borderPulse = 0f
    }

    /**
     * Gameplay dice keep a high-contrast neutral face and a team-colored outline.
     *
     * The selected cosmetic id is still retained by the inventory/store contract,
     * while the white/light-gray face remains legible against the gameplay HUD.
     */
    private fun dicePalette(): DicePalette =
        DiceStylePalettePolicy.resolve(
            styleId = diceStyleId,
            teamColor = playerTeamColor,
        )

    private fun dp(
        value: Float,
    ): Float =
        value *
            resources
                .displayMetrics
                .density
}
