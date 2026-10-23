package com.rekixo.ludopaws

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.min

/**
 * Presentation-only legal-move affordance for the 3D pawn runtime.
 *
 * Only pawn-local shimmer marks are rendered. There is deliberately no circle,
 * ring or destination preview around the animal. This keeps the cue attached to
 * the pawn itself while preserving the production no-follow-circle contract.
 */
internal class LudoPaws3DLegalPulseView(
    context: Context,
) : View(context) {
    private var snapshot: MatchSnapshot? = null
    private var localPlayerId: String? = null
    private var perspectiveColor: String? = null
    private var pulse = 0f
    private var pulseAnimator: ValueAnimator? = null

    private val glintPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    private val sparklePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bind(
        state: MatchSnapshot?,
        playerId: String?,
        perspectiveColor: String?,
    ) {
        snapshot = state
        localPlayerId = playerId
        this.perspectiveColor =
            perspectiveColor
                ?.takeIf { it in OfflinePlayerLayout.COLORS }

        val player =
            state
                ?.players
                ?.firstOrNull { it.playerId == playerId }
        val shouldPulse =
            state?.pendingRoll?.let { pending ->
                player != null &&
                    pending.status == "RESOLVED" &&
                    pending.seat == player.seat &&
                    pending.legalTokenIndexes.isNotEmpty()
            } == true
        if (shouldPulse) {
            startPulseIfNeeded()
        } else {
            stopPulse()
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stopPulse()
        super.onDetachedFromWindow()
    }

    override fun onDraw(
        canvas: Canvas,
    ) {
        super.onDraw(canvas)
        val state = snapshot ?: return
        val pending = state.pendingRoll ?: return
        val localId = localPlayerId ?: return
        val player =
            state.players.firstOrNull {
                it.playerId == localId
            } ?: return
        if (
            pending.status != "RESOLVED" ||
            pending.seat != player.seat ||
            pending.legalTokenIndexes.isEmpty()
        ) {
            return
        }

        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cell =
            size /
                LudoPawsFxBoardGeometry.BOARD_SIZE
        val stackPlacements =
            LudoPawsPawnLayout.stackPlacements(
                snapshot = state,
                cell = cell,
            )
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

        val breath = pulse.coerceIn(0f, 1f)
        val glintAlpha = (28 + breath * 72f).toInt().coerceIn(0, 255)
        val sparkleAlpha = (78 + breath * 122f).toInt().coerceIn(0, 255)

        pending.legalTokenIndexes.forEach { tokenIndex ->
            val position =
                player.tokens.getOrNull(tokenIndex)
                    ?: return@forEach
            val center =
                LudoPawsFxBoardGeometry
                    .tokenCenter(
                        color = player.color,
                        tokenIndex = tokenIndex,
                        position = position,
                        cell = cell,
                        perspectiveQuarterTurns = turns,
                    )
                    ?: return@forEach
            val placement =
                stackPlacements[
                    LudoPawsPawnVisualKey(
                        playerId = player.playerId,
                        tokenIndex = tokenIndex,
                    )
                ]
            val x =
                center.first +
                    (placement?.offsetXFraction ?: 0f) * cell
            val y =
                center.second +
                    (placement?.offsetYFraction ?: 0f) * cell
            val radius =
                cell *
                    LudoPawsPawnLayout
                        .radiusScale(
                            position = position,
                            occupancy = placement?.occupancy ?: 1,
                        )

            // A short sheen sits over the pawn body itself. It brightens/fades
            // instead of drawing a ring around the token or its destination.
            glintPaint.strokeWidth = (radius * 0.11f).coerceAtLeast(density(1f))
            glintPaint.color = Color.argb(glintAlpha, 255, 255, 255)
            val glintRise = radius * (0.08f * breath)
            canvas.drawLine(
                x - radius * 0.24f,
                y - radius * 0.42f - glintRise,
                x + radius * 0.08f,
                y - radius * 0.12f - glintRise,
                glintPaint,
            )

            // A tiny four-point sparkle above the animal makes the legal choice
            // readable at a glance without becoming a follow-circle.
            sparklePaint.strokeWidth = (radius * 0.075f).coerceAtLeast(density(0.8f))
            sparklePaint.color = Color.argb(sparkleAlpha, 255, 224, 120)
            val sparkleX = x + radius * 0.38f
            val sparkleY = y - radius * (0.88f + 0.08f * breath)
            val longArm = radius * (0.18f + 0.06f * breath)
            val shortArm = radius * (0.11f + 0.035f * breath)
            canvas.drawLine(
                sparkleX,
                sparkleY - longArm,
                sparkleX,
                sparkleY + longArm,
                sparklePaint,
            )
            canvas.drawLine(
                sparkleX - shortArm,
                sparkleY,
                sparkleX + shortArm,
                sparkleY,
                sparklePaint,
            )
        }

        if (turns != 0) {
            canvas.restore()
        }
    }

    private fun startPulseIfNeeded() {
        if (pulseAnimator != null) return
        pulseAnimator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = PULSE_DURATION_MS
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    pulse = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulse = 0f
        invalidate()
    }

    private fun density(
        value: Float,
    ): Float =
        value * resources.displayMetrics.density

    private companion object {
        const val PULSE_DURATION_MS = 720L
    }
}
