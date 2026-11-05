package com.rekixo.ludopaws.feature.referrals

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Button
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.DiceView
import com.rekixo.ludopaws.LudoBoardView
import com.rekixo.ludopaws.LudoPawsBoardChromeView
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground
import com.rekixo.ludopaws.feature.store.presentation.components.FeaturedPawsPreview

internal class FriendshipBundleView(context: Context, private val compact: Boolean = false, private val ownedItems: Set<String> = emptySet()) : LinearLayout(context) {
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
        background = recessedPlywoodBackground(context, outerCornerRadiusDp = 24f)
        addView(label(if (compact) "FRIENDSHIP BUNDLE" else "YOUR FRIENDSHIP BUNDLE", if (compact) 16f else 11f, true).apply {
            if (!compact) letterSpacing = 0.12f
        }, LayoutParams(-1, -2))
        val art = object : LinearLayout(context) {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean = false
        }.apply {
            gravity = Gravity.CENTER_VERTICAL
            // Keep the artwork clear of the recessed panel's inner rim on
            // both the Home carousel and the full Share/popup layout.
            setPadding(dp(12), 0, dp(12), 0)
        }
        fun artworkSlot(view: View) = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                val side = minOf(measuredWidth, measuredHeight)
                val exact = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY)
                // Board and chrome share one centered square, even when the
                // weighted column is wider or narrower than the artwork.
                getChildAt(0).measure(exact, exact)
            }
        }.apply {
            addView(view, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        }
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
        art.addView(artworkSlot(board), LayoutParams(0, dp(if (compact) 76 else 84), 1f))
        art.addView(rabbit, LayoutParams(0, -1, 1.3f))
        art.addView(artworkSlot(dice), LayoutParams(0, dp(if (compact) 76 else 84), 1f))
        addView(art, if (compact) LayoutParams(-1, 0, 1f) else LayoutParams(-1, dp(132)))
        addView(ReferralGemRewardView(context, compact), LayoutParams(-1, -2))
        addView(label(if (compact) "100 Gems + missing bundle items · Invite & play" else "Board  ·  Rabbit  ·  Dice", if (compact) 11f else 12f).apply {
            if (!compact) setPadding(0, dp(5), 0, 0)
        }, LayoutParams(-1, -2))
        if (!compact && ownedItems.any { it in ReferralPopupPolicy.rewardItems }) {
            val ownership = LinearLayout(context)
            for ((id, title) in listOf("board_blossom_garden" to "Board", "character_rabbit" to "Rabbit", "dice_pearl_paw" to "Dice")) {
                ownership.addView(label(if (id in ownedItems) "$title · Owned ✓" else "$title · Included", 10f), LayoutParams(0, -2, 1f))
            }
            addView(ownership, LayoutParams(-1, -2).apply { topMargin = dp(5) })
        }
        if (compact) addView(Button(context).apply {
            text = "Invite a friend  ›"
            contentDescription = "Invite a friend. View the friendship bundle and qualifying rules."
            isAllCaps = false
            textSize = 15f
            typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            backgroundTintList = null
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF80CD36.toInt(), 0xFF2D821C.toInt())).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0xFFB0E56E.toInt())
            }
            minHeight = dp(48)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener { this@FriendshipBundleView.performClick() }
        }, LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Friendship bundle: Pearl Paw Dice, Blossom Garden Board, Rabbit and 100 Gems. Invite a new friend. Reward only after verified play."
    }

    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(if (bold) 0xFF422B1C.toInt() else 0xFF60452D.toInt())
        if (bold) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        if (compact) { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    }

    fun setActive(value: Boolean) { active = value; updateMotion() }
    private fun canShowMotion() = active && isAttachedToWindow && isShown && hasWindowFocus() && windowVisibility == VISIBLE &&
        android.animation.ValueAnimator.areAnimatorsEnabled()
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
