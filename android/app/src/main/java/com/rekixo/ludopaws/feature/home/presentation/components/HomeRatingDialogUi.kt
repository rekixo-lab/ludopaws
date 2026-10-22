package com.rekixo.ludopaws.ui.home

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.HomeActivity
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.ui.dialogs.baseDialog
import com.rekixo.ludopaws.ui.dialogs.sizeDialog
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Keeps the sequential fill independent from Android view state and frame rate. */
internal object RatingStarFillPolicy {
    const val MAX_STARS = 5
    fun fraction(progress: Float, starIndex: Int): Float =
        (progress - starIndex).coerceIn(0f, 1f)
}

/** Golden fluid surface rises inside each outlined star, bottom to top. */
internal object RatingLiquidFillPolicy {
    fun surfaceY(fraction: Float): Float =
        82f - 75f * fraction.coerceIn(0f, 1f)

    fun waveHeight(fraction: Float): Float =
        2.6f * sin(Math.PI.toFloat() * fraction.coerceIn(0f, 1f))
}

internal fun HomeActivity.showHomeRatingDialog() {
    if (isFinishing || isDestroyed) return

    val dialog = baseDialog(this)
    val compact = LudoProofTheme.isCompactWidth(this)
    val reduceMotion = GameSettingsStore(this).snapshot().reduceMotionEnabled
    val side = dp(if (compact) 12 else 16)
    val panel = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(side, dp(9), side, dp(12))
        clipChildren = false
        clipToPadding = false
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                0xFF1845A1.toInt(), 0xFF11185D.toInt(), 0xFF35166C.toInt(),
                0xFF071B4D.toInt(),
            ),
        ).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(2), 0xFFFFD96A.toInt())
        }
        elevation = dp(12).toFloat()
    }

    // A single, compact chrome row replaces the former oversized star badge.
    val closeRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val medallion = TextView(this).apply {
        text = "★"
        textSize = 22f
        setTextColor(0xFFFFE981.toInt())
        gravity = Gravity.CENTER
        includeFontPadding = false
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF8656FF.toInt(), 0xFF253FC0.toInt(), 0xFF152064.toInt()),
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(1), 0xFFFFE28E.toInt())
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    closeRow.addView(
        medallion,
        LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(9) },
    )
    closeRow.addView(
        TextView(this).apply {
            text = "RATE & REVIEW"
            textSize = 11f
            letterSpacing = .09f
            setTextColor(0xFF8FE9FF.toInt())
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER_VERTICAL
        },
        LinearLayout.LayoutParams(0, dp(30), 1f),
    )
    closeRow.addView(
        TextView(this).apply {
            text = "×"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Close rating dialog"
            isClickable = true
            isFocusable = true
            background = ratingCloseDrawable(this@showHomeRatingDialog)
            setOnClickListener { dialog.dismiss() }
        },
        LinearLayout.LayoutParams(dp(30), dp(30)),
    )
    panel.addView(
        closeRow,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    )

    panel.addView(
        TextView(this).apply {
            text = getString(R.string.rate_dialog_title)
            textSize = if (compact) 19f else 21f
            setTextColor(0xFFFFEAA4.toInt())
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0x990F051D.toInt())
        },
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(3) },
    )
    panel.addView(
        TextView(this).apply {
            text = getString(R.string.rate_dialog_question)
            textSize = if (compact) 14f else 15f
            setTextColor(Color.WHITE)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
        },
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(3) },
    )
    panel.addView(
        TextView(this).apply {
            text = "Tap a star to choose your rating"
            textSize = if (compact) 10.5f else 11f
            setTextColor(0xFFB9DDFB.toInt())
            gravity = Gravity.CENTER
        },
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(3) },
    )

    var selectedRating = RatingStarFillPolicy.MAX_STARS
    var displayedProgress = 0f
    var starAnimator: ValueAnimator? = null
    var shimmerAnimator: ValueAnimator? = null
    val stars = mutableListOf<RatingStarView>()
    val popped = BooleanArray(RatingStarFillPolicy.MAX_STARS)
    val starContainer = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        clipChildren = false
        clipToPadding = false
        setPadding(dp(3), dp(2), dp(3), dp(2))
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xB5091440.toInt(), 0xB71A2C71.toInt()),
        ).apply {
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), 0x887DCBFF.toInt())
        }
    }
    val ratingCaption = TextView(this).apply {
        text = "5 / 5  •  PAWSOME!"
        textSize = if (compact) 11.5f else 12f
        setTextColor(0xFFFFE28E.toInt())
        setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        gravity = Gravity.CENTER
    }
    val spectrum = RatingSpectrumDrawable(this)
    var rateAction: LinearLayout? = null
    val ctaText = TextView(this).apply {
        setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        setTextColor(Color.WHITE)
        textSize = if (compact) 11.5f else 12.5f
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        setSingleLine(true)
    }

    fun refreshCopy() {
        ctaText.text = if (selectedRating == 1) {
            "RATE 1 STAR ON PLAY STORE"
        } else {
            "RATE " + selectedRating + " STARS ON PLAY STORE"
        }
        ratingCaption.text = selectedRating.toString() + " / 5  •  " +
            if (selectedRating == 5) "PAWSOME!" else "THANK YOU!"
        rateAction?.contentDescription = "Rate $selectedRating stars on Google Play"
        stars.forEachIndexed { index, star ->
            star.isSelected = index + 1 == selectedRating
        }
    }

    fun applyProgress(progress: Float, allowPop: Boolean) {
        displayedProgress = progress.coerceIn(0f, RatingStarFillPolicy.MAX_STARS.toFloat())
        stars.forEachIndexed { index, star ->
            val fraction = RatingStarFillPolicy.fraction(displayedProgress, index)
            star.wavePhase = displayedProgress * 7.5f
            star.fillFraction = fraction
            if (allowPop && fraction > .06f && !popped[index]) {
                popped[index] = true
                star.animate().cancel()
                star.scaleX = .80f
                star.scaleY = .80f
                star.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(290L)
                    .setInterpolator(OvershootInterpolator(2.4f))
                    .start()
            }
        }
    }

    fun animateRating(to: Int, intro: Boolean = false) {
        starAnimator?.cancel()
        for (i in popped.indices) popped[i] =
            RatingStarFillPolicy.fraction(displayedProgress, i) > .06f
        val destination = to.toFloat()
        if (reduceMotion || abs(destination - displayedProgress) < .01f) {
            applyProgress(destination, allowPop = false)
            return
        }
        val distance = abs(destination - displayedProgress)
        starAnimator = ValueAnimator.ofFloat(displayedProgress, destination).apply {
            duration = if (intro) 2500L else (230f + distance * 260f).toLong()
            interpolator = LinearInterpolator()
            addUpdateListener {
                applyProgress(it.animatedValue as Float, allowPop = true)
            }
            start()
        }
    }

    repeat(RatingStarFillPolicy.MAX_STARS) { index ->
        val star = RatingStarView(this).apply {
            fillFraction = 0f
            contentDescription = (index + 1).toString() + " star rating"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedRating = index + 1
                refreshCopy()
                animateRating(selectedRating)
            }
        }
        stars += star
        starContainer.addView(
            star,
            LinearLayout.LayoutParams(0, dp(if (compact) 43 else 47), 1f).apply {
                marginStart = dp(1)
                marginEnd = dp(1)
            },
        )
    }
    panel.addView(
        starContainer,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(if (compact) 11 else 13)
        },
    )
    panel.addView(
        ratingCaption,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(4) },
    )

    val cta = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = spectrum
        elevation = dp(6).toFloat()
        isClickable = true
        isFocusable = true
        contentDescription = "Rate 5 stars on Google Play"
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener {
            dialog.dismiss()
            // This opens the Google Play listing. It does not submit a review.
            openPlayStoreRating(this@showHomeRatingDialog)
        }
        addView(
            HomeGooglePlayIconView(this@showHomeRatingDialog).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(dp(24), dp(28)).apply {
                marginEnd = dp(6)
            },
        )
        addView(
            LinearLayout(this@showHomeRatingDialog).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(ctaText)
                addView(
                    TextView(this@showHomeRatingDialog).apply {
                        text = "CONTINUE TO GOOGLE PLAY"
                        textSize = if (compact) 8.5f else 9.2f
                        letterSpacing = .07f
                        setTextColor(0xFFF4F7FF.toInt())
                    },
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }
    rateAction = cta
    panel.addView(
        cta,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(if (compact) 49 else 54),
        ).apply {
            topMargin = dp(10)
            leftMargin = dp(if (compact) 0 else 6)
            rightMargin = dp(if (compact) 0 else 6)
        },
    )

    panel.addView(
        TextView(this).apply {
            text = "Not now"
            textSize = if (compact) 13f else 14f
            setTextColor(0xFFC4D0EE.toInt())
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "Not now"
            setPadding(dp(8), dp(7), dp(8), 0)
            setOnClickListener { dialog.dismiss() }
        },
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    )

    refreshCopy()
    // The stars are intentionally dim at the first rendered frame.
    applyProgress(if (reduceMotion) selectedRating.toFloat() else 0f, allowPop = false)
    dialog.setOnDismissListener {
        starAnimator?.cancel()
        shimmerAnimator?.cancel()
        panel.animate().cancel()
        medallion.animate().cancel()
        stars.forEach { it.animate().cancel() }
    }
    dialog.setContentView(panel)
    sizeDialog(dialog, .88f)
    // Prepare transparent entrance BEFORE showing the window: no first-frame flash.
    if (!reduceMotion) {
        panel.alpha = 0f
        panel.scaleX = .90f
        panel.scaleY = .90f
        panel.translationY = dp(16).toFloat()
        medallion.scaleX = .72f
        medallion.scaleY = .72f
    }
    dialog.show()

    if (!reduceMotion) {
        panel.animate()
            .alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(500L)
            .setInterpolator(OvershootInterpolator(.90f))
            .start()
        medallion.animate().scaleX(1f).scaleY(1f)
            .setStartDelay(180L)
            .setDuration(600L)
            .setInterpolator(OvershootInterpolator(1.9f))
            .start()
        // Start after layout but before the user sees a completed star row.
        starContainer.postDelayed({
            if (dialog.isShowing) animateRating(selectedRating, intro = true)
        }, 430L)
        shimmerAnimator = ValueAnimator.ofFloat(-.35f, 1.35f).apply {
            duration = 2750L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                spectrum.shimmerPosition = it.animatedValue as Float
            }
            start()
        }
    }
}

private fun openPlayStoreRating(
    activity: Activity,
) {
    val packageName =
        activity.packageName
    val marketIntent =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                "market://details?id=" +
                    packageName,
            ),
        ).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
            )
        }

    val openedMarket =
        runCatching {
            activity.startActivity(
                marketIntent,
            )
        }
            .isSuccess

    if (
        openedMarket
    ) {
        return
    }

    runCatching {
        activity.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://play.google.com/store/apps/details?id=" +
                        packageName,
                ),
            ),
        )
    }
}


/**
 * Tinted-to-gold star. The filled portion is clipped from the LEFT edge,
 * so each star visibly fills left-to-right rather than simply changing tint.
 */
/** Five empty navy outlines receive a visible rising gold-liquid wave in sequence. */
private class RatingStarView(context: Context) : View(context) {
    var fillFraction = 0f
        set(value) {
            val next = value.coerceIn(0f, 1f)
            if (field != next) {
                field = next
                invalidate()
            }
        }
    var wavePhase = 0f
        set(value) {
            field = value
            if (fillFraction in .001f..0.999f) invalidate()
        }

    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF253466.toInt()
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.2f
        strokeJoin = Paint.Join.ROUND
        color = 0xFF8096CC.toInt()
    }
    private val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 100f, 100f,
            intArrayOf(0xFFFFFFD3.toInt(), 0xFFFFE764.toInt(), 0xFFFFA51E.toInt()),
            null, Shader.TileMode.CLAMP,
        )
    }
    private val goldBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.4f
        strokeJoin = Paint.Join.ROUND
        color = 0xFFFFEEAA.toInt()
    }
    private val waveHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.6f
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFF8C4.toInt()
    }
    private val sparkle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xEAFFFFFF.toInt()
    }
    private val star = Path().apply {
        for (index in 0 until 10) {
            val radius = if (index % 2 == 0) 38f else 17.5f
            val angle = Math.toRadians(-90.0 + index * 36.0)
            val x = 50f + cos(angle).toFloat() * radius
            val y = 50f + sin(angle).toFloat() * radius
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    private val fluid = Path()
    private val surface = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        canvas.save()
        canvas.translate((width - size) / 2f, (height - size) / 2f)
        canvas.scale(size / 100f, size / 100f)

        // Empty star is visibly a recessed navy silhouette and pale outline.
        canvas.drawPath(star, tintPaint)
        if (fillFraction > 0f) {
            val y = RatingLiquidFillPolicy.surfaceY(fillFraction)
            val amplitude = RatingLiquidFillPolicy.waveHeight(fillFraction)
            fluid.reset()
            surface.reset()
            for (step in 0..26) {
                val x = step * 4f
                val waveY = y + sin(x * .18f + wavePhase) * amplitude
                if (step == 0) {
                    fluid.moveTo(x, waveY)
                    surface.moveTo(x, waveY)
                } else {
                    fluid.lineTo(x, waveY)
                    surface.lineTo(x, waveY)
                }
            }
            fluid.lineTo(104f, 104f)
            fluid.lineTo(0f, 104f)
            fluid.close()

            // Clip twice: inside the star silhouette AND below the moving
            // liquid surface. This paints a real filling shape, not a tint.
            canvas.save()
            canvas.clipPath(star)
            canvas.clipPath(fluid)
            canvas.drawPath(star, gold)
            canvas.restore()

            if (fillFraction > .02f && fillFraction < .98f) {
                canvas.save()
                canvas.clipPath(star)
                canvas.drawPath(surface, waveHighlight)
                canvas.restore()
            }
            if (fillFraction >= .99f) {
                canvas.drawCircle(39f, 33f, 3.7f, sparkle)
            }
        }
        canvas.drawPath(star, if (fillFraction >= .99f) goldBorder else line)
        canvas.restore()
    }
}

/** Animated rainbow/gradient CTA with a moving white sheen and gold rim. */
private class RatingSpectrumDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFECAE.toInt()
        strokeWidth = 2f * density
        style = Paint.Style.STROKE
    }
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x90FFFFFF.toInt()
        strokeWidth = density
        style = Paint.Style.STROKE
    }
    private val rect = RectF()
    private val inset = RectF()
    private val outlinePath = Path()
    var shimmerPosition = -.35f
        set(value) {
            field = value
            invalidateSelf()
        }
    private var pressed = false

    override fun isStateful(): Boolean = true

    override fun onStateChange(stateSet: IntArray): Boolean {
        val next = stateSet.contains(android.R.attr.state_pressed)
        if (pressed == next) return false
        pressed = next
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        rect.inset(2.5f * density, 2.5f * density)
        if (rect.width() <= 0 || rect.height() <= 0) return
        val radius = rect.height() / 2f
        outlinePath.reset()
        outlinePath.addRoundRect(rect, radius, radius, Path.Direction.CW)

        body.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            if (pressed) intArrayOf(
                0xFF244AC6.toInt(), 0xFF6731BA.toInt(),
                0xFFBB397D.toInt(), 0xFFCC7719.toInt(),
            ) else intArrayOf(
                0xFF277DFF.toInt(), 0xFF774AF1.toInt(),
                0xFFD62BB4.toInt(), 0xFFF6A52C.toInt(),
            ),
            null, Shader.TileMode.CLAMP,
        )
        canvas.drawPath(outlinePath, body)
        body.shader = null

        // A soft specular sweep stays clipped to the rounded glass button.
        if (!pressed) {
            val x = rect.left + rect.width() * shimmerPosition
            shine.shader = LinearGradient(
                x - rect.width() * .34f, rect.top,
                x + rect.width() * .34f, rect.bottom,
                intArrayOf(0x00FFFFFF, 0x65FFFFFF, 0x00FFFFFF),
                null, Shader.TileMode.CLAMP,
            )
            canvas.save()
            canvas.clipPath(outlinePath)
            canvas.drawRect(rect, shine)
            canvas.restore()
            shine.shader = null
        }

        canvas.drawRoundRect(rect, radius, radius, rim)
        inset.set(rect)
        inset.inset(3.8f * density, 3.8f * density)
        canvas.drawRoundRect(inset, inset.height() / 2f, inset.height() / 2f, inner)
    }

    override fun setAlpha(alpha: Int) {
        body.alpha = alpha
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setColorFilter(colorFilter: ColorFilter?) {
        body.colorFilter = colorFilter
        invalidateSelf()
    }
}

private fun ratingCloseDrawable(context: Context): StateListDrawable =
    StateListDrawable().apply {
        fun circle(color: Int): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(LudoProofTheme.dp(context, 1), 0xFFFFD96A.toInt())
        }
        addState(intArrayOf(android.R.attr.state_pressed), circle(0xFF6742B2.toInt()))
        addState(intArrayOf(), circle(0xFF172960.toInt()))
    }
