package com.rekixo.ludopaws.core.ui.art

import android.content.Context
import android.graphics.drawable.GradientDrawable
import com.rekixo.ludopaws.LudoProofTheme

/**
 * App-wide surface palette and background factories.
 *
 * Game screens should reuse these helpers instead of recreating approved panel,
 * icon, progress, plywood or recessed-pocket backgrounds inline. Keep the visual
 * values here as the shared source of truth for the LudoProof UI design language.
 */

/**
 * Shared translucent navy surface for cards and sections across the app.
 * Defaults match the approved UI: #33104483 fill, #FF247FCB stroke, 18dp radius.
 */
internal fun transparentDarkBlueStrokeBackground(
    context: Context,
    cornerRadiusDp: Float = 18f,
): GradientDrawable =
    LudoProofTheme.rounded(
        0x33104483,
        cornerRadiusDp,
        0xFF247FCB.toInt(),
        1f,
        context,
    )

/**
 * Shared blue icon chip. Pass fill/stroke overrides only when a specific icon already uses
 * a slightly different approved blue; its shape and drawable construction remain centralized.
 */
internal fun blueIconBackground(
    context: Context,
    cornerRadiusDp: Float = 999f,
    fillColor: Int = 0xFF073F8C.toInt(),
    strokeColor: Int = 0xFF1C8BDA.toInt(),
    strokeWidthDp: Float = 1f,
): GradientDrawable =
    LudoProofTheme.rounded(
        fillColor,
        cornerRadiusDp,
        strokeColor,
        strokeWidthDp,
        context,
    )


/** Shared factory for the approved code-drawn plywood skin. */
internal fun plywoodBackground(
    context: Context,
    cornerRadiusDp: Float = 15f,
    rimThicknessDp: Float = 5.5f,
    gameplayFinish: Boolean = false,
): android.graphics.drawable.Drawable =
    PlywoodTextureDrawable(
        density = context.resources.displayMetrics.density,
        cornerRadiusDp = cornerRadiusDp,
        rimThicknessDp = rimThicknessDp,
        gameplayFinish = gameplayFinish,
    )

/** Shared factory for a curved, recessed plywood pocket for 3D character/art panels. */
internal fun recessedPlywoodBackground(
    context: Context,
    outerCornerRadiusDp: Float = 18f,
): android.graphics.drawable.Drawable =
    RecessedPlywoodDrawable(context.resources.displayMetrics.density, outerCornerRadiusDp)


/**
 * Shared selection treatment for player-count tiles.
 *
 * Both states use the carved plywood pocket. The active state adds a soft gold
 * wash, inset highlight and a compact check badge without covering the tile art.
 */
internal fun playerCountSelectionBackground(
    context: Context,
    selected: Boolean,
    outerCornerRadiusDp: Float = 20f,
    checkBadgeAtTopEnd: Boolean = false,
    emphasizeSelected: Boolean = false,
    showCheckBadge: Boolean = true,
    checkMarkScale: Float = 1f,
    useRecessedPlywood: Boolean = true,
): android.graphics.drawable.Drawable {
    val baseBackground =
        if (useRecessedPlywood) {
            recessedPlywoodBackground(context, outerCornerRadiusDp)
        } else {
            plywoodBackground(context, outerCornerRadiusDp)
        }
    if (!selected) return baseBackground
    return android.graphics.drawable.LayerDrawable(
        arrayOf(
            baseBackground,
            PlayerCountSelectionOverlayDrawable(
                context,
                checkBadgeAtTopEnd,
                emphasizeSelected,
                showCheckBadge,
                checkMarkScale,
            ),
        ),
    )
}

private class PlayerCountSelectionOverlayDrawable(
    context: Context,
    private val checkBadgeAtTopEnd: Boolean,
    private val emphasizeSelected: Boolean,
    private val showCheckBadge: Boolean,
    private val checkMarkScale: Float,
) : android.graphics.drawable.Drawable() {
    private val density = context.resources.displayMetrics.density
    private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val strokePaint =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
    private var drawableAlpha = 255

    override fun draw(canvas: android.graphics.Canvas) {
        val boundsRect = android.graphics.RectF(bounds)
        if (boundsRect.width() <= 0f || boundsRect.height() <= 0f) return

        // Soft gold tint inside the carved pocket, leaving the timber rim visible.
        val inset = dp(7.5f)
        val overlayRect = android.graphics.RectF(boundsRect).apply {
            inset(inset, inset)
        }
        val radius = dp(12f).coerceAtMost(
            minOf(overlayRect.width(), overlayRect.height()) / 2f,
        )
        fillPaint.style = android.graphics.Paint.Style.FILL
        fillPaint.color = 0xFFFFD45E.toInt()
        fillPaint.alpha = (if (emphasizeSelected) 92 else 46) * drawableAlpha / 255
        canvas.drawRoundRect(overlayRect, radius, radius, fillPaint)

        // Thin inset edge gives selection a clear boundary without an outside stroke.
        val strokeRect = android.graphics.RectF(overlayRect).apply {
            inset(dp(0.8f), dp(0.8f))
        }
        strokePaint.color = 0xFFFFD45E.toInt()
        strokePaint.alpha = (if (emphasizeSelected) 255 else 220) * drawableAlpha / 255
        strokePaint.strokeWidth = dp(if (emphasizeSelected) 2f else 1.35f)
        val strokeRadius = (radius - dp(0.8f)).coerceAtLeast(1f)
        canvas.drawRoundRect(strokeRect, strokeRadius, strokeRadius, strokePaint)

        // Small Friends-page count buttons use the inset gold outline alone so
        // the selected numeral remains unobstructed.
        if (!showCheckBadge) return

        // Friends keeps its compact marker by default. Computer opts into a
        // 3x checkmark; its badge grows enough to contain the enlarged tick.
        val topEndScale = if (checkBadgeAtTopEnd) checkMarkScale.coerceAtLeast(1f) else 1f
        val baseTickRadius = dp(if (checkBadgeAtTopEnd) 5.2f else 7.2f)
        val badgeRadius =
            if (checkBadgeAtTopEnd) dp(5.2f + (topEndScale - 1f) * 3.4f)
            else baseTickRadius
        val badgeInset =
            if (checkBadgeAtTopEnd) 8f + (topEndScale - 1f) * 3f
            else 18f
        val badgeX = boundsRect.right - dp(badgeInset)
        val badgeY =
            if (checkBadgeAtTopEnd) boundsRect.top + dp(badgeInset)
            else boundsRect.bottom - dp(19f)
        fillPaint.alpha = drawableAlpha
        canvas.drawCircle(badgeX, badgeY, badgeRadius, fillPaint)

        // Match the Friends check tick to the green COPY button.
        strokePaint.color =
            if (checkBadgeAtTopEnd) 0xFF08A93B.toInt() else 0xFF63451B.toInt()
        strokePaint.alpha = drawableAlpha
        strokePaint.strokeWidth =
            dp(if (checkBadgeAtTopEnd) 1.5f * topEndScale else 1.8f)
        val tickRadius = if (checkBadgeAtTopEnd) baseTickRadius * topEndScale else badgeRadius
        val check = android.graphics.Path().apply {
            moveTo(badgeX - tickRadius * 0.43f, badgeY + tickRadius * 0.02f)
            lineTo(badgeX - tickRadius * 0.10f, badgeY + tickRadius * 0.34f)
            lineTo(badgeX + tickRadius * 0.44f, badgeY - tickRadius * 0.36f)
        }
        canvas.drawPath(check, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

    private fun dp(value: Float): Float = value * density
}

/** Approved green/cyan Google Play sign-in action skin. */
internal fun playGamesSignInBackground(context: Context): GradientDrawable =
    LudoProofTheme.rounded(
        0xFF058A69.toInt(),
        13f,
        0xFF20E1C0.toInt(),
        1.5f,
        context,
    )

/** Dark blue track used under the player's XP progress. */
internal fun experienceProgressTrackBackground(context: Context): GradientDrawable =
    LudoProofTheme.rounded(
        0xFF052E78.toInt(),
        999f,
        0xFF3185D5.toInt(),
        1f,
        context,
    )

/** Gold gradient fill used by the player's XP progress. */
internal fun experienceProgressFillBackground(context: Context): GradientDrawable =
    GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0xFFFFD83D.toInt(), 0xFFFFB812.toInt()),
    ).apply {
        cornerRadius = LudoProofTheme.dp(context, 999f).toFloat()
    }

/** Dark blue track used by a per-mode win-rate progress bar. */
internal fun classicStatsProgressTrackBackground(context: Context): GradientDrawable =
    LudoProofTheme.rounded(
        0xFF062E70.toInt(),
        999f,
        0xFF1E73BE.toInt(),
        1f,
        context,
    )

/** Gold gradient fill used by a per-mode win-rate progress bar. */
internal fun classicStatsProgressFillBackground(context: Context): GradientDrawable =
    GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(0xFFFFD93D.toInt(), 0xFFFFB91A.toInt()),
    ).apply {
        cornerRadius = LudoProofTheme.dp(context, 999f).toFloat()
    }

/** Translucent blue circular backing for the empty Purchased Products illustration. */
internal fun emptyPurchaseIconBackground(context: Context): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(0x1C3DBEFF)
        setStroke(LudoProofTheme.dp(context, 1), 0x663EABF3)
    }


/** Darker blue icon backing used by the Classic mode tile. */
internal fun classicModeIconBackground(context: Context): GradientDrawable =
    blueIconBackground(
        context = context,
        fillColor = 0xE20A347A.toInt(),
        strokeColor = 0xFF217BD0.toInt(),
    )
