package com.rekixo.ludopaws.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import com.rekixo.ludopaws.core.ui.art.RareGemArtwork
import com.rekixo.ludopaws.LudoProofTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal enum class HomeIconKind {
    GEM,
    PROFILE,
    EDIT,
    ADD,
    LEADERBOARD,
    SHOP,
    BADGE,
    SHARE,
    RATING,
    AD_BLOCKER,
    SETTINGS,
}

internal class HomeIconView(
    context: Context,
) : View(context) {
    var kind: HomeIconKind = HomeIconKind.SHOP
        set(value) {
            field = value
            invalidate()
        }

    var iconColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val stroke =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private val fill =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    private val detail =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = min(width, height).toFloat()
        if (size <= 0f) return

        val scale = size / 100f
        val left = (width - size) / 2f
        val top = (height - size) / 2f

        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)

        stroke.color = iconColor
        fill.color = iconColor
        detail.color = iconColor

        when (kind) {
            HomeIconKind.GEM -> drawGem(canvas)
            HomeIconKind.PROFILE -> drawProfile(canvas)
            HomeIconKind.EDIT -> drawEdit(canvas)
            HomeIconKind.ADD -> drawAdd(canvas)
            HomeIconKind.LEADERBOARD -> drawLeaderboard(canvas)
            HomeIconKind.SHOP -> drawShop(canvas)
            HomeIconKind.BADGE -> drawBadge(canvas)
            HomeIconKind.SHARE -> drawShare(canvas)
            HomeIconKind.RATING -> drawRatingStar(canvas)
            HomeIconKind.AD_BLOCKER -> drawAdBlocker(canvas)
            HomeIconKind.SETTINGS -> drawSettings(canvas)
        }

        canvas.restore()
    }

    private val gemBitmap by lazy(LazyThreadSafetyMode.NONE) { RareGemArtwork.gem(resources) }
    private val gemBounds = RectF(3f, 3f, 97f, 97f)
    private val gemPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun drawGem(canvas: Canvas) {
        canvas.drawBitmap(gemBitmap, null, gemBounds, gemPaint)
    }

    private fun drawProfile(canvas: Canvas) {
        fill.color = Color.WHITE
        canvas.drawCircle(50f, 35f, 16f, fill)
        canvas.drawRoundRect(
            RectF(23f, 55f, 77f, 84f),
            19f,
            19f,
            fill,
        )
        detail.color = 0x669B6EFF
        detail.strokeWidth = 3f
        canvas.drawCircle(50f, 35f, 16f, detail)
        canvas.drawRoundRect(
            RectF(23f, 55f, 77f, 84f),
            19f,
            19f,
            detail,
        )
    }

    private fun drawEdit(canvas: Canvas) {
        val pencil =
            Path().apply {
                moveTo(24f, 69f)
                lineTo(29f, 52f)
                lineTo(64f, 17f)
                lineTo(83f, 36f)
                lineTo(48f, 71f)
                lineTo(31f, 76f)
                close()
            }
        fill.color = iconColor
        canvas.drawPath(pencil, fill)
        detail.color = 0x77000000
        detail.strokeWidth = 3f
        canvas.drawLine(29f, 52f, 48f, 71f, detail)
        canvas.drawLine(64f, 17f, 83f, 36f, detail)
        fill.color = 0xFFFFD98C.toInt()
        canvas.drawPath(
            Path().apply {
                moveTo(24f, 69f)
                lineTo(31f, 76f)
                lineTo(18f, 82f)
                close()
            },
            fill,
        )
    }

    private fun drawAdd(canvas: Canvas) {
        stroke.color = iconColor
        stroke.strokeWidth = 11f
        canvas.drawLine(50f, 25f, 50f, 75f, stroke)
        canvas.drawLine(25f, 50f, 75f, 50f, stroke)
    }

    private fun drawShop(canvas: Canvas) {
        fill.color = 0xFF1B9D72.toInt()
        canvas.drawRoundRect(
            RectF(20f, 43f, 80f, 82f),
            6f,
            6f,
            fill,
        )

        fill.color = 0xFFFFA322.toInt()
        canvas.drawRect(24f, 23f, 76f, 39f, fill)
        val awning =
            Path().apply {
                moveTo(18f, 39f)
                lineTo(26f, 18f)
                lineTo(74f, 18f)
                lineTo(82f, 39f)
                close()
            }
        fill.color = 0xFFFFC24A.toInt()
        canvas.drawPath(awning, fill)

        fill.color = 0xFFF34A38.toInt()
        canvas.drawPath(
            Path().apply {
                moveTo(30f, 18f)
                lineTo(41f, 18f)
                lineTo(39f, 39f)
                lineTo(27f, 39f)
                close()
            },
            fill,
        )
        canvas.drawPath(
            Path().apply {
                moveTo(58f, 18f)
                lineTo(69f, 18f)
                lineTo(73f, 39f)
                lineTo(61f, 39f)
                close()
            },
            fill,
        )

        fill.color = 0xFF9DE7FF.toInt()
        canvas.drawRoundRect(
            RectF(27f, 52f, 48f, 68f),
            3f,
            3f,
            fill,
        )
        fill.color = 0xFF73452B.toInt()
        canvas.drawRoundRect(
            RectF(57f, 51f, 70f, 82f),
            3f,
            3f,
            fill,
        )

        detail.color = 0xFFEAFBFF.toInt()
        detail.strokeWidth = 3.5f
        canvas.drawPath(awning, detail)
        canvas.drawRoundRect(
            RectF(20f, 43f, 80f, 82f),
            6f,
            6f,
            detail,
        )
    }

    private fun drawBadge(canvas: Canvas) {
        fill.color = 0xFF7C3AD8.toInt()
        canvas.drawPath(
            Path().apply {
                moveTo(35f, 56f)
                lineTo(29f, 86f)
                lineTo(49f, 75f)
                lineTo(50f, 57f)
                close()
            },
            fill,
        )
        fill.color = 0xFFB653E8.toInt()
        canvas.drawPath(
            Path().apply {
                moveTo(65f, 56f)
                lineTo(71f, 86f)
                lineTo(51f, 75f)
                lineTo(50f, 57f)
                close()
            },
            fill,
        )

        fill.color = 0xFFFFC52C.toInt()
        canvas.drawCircle(50f, 40f, 25f, fill)
        fill.color = 0xFFFFE77A.toInt()
        canvas.drawCircle(50f, 40f, 19f, fill)

        fill.color = 0xFFB86A0B.toInt()
        drawPaw(canvas, 50f, 43f, 0.62f, fill)

        detail.color = 0xFFFFF2A8.toInt()
        detail.strokeWidth = 3f
        canvas.drawCircle(50f, 40f, 25f, detail)
    }

    private fun drawLeaderboard(canvas: Canvas) {
        fill.color = 0xFFFFB719.toInt()
        val cup =
            Path().apply {
                moveTo(31f, 20f)
                lineTo(69f, 20f)
                lineTo(64f, 49f)
                quadTo(60f, 63f, 50f, 67f)
                quadTo(40f, 63f, 36f, 49f)
                close()
            }
        canvas.drawPath(cup, fill)

        stroke.color = 0xFFFFD75E.toInt()
        stroke.strokeWidth = 6f
        canvas.drawArc(RectF(16f, 25f, 39f, 55f), 95f, 185f, false, stroke)
        canvas.drawArc(RectF(61f, 25f, 84f, 55f), -100f, 185f, false, stroke)

        fill.color = 0xFFFFC52C.toInt()
        canvas.drawRect(46f, 64f, 54f, 75f, fill)
        canvas.drawRoundRect(RectF(33f, 73f, 67f, 83f), 4f, 4f, fill)

        fill.color = 0xFFFFF2A8.toInt()
        drawFivePointStar(canvas, 50f, 38f, 10f, 4.5f, fill)
    }

    private fun drawAdBlocker(canvas: Canvas) {
        val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
                textSize = 29f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
        val blocker =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = 0xFFFF2C38.toInt()
                strokeWidth = 9f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

        canvas.drawText("ADS", 50f, 59f, textPaint)
        canvas.drawCircle(50f, 50f, 35f, blocker)
        canvas.drawLine(25f, 25f, 75f, 75f, blocker)
    }

    private fun drawRatingStar(canvas: Canvas) {
        fill.color = 0xFFFFCC26.toInt()
        drawFivePointStar(canvas, 48f, 51f, 33f, 14f, fill)
        fill.color = 0xFFFFF1A3.toInt()
        drawFivePointStar(canvas, 42f, 43f, 13f, 6f, fill)

        fill.color = 0xFFFFF4B2.toInt()
        canvas.drawCircle(80f, 26f, 4f, fill)
        canvas.drawCircle(85f, 38f, 2.5f, fill)
    }

    private fun drawShare(canvas: Canvas) {
        stroke.color = iconColor
        stroke.strokeWidth = 6f
        canvas.drawLine(34f, 47f, 64f, 31f, stroke)
        canvas.drawLine(34f, 53f, 64f, 69f, stroke)

        fill.color = iconColor
        canvas.drawCircle(26f, 50f, 9f, fill)
        canvas.drawCircle(72f, 27f, 9f, fill)
        canvas.drawCircle(72f, 73f, 9f, fill)
    }

    private fun drawSettings(canvas: Canvas) {
        stroke.color = iconColor
        stroke.strokeWidth = 8f
        canvas.drawCircle(50f, 50f, 24f, stroke)
        canvas.drawCircle(50f, 50f, 8f, stroke)

        stroke.strokeWidth = 8f
        for (index in 0 until 8) {
            val angle = Math.toRadians(index * 45.0)
            val x1 = 50f + cos(angle).toFloat() * 29f
            val y1 = 50f + sin(angle).toFloat() * 29f
            val x2 = 50f + cos(angle).toFloat() * 39f
            val y2 = 50f + sin(angle).toFloat() * 39f
            canvas.drawLine(x1, y1, x2, y2, stroke)
        }
    }

    private fun drawPaw(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        scale: Float,
        paint: Paint,
    ) {
        canvas.drawOval(
            RectF(
                centerX - 12f * scale,
                centerY - 2f * scale,
                centerX + 12f * scale,
                centerY + 16f * scale,
            ),
            paint,
        )
        canvas.drawCircle(centerX - 13f * scale, centerY - 8f * scale, 5f * scale, paint)
        canvas.drawCircle(centerX - 4f * scale, centerY - 13f * scale, 5f * scale, paint)
        canvas.drawCircle(centerX + 5f * scale, centerY - 13f * scale, 5f * scale, paint)
        canvas.drawCircle(centerX + 14f * scale, centerY - 7f * scale, 5f * scale, paint)
    }

    private fun drawFivePointStar(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        outerRadius: Float,
        innerRadius: Float,
        paint: Paint,
    ) {
        val star = Path()
        for (index in 0 until 10) {
            val radius = if (index % 2 == 0) outerRadius else innerRadius
            val angle = Math.toRadians(-90.0 + index * 36.0)
            val x = centerX + cos(angle).toFloat() * radius
            val y = centerY + sin(angle).toFloat() * radius
            if (index == 0) {
                star.moveTo(x, y)
            } else {
                star.lineTo(x, y)
            }
        }
        star.close()
        canvas.drawPath(star, paint)
    }
}

internal fun homeBlueCircularIconBackground(
    context: Context,
): StateListDrawable =
    StateListDrawable().apply {
        addState(
            intArrayOf(android.R.attr.state_pressed),
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    0xFF08498F.toInt(),
                    0xFF042E6F.toInt(),
                    0xFF021743.toInt(),
                ),
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(
                    LudoProofTheme.dp(context, 2),
                    0xFFFFD45E.toInt(),
                )
            },
        )
        addState(
            intArrayOf(),
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    0xFF0D66C8.toInt(),
                    0xFF063B8C.toInt(),
                    0xFF031D57.toInt(),
                ),
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(
                    LudoProofTheme.dp(context, 2),
                    0xFF58E3FF.toInt(),
                )
            },
        )
    }
