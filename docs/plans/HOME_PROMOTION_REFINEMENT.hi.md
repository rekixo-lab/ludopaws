package com.rekixo.ludopaws.feature.referrals

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.DiceView
import com.rekixo.ludopaws.LudoBoardView
import com.rekixo.ludopaws.LudoPawsBoardChromeView
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView

/** Local transparent 3D reward artwork, with native readable condition text. */
internal class ReferralGemRewardView(
    context: Context,
    compact: Boolean,
    includeBundleItems: Boolean = false,
) : LinearLayout(context) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val size = dp(if (compact) 72 else 104)
        val bucket = ImageView(context).apply {
            setImageResource(R.drawable.referral_gem_bucket_3d)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        if (includeBundleItems) {
            val art = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            fun slot(view: android.view.View) {
                art.addView(object : FrameLayout(context) {
                    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                        // Measure board + chrome in one shared square too, so
                        // their origins cannot separate inside a tall row.
                        val side = minOf(measuredWidth, measuredHeight)
                        val exact = MeasureSpec.makeMeasureSpec(side, MeasureSpec.EXACTLY)
                        getChildAt(0)?.measure(exact, exact)
                    }
                }.apply {
                    addView(view, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
                }, LayoutParams(0, size, 1f))
            }
            // Same-height artwork slots share the available width; keep the
            // reward label fixed on the right rather than crowding it out.
            slot(bucket)
            slot(FrameLayout(context).apply {
                addView(LudoBoardView(context).apply {
                    setBoardThemeOverride("board_blossom_garden")
                    setClassicTokenDrawingEnabled(false)
                }, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
                addView(LudoPawsBoardChromeView(context).apply {
                    setBoardThemeOverride("board_blossom_garden")
                }, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
                contentDescription = "Blossom Garden board"
            })
            slot(LudoPawsCharacterAvatarView(context).apply {
                bind("rabbit", showCircularChrome = false, showTeamBadge = false)
            })
            slot(DiceView(context).apply {
                setPreviewStyle("dice_pearl_paw")
                showOutcome(1, animate = false)
                setPlayerTeamColor(null)
                contentDescription = "Pearl Paw dice"
            })
            addView(art, LayoutParams(0, size, 1f))
        } else addView(bucket, LayoutParams(size, size))
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(8), 0, 0, 0)
            addView(TextView(context).apply {
                text = "FREE · 100 Gems"
                textSize = if (compact) 17f else 23f
                typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(dp(6), dp(3), dp(6), dp(3))
                background = GradientDrawable().apply {
                    setColor(0xFF245B2C.toInt()); cornerRadius = dp(8).toFloat()
                }
            })
            addView(TextView(context).apply {
                text = when {
                    includeBundleItems -> "After verified play"
                    compact -> "Earn with a qualifying invite"
                    else -> "After your friend’s qualifying match"
                }
                textSize = 12f
                setTextColor(0xFF523B28.toInt())
            })
        }, if (includeBundleItems) LayoutParams(dp(126), -2) else LayoutParams(0, -2, 1f))
        contentDescription = (if (includeBundleItems) "Bucket, Blossom Garden board, Rabbit character, Pearl Paw dice. " else "") + "Free 100 Gems for you after a new invited friend completes a qualifying match. No reward for sharing alone."
    }
}
