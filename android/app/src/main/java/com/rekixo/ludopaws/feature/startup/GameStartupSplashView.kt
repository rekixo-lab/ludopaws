package com.rekixo.ludopaws.feature.startup

import android.app.Activity
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
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
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground

/** Preparation UI only. Fill interpolates readiness milestones; no identity/ad SDK starts here. */
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
        setTextColor(0xFF422B1C.toInt())
        setShadowLayer(dp(1).toFloat(), 0f, dp(1).toFloat(), 0x66FFF1D7)
        gravity = Gravity.CENTER
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val caption = TextView(context).apply {
        text = "Preparing game artwork…"
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(0xFF523B28.toInt())
        gravity = Gravity.CENTER
    }
    private val progress = PawLoadingTrack(activity)
    private val progressFrame = FrameLayout(context).apply {
        background = recessedPlywoodBackground(context, outerCornerRadiusDp = 18f)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        addView(progress, LayoutParams(-1, -1))
    }
    private val actions = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        visibility = View.GONE
        addView(Button(context).apply { text = "Retry"; setOnClickListener { retry() } })
        addView(Button(context).apply { text = "Exit"; setOnClickListener { exit() } })
    }
    private val clearance = View(context)

    init {
        background = plywoodBackground(context, cornerRadiusDp = 0f, rimThicknessDp = 0f)
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
            setPadding(dp(6), dp(9), dp(6), dp(9))
        }, LayoutParams(dp(150), -1, Gravity.CENTER).apply {
            // Halve both width and horizontal padding so visible artwork is half-size.
            width = minOf(dp(300), resources.displayMetrics.widthPixels - dp(48)) / 2
        })
        column.addView(caption, LinearLayout.LayoutParams(-1, -2))
        column.addView(progressFrame, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(12) })
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
        progress.setReadiness(localReady, account.settled)
    }

    fun finishPreparation(onComplete: () -> Unit) {
        progress.complete(onComplete)
    }

    fun showPreparationError() {
        status.text = "Couldn't prepare the game"
        caption.text = "Try again to load the game artwork."
        progressFrame.visibility = View.GONE
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

/** Empty recessed wood fills from the left; milestones set a monotonic visual target. */
private class PawLoadingTrack(activity: Activity) : View(activity) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private val fill = RectF()
    private val clip = Path()
    private var target = 0f
    private var displayed = 0f
    private var lastFrameAt = 0L
    private var gradient: Shader? = null
    private var depthGradient: Shader? = null
    private var completion: (() -> Unit)? = null

    init { contentDescription = "Preparing game" }

    fun setReadiness(localReady: Boolean, accountSettled: Boolean) {
        // These are preparation milestones, not measured download percentages.
        val milestone = when {
            localReady && accountSettled -> 1f
            localReady -> 0.7f
            accountSettled -> 0.25f
            else -> 0.15f
        }
        target = maxOf(target, milestone)
        syncAnimation()
    }

    fun complete(onComplete: () -> Unit) {
        // Home calls this only after real readiness/offline-grace and minimum
        // visibility gates pass. Let the remaining fill finish before leaving.
        target = 1f
        completion = onComplete
        syncAnimation()
    }

    private fun canAnimate() = isAttachedToWindow && isShown && windowVisibility == VISIBLE

    private fun syncAnimation() {
        if (canAnimate()) postInvalidateOnAnimation() else lastFrameAt = 0L
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncAnimation() }
    override fun onDetachedFromWindow() { lastFrameAt = 0L; completion = null; super.onDetachedFromWindow() }
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
            intArrayOf(0xFFFF655B.toInt(), 0xFFFFD35F.toInt(), 0xFF5BDF9F.toInt(), 0xFF45A8FF.toInt()),
            null, Shader.TileMode.CLAMP)
        depthGradient = LinearGradient(0f, track.top, 0f, track.bottom.coerceAtLeast(track.top + 1f),
            intArrayOf(0x88FFFFFF.toInt(), 0x00FFFFFF, 0x55001D3F),
            floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (track.width() <= 0 || track.height() <= 0) return
        val now = SystemClock.uptimeMillis()
        // First frame is genuinely empty, even when preparation finished while
        // the native launch covered this View. Never jump forward after pause.
        if (lastFrameAt != 0L) {
            val step = (now - lastFrameAt).coerceIn(0L, 50L) / 1_000f
            displayed = minOf(target, displayed + step)
        }
        lastFrameAt = now
        val radius = track.height() / 2f
        if (displayed > 0f) {
            val checkpoint = canvas.save()
            canvas.clipPath(clip)
            fill.set(track.left, track.top, track.left + track.width() * displayed, track.bottom)
            val cap = minOf(radius, fill.width() / 2f)
            paint.style = Paint.Style.FILL
            paint.shader = gradient
            canvas.drawRoundRect(fill, cap, cap, paint)
            // Fixed lighting belongs to the growing fill itself; there is no
            // separate moving shadow or already-colored empty track.
            paint.shader = depthGradient
            canvas.drawRoundRect(fill, cap, cap, paint)
            canvas.restoreToCount(checkpoint)
        }
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = 0xFFEBC477.toInt()
        canvas.drawRoundRect(track, radius, radius, paint)
        paint.style = Paint.Style.FILL
        if (displayed >= 1f) {
            contentDescription = "Game ready"
            completion?.let { callback ->
                completion = null
                post { if (isAttachedToWindow) callback() }
            }
        }
        if (canAnimate() && displayed < target) postInvalidateOnAnimation()
    }
}
