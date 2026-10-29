package com.rekixo.ludopaws.feature.referrals

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.R

/** Local transparent 3D reward artwork, with native readable condition text. */
internal class ReferralGemRewardView(context: Context, compact: Boolean) : LinearLayout(context) {
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
        addView(bucket, LayoutParams(size, size))
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
                text = if (compact) "Earn with a qualifying invite" else "After your friend’s qualifying match"
                textSize = 12f
                setTextColor(0xFF523B28.toInt())
            })
        }, LayoutParams(0, -2, 1f))
        contentDescription = "Free 100 Gems for you after a new invited friend completes a qualifying match. No reward for sharing alone."
    }
}
