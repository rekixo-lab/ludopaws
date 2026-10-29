package com.rekixo.ludopaws.feature.startup

import android.app.Activity
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.core.ui.art.plywoodBackground

/** Preparation UI only. It never initializes an identity/ad SDK or fabricates timed progress. */
class GameStartupSplashView(
    activity: Activity,
    retry: () -> Unit,
    exit: () -> Unit,
    showLaunchBranding: Boolean = false,
    private val firstFrame: () -> Unit = {},
) : FrameLayout(activity) {
    private var firstFrameReported = false
    private var brandingVisible = showLaunchBranding
    private var launchLogo: ImageView? = null
    private lateinit var contentColumn: LinearLayout
    private val gameBackground = GradientDrawable(GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xFF102C61.toInt(), 0xFF071B43.toInt(), 0xFF030E28.toInt()))
    private val finishBranding = Runnable {
        if (isAttachedToWindow && brandingVisible) {
            brandingVisible = false
            launchLogo?.let { removeView(it) }
            launchLogo = null
            background = gameBackground
            contentColumn.visibility = View.VISIBLE
            firstFrameReported = false
            invalidate()
        }
    }
    private fun dp(value: Int) = LudoProofTheme.dp(context, value)
    private val status = TextView(context).apply {
        text = "Preparing Ludo Paws…"
        textSize = 21f
        typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        letterSpacing = 0.035f
        setTextColor(0xFFFFE6A8.toInt())
        setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0x99000000.toInt())
        gravity = Gravity.CENTER
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val caption = TextView(context).apply {
        text = "Preparing game artwork…"
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(0xFFD4E6FF.toInt())
        gravity = Gravity.CENTER
    }
    private val progress = PawLoadingTrack(activity)
    private val actions = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        visibility = View.GONE
        addView(Button(context).apply { text = "Retry"; setOnClickListener { retry() } })
        addView(Button(context).apply { text = "Exit"; setOnClickListener { exit() } })
    }
    private val clearance = View(context)

    init {
        background = if (brandingVisible) plywoodBackground(context, cornerRadiusDp = 0f, rimThicknessDp = 0f) else gameBackground
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        contentColumn = column
        column.visibility = if (brandingVisible) View.INVISIBLE else View.VISIBLE
        addView(column, LayoutParams(-1, -1))
        column.addView(status, LinearLayout.LayoutParams(-1, -2))
        val center = FrameLayout(context)
        column.addView(center, LinearLayout.LayoutParams(-1, 0, 1f))
        center.addView(RoundedSplashLogo(activity).apply {
            // Use the actual game logo, never the Home hero/preparation bitmap.
            setImageResource(R.drawable.ic_ludo_paws)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Ludo Paws game logo"
            adjustViewBounds = true
            setPadding(dp(6), dp(9), dp(6), dp(9))
        }, LayoutParams(dp(150), -1, Gravity.CENTER).apply {
            // Halve both width and horizontal padding so visible artwork is half-size.
            width = minOf(dp(300), resources.displayMetrics.widthPixels - dp(48)) / 2
        })
        column.addView(caption, LinearLayout.LayoutParams(-1, -2))
        column.addView(progress, LinearLayout.LayoutParams(-1, dp(24)).apply { topMargin = dp(12) })
        column.addView(actions, LinearLayout.LayoutParams(-1, -2))
        column.addView(clearance, LinearLayout.LayoutParams(1, 0))
        if (brandingVisible) {
            launchLogo = ImageView(context).apply {
                setImageResource(R.drawable.ic_rekixo_launch)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Rekixo"
            }.also { addView(it, LayoutParams(minOf(dp(330), resources.displayMetrics.widthPixels - dp(48)), dp(288), Gravity.CENTER)) }
        }
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left + dp(24), safe.top + dp(24), safe.right + dp(24), safe.bottom)
            insets
        }
        setPadding(dp(24), dp(24), dp(24), 0)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!firstFrameReported) {
            firstFrameReported = true
            if (brandingVisible) {
                // Android 12+ native splash only supports a solid background.
                // Draw the shared full-screen wood in-app for one second,
                // then begin the separate game-splash visibility interval.
                postDelayed(finishBranding, 1_000L)
            } else post { if (isAttachedToWindow) firstFrame() }
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(finishBranding)
        if (brandingVisible) firstFrameReported = false
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Reserve the gap BEFORE measuring the weighted column. Updating a
        // spacer from onSizeChanged happens during layout, after child measure,
        // and can leave its initial zero height visible for the whole splash.
        val h = MeasureSpec.getSize(heightMeasureSpec)
        // Approx. 1.5 physical inches above the bottom safe edge. Cap the gap
        // on landscape/split-screen windows to leave the logo and actions usable.
        val inches = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_IN, 1.5f, resources.displayMetrics).toInt()
        val gap = minOf(inches, ((h - paddingTop - paddingBottom).coerceAtLeast(0) * 0.35f).toInt())
        clearance.layoutParams.height = gap
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun update(account: StartupAccountSnapshot, localReady: Boolean) {
        status.text = "Preparing Ludo Paws…"
        caption.text = when {
            !localReady -> "Preparing game artwork…"
            !account.settled -> "Preparing — offline play available"
            else -> "Game ready"
        }
        progress.setLoading(!localReady || !account.settled)
    }

    fun showPreparationError() {
        status.text = "Couldn't prepare the game"
        caption.text = "Try again to load the game artwork."
        progress.visibility = View.GONE
        actions.visibility = View.VISIBLE
    }
}

/** Clip the actual FIT_CENTER artwork, not the larger ImageView letterbox. */
private class RoundedSplashLogo(activity: Activity) : ImageView(activity) {
    private val artworkBounds = RectF()
    private val roundedArtwork = Path()
    private val radius = 36f * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val art = drawable ?: return
        if (art.intrinsicWidth <= 0 || art.intrinsicHeight <= 0) {
            super.onDraw(canvas)
            return
        }
        artworkBounds.set(0f, 0f, art.intrinsicWidth.toFloat(), art.intrinsicHeight.toFloat())
        imageMatrix.mapRect(artworkBounds)
        artworkBounds.offset(paddingLeft.toFloat(), paddingTop.toFloat())
        roundedArtwork.reset()
        val corner = minOf(radius, artworkBounds.width() / 2f, artworkBounds.height() / 2f)
        roundedArtwork.addRoundRect(artworkBounds, corner, corner, Path.Direction.CW)
        val checkpoint = canvas.save()
        canvas.clipPath(roundedArtwork)
        super.onDraw(canvas)
        canvas.restoreToCount(checkpoint)
    }
}

/** A gold-rimmed game track. The moving highlight means unknown work, not percent complete. */
private class PawLoadingTrack(activity: Activity) : View(activity) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private val fill = RectF()
    private val clip = Path()
    private var loading = true
    private var animationStartedAt = 0L
    private var gradient: Shader? = null
    private val markerColors = intArrayOf(0xFFFF5959.toInt(), 0xFF65D895.toInt(), 0xFFFFD76A.toInt(), 0xFF5DB8FF.toInt())

    init { contentDescription = "Preparing game" }

    fun setLoading(value: Boolean) {
        loading = value
        contentDescription = if (value) "Preparing game" else "Game ready"
        syncAnimation()
        invalidate()
    }

    private fun canAnimate() = isAttachedToWindow && isShown && windowVisibility == VISIBLE

    private fun syncAnimation() {
        if (canAnimate()) postInvalidateOnAnimation() else animationStartedAt = 0L
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncAnimation() }
    override fun onDetachedFromWindow() { animationStartedAt = 0L; super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) syncAnimation()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) syncAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        track.set(2f * density, 2f * density, w - 2f * density, h - 2f * density)
        clip.reset()
        clip.addRoundRect(track, track.height() / 2f, track.height() / 2f, Path.Direction.CW)
        gradient = LinearGradient(0f, 0f, w.coerceAtLeast(1).toFloat(), 0f,
            intArrayOf(0xFF178EFF.toInt(), 0xFF37D5EE.toInt(), 0xFF71F3BF.toInt()), null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (track.width() <= 0 || track.height() <= 0) return
        // Start on the first drawn frame, not attach (which can happen while
        // the native launch is still covering this View). Native VSYNC drives
        // redraws directly; no ValueAnimator can remain stopped at phase zero.
        val now = SystemClock.uptimeMillis()
        if (animationStartedAt == 0L) animationStartedAt = now
        val cycle = ((now - animationStartedAt) % 1_600L).toFloat() / 1_600f
        val phase = ((1.0 - kotlin.math.cos(cycle * 2.0 * Math.PI)) * 0.5).toFloat()
        val radius = track.height() / 2f
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = 0xFF071126.toInt()
        canvas.drawRoundRect(track, radius, radius, paint)
        val checkpoint = canvas.save()
        canvas.clipPath(clip)
        val segment = track.width() * 0.38f
        val movingLeft = track.left + phase * (track.width() - segment)
        val left = if (loading) movingLeft else track.left
        fill.set(left, track.top, if (loading) left + segment else track.right, track.bottom)
        paint.shader = gradient
        canvas.drawRoundRect(fill, radius, radius, paint)
        paint.shader = null
        paint.color = 0x55FFFFFF
        canvas.drawRoundRect(fill.left, track.top + density, fill.right, track.centerY(), radius, radius, paint)
        // Once ready, retain the truthful full track and only move a soft
        // highlight while the one-second presentation interval finishes.
        if (!loading) {
            paint.color = 0x66FFFFFF
            canvas.drawRoundRect(movingLeft, track.top, movingLeft + segment, track.bottom, radius, radius, paint)
        }
        // Four Ludo-color markers give the frame the same playful palette as the logo.
        canvas.restoreToCount(checkpoint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = 0xFFEBC477.toInt()
        canvas.drawRoundRect(track, radius, radius, paint)
        paint.style = Paint.Style.FILL
        markerColors.forEachIndexed { index, color ->
            paint.color = color
            canvas.drawCircle(track.centerX() + (index - 1.5f) * 7f * density, track.centerY(), 1.5f * density, paint)
        }
        if (canAnimate()) postInvalidateOnAnimation()
    }
}
