package com.rekixo.ludopaws.ui.online

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min

internal class MatchmakingOpponentRailView(
    context: Context,
) : View(context) {
    private val border =
        Paint(
            Paint.ANTI_ALIAS_FLAG,
        ).apply {
            style =
                Paint.Style.STROKE
            strokeWidth =
                dp(2f)
        }

    private val cardFill =
        Paint(
            Paint.ANTI_ALIAS_FLAG,
        ).apply {
            style =
                Paint.Style.FILL
        }

    private val silhouette =
        Paint(
            Paint.ANTI_ALIAS_FLAG,
        ).apply {
            style =
                Paint.Style.FILL
            color =
                0xFFBDEFFF.toInt()
        }

    private val label =
        Paint(
            Paint.ANTI_ALIAS_FLAG,
        ).apply {
            style =
                Paint.Style.FILL
            textAlign =
                Paint.Align.CENTER
            typeface =
                android.graphics.Typeface.DEFAULT_BOLD
            textSize =
                dp(10f)
        }

    private var targetPlayerCount =
        2
    private var queuedPlayers =
        1
    private var searching =
        false
    private var scanProgress =
        0f

    private val animator =
        ValueAnimator
            .ofFloat(
                0f,
                1f,
            )
            .apply {
                duration =
                    1_250L
                repeatCount =
                    ValueAnimator.INFINITE
                repeatMode =
                    ValueAnimator.REVERSE
                addUpdateListener {
                    scanProgress =
                        it.animatedValue as Float
                    invalidate()
                }
            }

    fun updateState(
        queuedPlayers: Int,
        targetPlayerCount: Int,
        searching: Boolean,
    ) {
        this.targetPlayerCount =
            if (
                targetPlayerCount ==
                4
            ) {
                4
            } else {
                2
            }
        this.queuedPlayers =
            queuedPlayers.coerceIn(
                1,
                this.targetPlayerCount,
            )
        this.searching =
            searching

        if (
            searching &&
            isAttachedToWindow
        ) {
            ensureAnimator()
        } else if (
            !searching
        ) {
            animator.cancel()
            scanProgress =
                0f
        }

        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (
            searching
        ) {
            ensureAnimator()
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(
        canvas: Canvas,
    ) {
        super.onDraw(
            canvas,
        )

        val opponentSlots =
            max(
                1,
                targetPlayerCount -
                    1,
            )
        val gap =
            dp(8f)
        val usableWidth =
            width.toFloat() -
                gap *
                (
                    opponentSlots -
                        1
                    )
        val cardWidth =
            usableWidth /
                opponentSlots
        val cardHeight =
            min(
                height.toFloat(),
                dp(122f),
            )
        val top =
            (
                height -
                    cardHeight
                ) /
                2f
        val foundOpponents =
            (
                queuedPlayers -
                    1
                )
                .coerceIn(
                    0,
                    opponentSlots,
                )

        for (
            index in
            0 until opponentSlots
        ) {
            val left =
                index *
                    (
                        cardWidth +
                            gap
                        )
            val rect =
                RectF(
                    left,
                    top,
                    left +
                        cardWidth,
                    top +
                        cardHeight,
                )
            val found =
                index <
                    foundOpponents
            val activeSearchSlot =
                searching &&
                    index ==
                    foundOpponents

            // Match the Profile page's translucent blue surface (#33104483).
            // Slots stand alone on the shared full-page backdrop.
            cardFill.color =
                0x33104483
            border.color =
                when {
                    // Keep a green outline only for a slot whose opponent is found.
                    found ->
                        0xFF62ED91.toInt()

                    // Searching and waiting slots use the Profile page's blue stroke.
                    else ->
                        0xFF247FCB.toInt()
                }

            canvas.drawRoundRect(
                rect,
                dp(14f),
                dp(14f),
                cardFill,
            )
            canvas.drawRoundRect(
                rect,
                dp(14f),
                dp(14f),
                border,
            )

            val centerX =
                rect.centerX()
            val avatarCenterY =
                rect.top +
                    cardHeight *
                    0.43f
            val avatarX =
                if (
                    activeSearchSlot
                ) {
                    rect.left +
                        rect.width() *
                        (
                            0.28f +
                                0.44f *
                                scanProgress
                            )
                } else {
                    centerX
                }

            drawSilhouette(
                canvas,
                avatarX,
                avatarCenterY,
                when {
                    found ->
                        1f

                    activeSearchSlot ->
                        0.82f

                    else ->
                        0.28f
                },
            )

            label.color =
                when {
                    found ->
                        0xFF8DFFAB.toInt()

                    activeSearchSlot ->
                        0xFFFFE58A.toInt()

                    else ->
                        0xFF9AB6D2.toInt()
                }
            label.alpha =
                255
            canvas.drawText(
                when {
                    found ->
                        "PLAYER FOUND"

                    activeSearchSlot ->
                        "SEARCHING…"

                    else ->
                        "WAITING"
                },
                centerX,
                rect.bottom -
                    dp(14f),
                label,
            )
        }

        label.alpha =
            255
    }

    private fun drawSilhouette(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        alphaFraction: Float,
    ) {
        val previousAlpha =
            silhouette.alpha
        silhouette.alpha =
            (
                255f *
                    alphaFraction
                )
                .toInt()
                .coerceIn(
                    0,
                    255,
                )

        val headRadius =
            dp(12f)
        canvas.drawCircle(
            centerX,
            centerY -
                dp(13f),
            headRadius,
            silhouette,
        )

        val shoulders =
            Path().apply {
                moveTo(
                    centerX -
                        dp(24f),
                    centerY +
                        dp(26f),
                )
                cubicTo(
                    centerX -
                        dp(22f),
                    centerY +
                        dp(7f),
                    centerX -
                        dp(12f),
                    centerY +
                        dp(1f),
                    centerX,
                    centerY +
                        dp(1f),
                )
                cubicTo(
                    centerX +
                        dp(12f),
                    centerY +
                        dp(1f),
                    centerX +
                        dp(22f),
                    centerY +
                        dp(7f),
                    centerX +
                        dp(24f),
                    centerY +
                        dp(26f),
                )
                close()
            }
        canvas.drawPath(
            shoulders,
            silhouette,
        )

        silhouette.alpha =
            previousAlpha
    }

    private fun ensureAnimator() {
        if (
            !animator.isStarted
        ) {
            animator.start()
        }
    }

    private fun dp(
        value: Float,
    ):
        Float =
        value *
            resources
                .displayMetrics
                .density
}
