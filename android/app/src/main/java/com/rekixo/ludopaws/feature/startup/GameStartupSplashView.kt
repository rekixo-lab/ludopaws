package com.rekixo.ludopaws.feature.startup

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.rekixo.ludopaws.LudoProofTheme

/** Preparation UI only. It never initializes an identity/ad SDK or fabricates timed progress. */
class GameStartupSplashView(activity: Activity, retry: () -> Unit, exit: () -> Unit) : FrameLayout(activity) {
    private fun dp(value: Int) = LudoProofTheme.dp(context, value)
    private val status = TextView(context).apply {
        text = "Preparing your game…"
        textSize = 17f
        setTextColor(0xFFEED9B7.toInt())
        gravity = Gravity.CENTER
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val fallbackTitle = TextView(context).apply {
        text = "Ludo Paws"
        textSize = 36f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(LudoProofTheme.GOLD)
        gravity = Gravity.CENTER
    }
    private val logo = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "Ludo Paws"
    }
    private val caption = TextView(context).apply {
        text = "Preparing game artwork…"
        textSize = 13f
        setTextColor(0xFFD4E6FF.toInt())
        gravity = Gravity.CENTER
    }
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        isIndeterminate = true
        contentDescription = "Preparing game"
    }
    private val actions = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        visibility = View.GONE
        addView(Button(context).apply { text = "Retry"; setOnClickListener { retry() } })
        addView(Button(context).apply { text = "Exit"; setOnClickListener { exit() } })
    }

    init {
        setBackgroundColor(0xFF03153F.toInt())
        addView(status, LayoutParams(-1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        addView(fallbackTitle, LayoutParams(-1, -1, Gravity.CENTER).apply { topMargin = dp(80); bottomMargin = dp(120) })
        addView(logo, LayoutParams(-1, -1, Gravity.CENTER).apply { topMargin = dp(80); bottomMargin = dp(120) })
        val bottom = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(caption, LinearLayout.LayoutParams(-1, -2))
            addView(progress, LinearLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(12) })
            addView(actions, LinearLayout.LayoutParams(-1, -2))
        }
        addView(bottom, LayoutParams(-1, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(safe.left + dp(24), safe.top + dp(24), safe.right + dp(24), safe.bottom + dp(24))
            insets
        }
        setPadding(dp(24), dp(24), dp(24), dp(24))
    }

    fun artworkReady(bitmap: Bitmap) {
        logo.setImageBitmap(bitmap)
        fallbackTitle.visibility = View.GONE
    }

    fun update(account: StartupAccountSnapshot, localReady: Boolean, playGames: PlayGamesStartupState) {
        status.text = playGames.message
        caption.text = when {
            !localReady -> "Preparing game artwork…"
            !account.settled || !playGames.settled -> "Preparing — offline play available"
            else -> "Game ready"
        }
        progress.isIndeterminate = !localReady || !account.settled || !playGames.settled
        if (!progress.isIndeterminate) { progress.max = 1; progress.progress = 1 }
    }

    fun showPreparationError() {
        status.text = "Couldn't prepare the game"
        caption.text = "Try again to load the game artwork."
        progress.visibility = View.GONE
        actions.visibility = View.VISIBLE
    }
}
