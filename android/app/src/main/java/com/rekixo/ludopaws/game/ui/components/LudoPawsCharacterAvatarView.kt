package com.rekixo.ludopaws

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared character portrait for selection, profile, and gameplay player cards.
 *
 * Portraits are rasterized from the production 3D pawn geometry and cached.
 * Only portraits from the actual production model are shown. Pending/failed
 * preparation has a text status instead of a different 2D character drawing.
 */
class LudoPawsCharacterAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val fill =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val stroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val portraitPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }

    private val portraitBounds = RectF(0f, 0f, 100f, 100f)
    private var characterId: String =
        LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
    private var ringColor: Int =
        0xFF5BE0FF.toInt()
    private var highlighted: Boolean = false
    private var showCircularChrome: Boolean = true
    private var showTeamBadge: Boolean = false
    private var modelTeamColor: String = LudoPawsTeamColor.BLUE.wireName
    private var requestedPortraitKey: String? = null
    private var portraitBitmap: Bitmap? = null
    private val visibleBounds = android.graphics.Rect()
    private var failedAttempts = 0
    private var retryAt = 0L
    private val retryPortrait = Runnable { if (isAttachedToWindow && isShown) invalidate() }

    init { LudoPawsCharacterPortraitBitmapCache.initialize(context) }

    fun bind(
        characterId: String?,
        ringColor: Int = 0xFF5BE0FF.toInt(),
        highlighted: Boolean = false,
        modelTeamColor: String? = null,
        showCircularChrome: Boolean = true,
        showTeamBadge: Boolean = false,
    ) {
        this.characterId =
            LudoPawsCharacterCatalog
                .canonicalCharacterId(characterId)
                ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
        this.ringColor = ringColor
        this.highlighted = highlighted
        this.showCircularChrome = showCircularChrome
        this.showTeamBadge = showTeamBadge
        this.modelTeamColor =
            LudoPawsTeamIdentityPolicy
                .resolve(modelTeamColor)
                ?.wireName
                ?: LudoPawsTeamColor.BLUE.wireName
        contentDescription =
            LudoPawsCharacterCatalog
                .character(this.characterId)
                ?.displayName
                ?: "Ludo Paws character"

        val requestKey =
            LudoPawsCharacterPortraitBitmapCache.keyFor(
                characterId = this.characterId,
                teamColor = this.modelTeamColor,
            )
        if (requestedPortraitKey != requestKey) {
            requestedPortraitKey = null
            portraitBitmap = LudoPawsCharacterPortraitBitmapCache.peek(this.characterId, this.modelTeamColor)
            failedAttempts = 0
            retryAt = 0L
            removeCallbacks(retryPortrait)
        }
        if (portraitBitmap == null) contentDescription = "${characterName()}. Loading character image."
        requestVisiblePortrait()
        invalidate()
    }

    private fun requestVisiblePortrait() {
        if (!isAttachedToWindow || !isShown || !getLocalVisibleRect(visibleBounds) ||
            portraitBitmap != null || failedAttempts >= 3 || android.os.SystemClock.elapsedRealtime() < retryAt) return
        val requestKey = LudoPawsCharacterPortraitBitmapCache.keyFor(characterId, modelTeamColor)
        if (requestedPortraitKey == requestKey) return
        requestedPortraitKey = requestKey
        val requestedCharacterId = characterId
        LudoPawsCharacterPortraitBitmapCache.request(requestedCharacterId, modelTeamColor) { bitmap ->
            if (requestedPortraitKey == requestKey && characterId == requestedCharacterId) {
                portraitBitmap = bitmap
                if (bitmap == null) {
                    requestedPortraitKey = null
                    failedAttempts++
                    retryAt = android.os.SystemClock.elapsedRealtime() + 16_000L
                    contentDescription = "${characterName()}. Character image unavailable." +
                        if (failedAttempts < 3) " Retrying." else " Reopen this screen to retry."
                    if (failedAttempts < 3) postDelayed(retryPortrait, 16_000L)
                } else {
                    failedAttempts = 0
                    contentDescription = characterName()
                }
                invalidate()
            }
        }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val desired = dp(56)
        val measuredWidth = resolveSize(desired, widthMeasureSpec)
        val measuredHeight = resolveSize(desired, heightMeasureSpec)
        val size = min(measuredWidth, measuredHeight)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        requestVisiblePortrait()

        val scale = size / 100f
        canvas.save()
        canvas.translate(
            (width - size) / 2f,
            (height - size) / 2f,
        )
        canvas.scale(scale, scale)

        val portrait = portraitBitmap
        if (portrait == null || portrait.isRecycled) {
            fill.color = 0xFF6A5140.toInt()
            fill.textAlign = Paint.Align.CENTER
            fill.textSize = 11f
            canvas.drawText(if (failedAttempts > 0) "Unavailable" else "Loading…", 50f, 53f, fill)
            canvas.restore()
            return
        }

        if (showCircularChrome) {
            fill.color = 0x22000000
            canvas.drawCircle(50f, 53f, 44f, fill)

            fill.color = 0xFFF8FBFF.toInt()
            canvas.drawCircle(50f, 49f, 43f, fill)

            stroke.color = ringColor
            stroke.strokeWidth = if (highlighted) 6f else 4f
            canvas.drawCircle(50f, 49f, 43f, stroke)
        }

        canvas.drawBitmap(portrait, null, portraitBounds, portraitPaint)

        if (showTeamBadge) {
            // Always distinguish player colors, even when the 3D texture
            // uses the neutral natural-material portrait.
            val badgeColor = LudoPawsTeamIdentityPolicy.resolve(modelTeamColor)?.argb
                ?: ringColor
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(87f, 87f, 12f, fill)
            fill.color = badgeColor
            canvas.drawCircle(87f, 87f, 9f, fill)
            stroke.color = 0xAA061A32.toInt()
            stroke.strokeWidth = 2f
            canvas.drawCircle(87f, 87f, 9f, stroke)
        }

        canvas.restore()
    }

    private fun characterName() = LudoPawsCharacterCatalog.character(characterId)?.displayName ?: "Character"

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (portraitBitmap == null) {
            requestedPortraitKey = null
            failedAttempts = 0
            retryAt = 0L
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(retryPortrait)
        requestedPortraitKey = null
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density)
            .roundToInt()
}
