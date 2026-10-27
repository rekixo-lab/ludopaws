package com.rekixo.ludopaws.feature.referrals

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.DiceView
import com.rekixo.ludopaws.LudoBoardView
import com.rekixo.ludopaws.LudoPawsBoardChromeView
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.feature.store.presentation.components.CosmeticPriceLabels
import com.rekixo.ludopaws.feature.store.presentation.components.FeaturedPawsPreview

internal class FriendshipBundleView(context: Context, private val compact: Boolean = false) : LinearLayout(context) {
    private var live: FeaturedPawsPreview? = null
    private var active = false
    private val avatar = LudoPawsCharacterAvatarView(context).apply {
        bind("rabbit", showCircularChrome = false, showTeamBadge = false)
    }
    private val rabbit = FrameLayout(context)
    private val startPreview = Runnable {
        if (canShowMotion() && live == null) {
            try {
                live = FeaturedPawsPreview(context, 66L, playful = true).also { preview ->
                    preview.characterId = "rabbit"
                    preview.alpha = 0f
                    preview.onReady = { preview.alpha = 1f; avatar.visibility = INVISIBLE }
                    preview.onUnavailable = { preview.alpha = 0f; avatar.visibility = VISIBLE }
                    rabbit.addView(preview, FrameLayout.LayoutParams(-1, -1))
                }
            } catch (_: Exception) { avatar.visibility = VISIBLE }
        }
        live?.setActive(canShowMotion())
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(if (compact) 10 else 16), dp(12), dp(if (compact) 10 else 14))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            if (compact) intArrayOf(0xFF183F44.toInt(), 0xFF325B55.toInt(), 0xFF26384E.toInt())
            else intArrayOf(0xFF28584B.toInt(), 0xFF1D403E.toInt(), 0xFF1B3542.toInt())).apply {
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), if (compact) 0xFFE7CE99.toInt() else 0x557DAD8C)
        }
        addView(label(if (compact) "FRIENDSHIP BUNDLE" else "YOUR FRIENDSHIP BUNDLE", if (compact) 16f else 11f, true).apply {
            if (!compact) letterSpacing = 0.12f
        }, LayoutParams(-1, -2))
        val art = object : LinearLayout(context) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
        }.apply { gravity = Gravity.CENTER_VERTICAL }
        val board = FrameLayout(context).apply {
            addView(LudoBoardView(context).apply {
                setBoardThemeOverride("board_blossom_garden")
                setClassicTokenDrawingEnabled(false)
            }, FrameLayout.LayoutParams(-1, -1))
            addView(LudoPawsBoardChromeView(context).apply { setBoardThemeOverride("board_blossom_garden") }, FrameLayout.LayoutParams(-1, -1))
            contentDescription = "Blossom Garden Board"
        }
        rabbit.addView(avatar, FrameLayout.LayoutParams(-1, -1))
        rabbit.contentDescription = "Rabbit character"
        val dice = DiceView(context).apply {
            setPreviewStyle("dice_pearl_paw")
            showOutcome(1, animate = false)
            setPlayerTeamColor(null)
            contentDescription = "Pearl Paw Dice"
        }
        art.addView(board, LayoutParams(0, dp(if (compact) 76 else 84), 1f))
        art.addView(rabbit, LayoutParams(0, -1, 1.3f))
        art.addView(dice, LayoutParams(0, dp(if (compact) 76 else 84), 1f))
        addView(art, if (compact) LayoutParams(-1, 0, 1f) else LayoutParams(-1, dp(132)))
        addView(label("+ ", 18f, true).apply {
            text = android.text.SpannableStringBuilder("+ ").append(CosmeticPriceLabels.gems(context, 100, " Gems"))
            contentDescription = "Plus 100 Gems"
        }, LayoutParams(-1, -2))
        addView(label(if (compact) "100 Gems + missing bundle items · Invite & play" else "Board  ·  Rabbit  ·  Dice", if (compact) 11f else 12f).apply {
            if (!compact) setPadding(0, dp(5), 0, 0)
        }, LayoutParams(-1, -2))
        if (compact) addView(label("SEE BUNDLE  ›", 13f, true), LayoutParams(-1, -2).apply { topMargin = dp(4) })
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Friendship bundle: Pearl Paw Dice, Blossom Garden Board, Rabbit and 100 Gems. Invite a new friend. Reward only after verified play."
    }

    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(if (bold) 0xFFFFE8B7.toInt() else Color.WHITE)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        if (compact) { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    }

    fun setActive(value: Boolean) { active = value; updateMotion() }
    private fun canShowMotion() = active && isAttachedToWindow && isShown && hasWindowFocus() && windowVisibility == VISIBLE
    private fun updateMotion() {
        removeCallbacks(startPreview)
        live?.setActive(false)
        if (canShowMotion()) postDelayed(startPreview, 250L)
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateMotion() }
    override fun onDetachedFromWindow() { removeCallbacks(startPreview); live?.setActive(false); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if (isAttachedToWindow) updateMotion() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if (isAttachedToWindow) updateMotion() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
