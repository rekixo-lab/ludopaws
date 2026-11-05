package com.rekixo.ludopaws.feature.referrals

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.DiceView
import com.rekixo.ludopaws.LudoBoardView
import com.rekixo.ludopaws.LudoPawsBoardChromeView
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground
import com.rekixo.ludopaws.feature.store.presentation.components.FeaturedPawsPreview

internal class FriendshipBundleView(context: Context, private val compact: Boolean = false, private val ownedItems: Set<String> = emptySet(), private val timed: Boolean = false) : LinearLayout(context) {
    private val characterId = if (timed) "giraffe" else "rabbit"
    private val boardId = if (timed) "board_sunstone_palace" else "board_blossom_garden"
    private val diceId = if (timed) "dice_prism_royal" else "dice_pearl_paw"
    private val ownershipLabels = linkedMapOf<String, TextView>()
    private var ownershipRow: LinearLayout? = null
    private var timerLabel: TextView? = null
    private var offer: TimedFriendshipOffer? = null
    private var onExpired: (() -> Unit)? = null
    private val countdown = Runnable { updateCountdown() }
    private var live: FeaturedPawsPreview? = null
    private var active = false
    private val avatar = LudoPawsCharacterAvatarView(context).apply {
        bind(characterId, showCircularChrome = false, showTeamBadge = false)
    }
    private val characterSlot = FrameLayout(context)
    private val startPreview = Runnable {
        if (canShowMotion() && live == null) {
            try {
                live = FeaturedPawsPreview(context, 66L, playful = true).also { preview ->
                    preview.characterId = characterId
                    preview.alpha = 0f
                    preview.onReady = { preview.alpha = 1f; avatar.visibility = INVISIBLE }
                    preview.onUnavailable = { preview.alpha = 0f; avatar.visibility = VISIBLE }
                    characterSlot.addView(preview, FrameLayout.LayoutParams(-1, -1))
                }
            } catch (_: Exception) { avatar.visibility = VISIBLE }
        }
        live?.setActive(canShowMotion())
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = if (compact) carouselBackground() else recessedPlywoodBackground(context, outerCornerRadiusDp = 24f)
        setPadding(dp(12), dp(if (compact) 10 else 16), dp(12), dp(if (compact) 10 else 14))
        addView(label(if (timed) "Friendship Bundle 2" else if (compact) "Friendship Bundle" else "YOUR FRIENDSHIP BUNDLE", if (compact) 19f else 11f, true).apply {
            if (compact) typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
            else letterSpacing = 0.12f
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
                setBoardThemeOverride(boardId)
                setClassicTokenDrawingEnabled(false)
            }, FrameLayout.LayoutParams(-1, -1))
            addView(LudoPawsBoardChromeView(context).apply { setBoardThemeOverride(boardId) }, FrameLayout.LayoutParams(-1, -1))
            contentDescription = if (timed) "Sunstone Palace Board" else "Blossom Garden Board"
        }
        characterSlot.addView(avatar, FrameLayout.LayoutParams(-1, -1))
        characterSlot.contentDescription = if (timed) "Giraffe character" else "Rabbit character"
        val dice = DiceView(context).apply {
            setPreviewStyle(diceId)
            showOutcome(1, animate = false)
            setPlayerTeamColor(null)
            contentDescription = if (timed) "Prism Royal Dice" else "Pearl Paw Dice"
        }
        art.addView(artworkSlot(board), LayoutParams(0, dp(if (compact) 76 else 84), 1f))
        art.addView(characterSlot, LayoutParams(0, -1, 1.3f))
        art.addView(artworkSlot(dice), LayoutParams(0, dp(if (compact) 76 else 84), 1f))
        addView(art, if (compact) LayoutParams(-1, 0, 1f) else LayoutParams(-1, dp(132)))
        if (timed) {
            addView(label(TimedFriendshipOffer.REWARDS, if (compact) 10f else 13f, true), LayoutParams(-1, -2))
            ownershipRow = LinearLayout(context).apply {
                for (id in listOf(boardId, "character_$characterId", diceId)) {
                    val status = label("", if (compact) 9f else 11f).apply {
                        maxLines = 2
                    }
                    ownershipLabels[id] = status
                    addView(status, LayoutParams(0, -2, 1f))
                }
            }.also { addView(it, LayoutParams(-1, -2).apply { topMargin = dp(3) }) }
            bindOwnership(ownedItems)
            timerLabel = label("", 14f, true).also { addView(it, LayoutParams(-1, -2)) }
        } else {
            addView(ReferralGemRewardView(context, compact), LayoutParams(-1, -2))
            addView(label(if (compact) "Plus any bundle items you don’t own" else "Board  ·  Rabbit  ·  Dice", if (compact) 11f else 12f).apply {
                if (!compact) setPadding(0, dp(5), 0, 0)
            }, LayoutParams(-1, -2))
        }
        if (!timed && !compact && ownedItems.any { it in ReferralPopupPolicy.rewardItems }) {
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
            setTextColor(0xFF382716.toInt())
            backgroundTintList = null
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFFF8DE9F.toInt(), 0xFFDAAD60.toInt())).apply {
                cornerRadius = dp(13).toFloat()
                setStroke(dp(1), 0xFFFFE9B6.toInt())
            }
            minHeight = dp(48)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener { this@FriendshipBundleView.performClick() }
        }, LayoutParams(-1, dp(48)).apply { topMargin = dp(6) })
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = if (timed) "Friendship Bundle 2: " + TimedFriendshipOffer.REWARDS else "Friendship bundle: Pearl Paw Dice, Blossom Garden Board, Rabbit and 100 Gems. Invite a new friend. Reward only after verified play."
    }

    private fun carouselBackground(): LayerDrawable {
        val frame = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF225447.toInt(), 0xFF132D34.toInt()),
        ).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), 0xFFCBA464.toInt())
        }
        val light = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(.5f, .36f)
            gradientRadius = dp(140).toFloat()
            colors = intArrayOf(0x584AAE8A, 0x00225447)
            setStroke(dp(1), 0x457DD5BA)
        }
        return LayerDrawable(arrayOf(frame, light)).apply {
            val inset = dp(3)
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(when {
            compact && bold -> 0xFFFFEBC4.toInt()
            compact -> 0xFFD4EBDD.toInt()
            bold -> 0xFF422B1C.toInt()
            else -> 0xFF60452D.toInt()
        })
        if (bold) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        if (compact) { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    }

    fun setActive(value: Boolean) {
        active = value
        updateMotion()
        updateCountdown()
    }

    fun bindOffer(value: TimedFriendshipOffer?, expired: (() -> Unit)? = null) {
        offer = value
        onExpired = expired
        updateCountdown()
    }

    fun bindOwnership(owned: Set<String>) {
        ownershipRow?.visibility = if (ownershipLabels.keys.any { it in owned }) VISIBLE else GONE
        ownershipLabels.forEach { (id, status) ->
            val item = when (id) {
                boardId -> "Board"
                diceId -> "Dice"
                else -> "Giraffe"
            }
            status.text = if (id in owned) "$item\nAlready owned" else "$item\nIncluded"
        }
    }

    private fun updateCountdown() {
        removeCallbacks(countdown)
        val label = timerLabel ?: return
        val current = offer ?: return
        label.text = when (current.state) {
            "REWARDED" -> "Bundle received"
            "VERIFYING" -> "Match verified · delivering reward"
            "UNAVAILABLE" -> "Offer unavailable"
            else -> if (current.remainingMs > 0) "Time left  ${current.countdown}" else "Offer ended"
        }
        if (current.canInvite && active && isAttachedToWindow) postDelayed(countdown, 1000)
        if (current.state in setOf("INVITE", "FRIEND_JOINED") && current.remainingMs == 0L) {
            val expired = onExpired
            onExpired = null
            expired?.invoke()
        }
    }
    private fun canShowMotion() = active && isAttachedToWindow && isShown && hasWindowFocus() && windowVisibility == VISIBLE &&
        android.animation.ValueAnimator.areAnimatorsEnabled()
    private fun updateMotion() {
        removeCallbacks(startPreview)
        live?.setActive(false)
        if (canShowMotion()) postDelayed(startPreview, 250L)
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateMotion()
        updateCountdown()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(countdown)
        removeCallbacks(startPreview)
        live?.setActive(false)
        super.onDetachedFromWindow()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if (isAttachedToWindow) updateMotion() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if (isAttachedToWindow) updateMotion() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
