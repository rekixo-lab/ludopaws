package com.rekixo.ludopaws

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.min

/**
 * Presentation-only legal-move affordance for the 3D pawn runtime.
 *
 * Screen-aligned shimmer and tap cues identify legal pawns. Short corner marks
 * identify their occupied tiles; crowded tiles carry a count. No destination
 * preview or moving follow-circle is drawn.
 */
internal class LudoPaws3DLegalPulseView(
    context: Context,
) : View(context) {
    private var snapshot: MatchSnapshot? = null
    private var localPlayerId: String? = null
    private var perspectiveColor: String? = null
    private var characterIds: List<String> = emptyList()
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
        characterIdsBySeat: List<String> = emptyList(),
    ) {
        characterIds = characterIdsBySeat
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
        drawCrowdCounts(canvas, state)
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
        val turns = perspectiveColor?.let(OfflinePlayerLayout::rotationQuarterTurns) ?: 0
        val stackPlacements = LudoPawsPawnLayout.stackPlacements(state, cell, turns)

        val breath = pulse.coerceIn(0f, 1f)
        val glintAlpha = (65 + breath * 110f).toInt().coerceIn(0, 255)
        val sparkleAlpha = (130 + breath * 125f).toInt().coerceIn(0, 255)

        val markedTiles = mutableSetOf<Pair<Float, Float>>()
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
            val logicalX = center.first + (placement?.offsetXFraction ?: 0f) * cell
            val logicalY = center.second + (placement?.offsetYFraction ?: 0f) * cell
            fun screen(px: Float, py: Float): Pair<Float, Float> = when ((turns % 4 + 4) % 4) {
                1 -> (size - py) to px
                2 -> (size - px) to (size - py)
                3 -> py to (size - px)
                else -> px to py
            }
            val point = screen(logicalX, logicalY)
            val id = LudoPaws3DCharacterPolicy.characterIdForSeat(characterIds, player.seat, player.color)
            val fit = LudoPawsPawnLayout.roadPresentation(center.first, center.second,
                cell, position, placement?.occupancy ?: 1, id, turns)
            val y = point.second + fit.footDrop
            val radius = fit.radius
            val bodyHeight = radius * LudoPawsPawnLayout.silhouetteHeight(id)
            val side = if (position in 0 until LudoPathEncoding.HOME_POSITION)
                LudoPawsPawnFacingPolicy.sideRoad(point.first, point.second, size) else 0
            val rise = LudoPawsPawnFacingPolicy.roadRise(side)
            val x = point.first + rise.first * bodyHeight * .5f
            val cueY = y + rise.second * bodyHeight * .5f

            // Mark the occupied tile once, even when multiple legal pawns share it.
            if (position in 0 until LudoPathEncoding.HOME_POSITION &&
                markedTiles.add(center)) {
                val w = if (center.first in cell * 5f..cell * 10f) cell * 5f / 3f else cell * 5f / 6f
                val h = if (center.second in cell * 5f..cell * 10f) cell * 5f / 3f else cell * 5f / 6f
                glintPaint.color = Color.argb(sparkleAlpha, 255, 220, 100)
                glintPaint.strokeWidth = density(1.5f)
                for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                    val corner = screen(center.first + sx * w * .41f, center.second + sy * h * .41f)
                    val alongX = screen(center.first + sx * w * .24f, center.second + sy * h * .41f)
                    val alongY = screen(center.first + sx * w * .41f, center.second + sy * h * .24f)
                    canvas.drawLine(corner.first, corner.second, alongX.first, alongX.second, glintPaint)
                    canvas.drawLine(corner.first, corner.second, alongY.first, alongY.second, glintPaint)
                }
            }
            // Intermittent small finger outline points at the body, with no destination preview.
            if (SystemClock.uptimeMillis() % 3600L < 1400L) {
                val hx = x + radius * .72f
                val hy = cueY + radius * .08f * breath
                sparklePaint.color = Color.argb(sparkleAlpha, 255, 240, 184)
                sparklePaint.strokeWidth = density(1.4f)
                canvas.drawPath(Path().apply {
                    moveTo(hx, hy + radius * .38f)
                    lineTo(hx - radius * .16f, hy + radius * .12f)
                    quadTo(hx - radius * .22f, hy - radius * .02f, hx - radius * .08f, hy)
                    lineTo(hx, hy + radius * .10f)
                    lineTo(hx, hy - radius * .33f)
                    quadTo(hx + radius * .08f, hy - radius * .46f,
                        hx + radius * .15f, hy - radius * .30f)
                    lineTo(hx + radius * .15f, hy - radius * .02f)
                    lineTo(hx + radius * .37f, hy + radius * .04f)
                    lineTo(hx + radius * .33f, hy + radius * .38f)
                }, sparklePaint)
            }

            // A short sheen sits over the pawn body itself. It brightens/fades
            // instead of drawing a ring around the token or its destination.
            glintPaint.strokeWidth = (radius * 0.11f).coerceAtLeast(density(1f))
            glintPaint.color = Color.argb(glintAlpha, 255, 255, 255)
            val glintRise = radius * (0.08f * breath)
            canvas.drawLine(
                x - radius * 0.24f,
                cueY - radius * .15f - glintRise,
                x + radius * 0.08f,
                cueY + radius * .15f - glintRise,
                glintPaint,
            )

            // A tiny four-point sparkle above the animal makes the legal choice
            // readable at a glance without becoming a follow-circle.
            sparklePaint.strokeWidth = (radius * 0.075f).coerceAtLeast(density(0.8f))
            sparklePaint.color = Color.argb(sparkleAlpha, 255, 224, 120)
            val sparkleX = point.first + rise.first * bodyHeight * .85f + radius * .38f
            val sparkleY = y + rise.second * bodyHeight * .88f - radius * .08f * breath
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

    }

    private fun drawCrowdCounts(canvas: Canvas, state: MatchSnapshot) {
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cell = size / LudoPawsFxBoardGeometry.BOARD_SIZE
        val turns = perspectiveColor?.let(OfflinePlayerLayout::rotationQuarterTurns) ?: 0
        val groups = linkedMapOf<Pair<Float, Float>, Int>()
        for (player in state.players) for ((index, position) in player.tokens.withIndex()) {
            if (position !in 0 until LudoPathEncoding.HOME_POSITION) continue
            val center = LudoPawsFxBoardGeometry.tokenCenter(player.color, index, position, cell, turns)
                ?: continue
            groups[center] = (groups[center] ?: 0) + 1
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((center, count) in groups) {
            if (count < 5) continue
            val w = if (center.first in cell * 5f..cell * 10f) cell * 5f / 3f else cell * 5f / 6f
            val h = if (center.second in cell * 5f..cell * 10f) cell * 5f / 3f else cell * 5f / 6f
            val rotated = when ((turns % 4 + 4) % 4) {
                1 -> (size - center.second) to center.first
                2 -> (size - center.first) to (size - center.second)
                3 -> center.second to (size - center.first)
                else -> center
            }
            val screenW = if (turns % 2 == 0) w else h
            val screenH = if (turns % 2 == 0) h else w
            val r = minOf(cell * .17f, screenH * .19f)
            val x = rotated.first + screenW * .5f - r - cell * .04f
            val y = rotated.second - screenH * .5f + r + cell * .04f
            paint.color = 0xF0273947.toInt()
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, r, paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = r * 1.35f
            paint.isFakeBoldText = true
            canvas.drawText(count.toString(), x, y - (paint.ascent() + paint.descent()) * .5f, paint)
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
