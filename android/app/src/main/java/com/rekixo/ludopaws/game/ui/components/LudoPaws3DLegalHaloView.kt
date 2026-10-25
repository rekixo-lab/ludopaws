package com.rekixo.ludopaws

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
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

    // Unit-sized hand paths/materials are reused throughout the pulse animation.
    private val handPath = Path().apply {
        moveTo(0f, .38f)
        lineTo(-.16f, .12f)
        quadTo(-.22f, -.02f, -.08f, 0f)
        lineTo(0f, .10f)
        lineTo(0f, -.33f)
        cubicTo(.01f, -.44f, .14f, -.44f, .15f, -.30f)
        lineTo(.15f, -.02f)
        quadTo(.21f, -.05f, .23f, .015f)
        quadTo(.30f, -.015f, .31f, .065f)
        quadTo(.39f, .04f, .39f, .14f)
        lineTo(.33f, .38f)
        close()
    }
    private val handFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(-.16f, -.36f, .36f, .36f,
            intArrayOf(0xFFFFE6C7.toInt(), 0xFFF4BC86.toInt(), 0xFFC67F50.toInt()),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
    }
    private val handShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x60382214 }
    private val handEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF865235.toInt()
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val handHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFF1DC.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val handShine = Path().apply {
        moveTo(.035f, -.08f)
        lineTo(.035f, -.30f)
        quadTo(.065f, -.385f, .105f, -.31f)
    }
    private val handCreases = Path().apply {
        moveTo(.185f, .10f); lineTo(.18f, .19f)
        moveTo(.265f, .12f); lineTo(.255f, .21f)
        moveTo(.02f, .18f); quadTo(.10f, .15f, .15f, .25f)
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
            val x = point.first
            val y = point.second + fit.footDrop
            val radius = fit.radius
            val bodyHeight = radius * LudoPawsPawnLayout.silhouetteHeight(id)

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
            // Intermittent shaded hand points at the body, with no destination preview.
            if (SystemClock.uptimeMillis() % 3600L < 1400L) {
                val hx = x + radius * .72f
                val hy = y - bodyHeight * .55f + radius * .08f * breath
                val handSave = canvas.save()
                canvas.translate(hx, hy)
                canvas.scale(radius, radius)
                handShadow.alpha = sparkleAlpha * 3 / 10
                canvas.save()
                canvas.translate(.035f, .045f)
                canvas.drawPath(handPath, handShadow)
                canvas.restore()
                handFill.alpha = sparkleAlpha
                canvas.drawPath(handPath, handFill)
                handEdge.alpha = sparkleAlpha
                handEdge.strokeWidth = maxOf(.025f, density(.65f) / radius)
                canvas.drawPath(handPath, handEdge)
                handHighlight.alpha = sparkleAlpha * 3 / 4
                handHighlight.strokeWidth = maxOf(.025f, density(.55f) / radius)
                canvas.drawPath(handShine, handHighlight)
                handEdge.alpha = sparkleAlpha / 2
                handEdge.strokeWidth = maxOf(.018f, density(.40f) / radius)
                canvas.drawPath(handCreases, handEdge)
                canvas.restoreToCount(handSave)
            }

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
            val sparkleY = y - bodyHeight * .88f - radius * .08f * breath
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
