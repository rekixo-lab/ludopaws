package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.characters.rendering.CharacterAvatarContext
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
 * The existing vector art remains a synchronous fallback if off-screen OpenGL
 * rendering is unavailable, so the UI never waits on portrait generation.
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
    private val avatarContext = CharacterAvatarContext(fill, stroke)
    private val portraitPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }

    private var characterId: String =
        LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
    private var ringColor: Int =
        0xFF5BE0FF.toInt()
    private var highlighted: Boolean = false
    private var showCircularChrome: Boolean = true
    private var modelTeamColor: String = LudoPawsTeamColor.BLUE.wireName
    private var requestedPortraitKey: String? = null
    private var portraitBitmap: Bitmap? = null

    fun bind(
        characterId: String?,
        ringColor: Int = 0xFF5BE0FF.toInt(),
        highlighted: Boolean = false,
        modelTeamColor: String? = null,
        showCircularChrome: Boolean = true,
    ) {
        this.characterId =
            LudoPawsCharacterCatalog
                .canonicalCharacterId(characterId)
                ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID
        this.ringColor = ringColor
        this.highlighted = highlighted
        this.showCircularChrome = showCircularChrome
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
            requestedPortraitKey = requestKey
            portraitBitmap = null
            val requestedCharacterId = this.characterId
            val requestedTeamColor = this.modelTeamColor
            LudoPawsCharacterPortraitBitmapCache.request(
                characterId = requestedCharacterId,
                teamColor = requestedTeamColor,
            ) { bitmap ->
                // Views can be rebound while a portrait is still rendering.
                // Never let an older request overwrite a newer selection.
                if (
                    requestedPortraitKey == requestKey &&
                    this.characterId == requestedCharacterId &&
                    this.modelTeamColor == requestedTeamColor
                ) {
                    portraitBitmap = bitmap
                    invalidate()
                }
            }
        }
        invalidate()
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

        val scale = size / 100f
        canvas.save()
        canvas.translate(
            (width - size) / 2f,
            (height - size) / 2f,
        )
        canvas.scale(scale, scale)

        if (showCircularChrome) {
            fill.color = 0x22000000
            canvas.drawCircle(50f, 53f, 44f, fill)

            fill.color = 0xFFF8FBFF.toInt()
            canvas.drawCircle(50f, 49f, 43f, fill)

            stroke.color = ringColor
            stroke.strokeWidth = if (highlighted) 6f else 4f
            canvas.drawCircle(50f, 49f, 43f, stroke)
        }

        val portrait = portraitBitmap
        if (portrait != null && !portrait.isRecycled) {
            canvas.drawBitmap(
                portrait,
                null,
                RectF(0f, 0f, 100f, 100f),
                portraitPaint,
            )
        } else {
            // Keep a non-blank immediate fallback while the shared 3D portrait
            // is generated, or if ES3/off-screen rendering is unavailable.
            CharacterRegistry.resolve(characterId).avatar.draw(avatarContext, canvas)
        }

        canvas.restore()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density)
            .roundToInt()
}
