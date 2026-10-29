package com.rekixo.ludopaws.feature.startup

import android.animation.ValueAnimator
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
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.R

/** Preparation UI only. It never initializes an identity/ad SDK or fabricates timed progress. */
class GameStartupSplashView(
    activity: Activity,
    retry: () -> Unit,
    exit: () -> Unit,
    private val firstFrame: () -> Unit = {},
) : FrameLayout(activity) {
    private var firstFrameReported = false
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
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF102C61.toInt(), 0xFF071B43.toInt(), 0xFF030E28.toInt()))
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
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
            setPadding(dp(12), dp(18), dp(12), dp(18))
        }, LayoutParams(dp(300), -1, Gravity.CENTER).apply {
            // The surrounding center clips/constrains artwork on short windows.
            width = minOf(dp(300), resources.displayMetrics.widthPixels - dp(48))
        })
        column.addView(caption, LinearLayout.LayoutParams(-1, -2))
        column.addView(progress, LinearLayout.LayoutParams(-1, dp(24)).apply { topMargin = dp(12) })
        column.addView(actions, LinearLayout.LayoutParams(-1, -2))
        column.addView(clearance, LinearLayout.LayoutParams(1, 0))
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
            // Start its own visibility interval only after the native launch
            // gate has allowed a real game-splash frame to be drawn.
            post { if (isAttachedToWindow) firstFrame() }
        }
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
    private val radius = 26f * resources.displayMetrics.density

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
    private var phase = 0f
    private var gradient: Shader? = null
    private val shimmer = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init { contentDescription = "Preparing game" }

    fun setLoading(value: Boolean) {
        loading = value
        contentDescription = if (value) "Preparing game" else "Game ready"
        syncAnimation()
        invalidate()
    }

    private fun syncAnimation() {
        if (loading && isAttachedToWindow && isShown && windowVisibility == VISIBLE) {
            if (!shimmer.isStarted) shimmer.start()
        } else shimmer.cancel()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncAnimation() }
    override fun onDetachedFromWindow() { shimmer.cancel(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) syncAnimation()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Android can dispatch visibility while View's constructor is running.
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
        val radius = track.height() / 2f
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = 0xFF071126.toInt()
        canvas.drawRoundRect(track, radius, radius, paint)
        val checkpoint = canvas.save()
        canvas.clipPath(clip)
        val segment = track.width() * 0.38f
        val left = if (loading) track.left - segment + phase * (track.width() + segment) else track.left
        fill.set(left, track.top, if (loading) left + segment else track.right, track.bottom)
        paint.shader = gradient
        canvas.drawRoundRect(fill, radius, radius, paint)
        paint.shader = null
        paint.color = 0x55FFFFFF
        canvas.drawRoundRect(fill.left, track.top + density, fill.right, track.centerY(), radius, radius, paint)
        // Four Ludo-color markers give the frame the same playful palette as the logo.
        canvas.restoreToCount(checkpoint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = 0xFFEBC477.toInt()
        canvas.drawRoundRect(track, radius, radius, paint)
        paint.style = Paint.Style.FILL
        intArrayOf(0xFFFF5959.toInt(), 0xFF65D895.toInt(), 0xFFFFD76A.toInt(), 0xFF5DB8FF.toInt()).forEachIndexed { index, color ->
            paint.color = color
            canvas.drawCircle(track.centerX() + (index - 1.5f) * 7f * density, track.centerY(), 1.5f * density, paint)
        }
    }
}
