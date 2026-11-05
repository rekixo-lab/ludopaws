package com.rekixo.ludopaws.ui.home

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.HomeActivity
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.feature.rewards.RewardsActivity
import com.rekixo.ludopaws.ui.dialogs.showSettingsDialog

internal fun HomeActivity.homeBottomActions(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isBaselineAligned = false

        addView(
            bottomActionButton("Rate", getString(R.string.rate_accessibility_label), HomeIconKind.RATING) {
                openPlayStoreRating(this@homeBottomActions)
            },
            bottomActionParams(),
        )
        addView(
            bottomActionButton("Share", "Share", HomeIconKind.SHARE) { shareLudoPaws() },
            bottomActionParams(),
        )
        addView(
            bottomActionButton("Rewards", "Daily gifts, free spin, goals and collections", HomeIconKind.GEM) {
                startActivity(Intent(this@homeBottomActions, RewardsActivity::class.java))
            },
            bottomActionParams(),
        )
        addView(
            bottomActionButton("Settings", "Settings", HomeIconKind.SETTINGS) {
                showSettingsDialog(this@homeBottomActions)
            },
            bottomActionParams(),
        )
    }

private fun HomeActivity.bottomActionParams(): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(0, dp(if (isCompact()) 50 else 56), 1f).apply {
        marginStart = dp(if (isCompact()) 2 else 3)
        marginEnd = dp(if (isCompact()) 2 else 3)
    }

private fun HomeActivity.bottomActionButton(
    label: String,
    description: String,
    iconKind: HomeIconKind,
    action: () -> Unit,
): LinearLayout =
    LinearLayout(this).apply {
        val compact = isCompact()
        val iconSlot = dp(if (compact) 25 else 27)
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = description
        background = plywoodBackground(
            context = this@bottomActionButton,
            cornerRadiusDp = if (compact) 25f else 28f,
        )
        elevation = dp(7).toFloat()
        setPaddingRelative(dp(4), 0, dp(4), 0)
        setOnClickListener { action() }

        addView(
            FrameLayout(context).apply {
                val icon: View = if (iconKind == HomeIconKind.RATING) {
                    HomeGooglePlayIconView(context)
                } else {
                    HomeIconView(context).apply { kind = iconKind }
                }
                icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                // The gem and gear fill more of their source canvas than Share and Play.
                val iconSize = when (iconKind) {
                    HomeIconKind.GEM -> iconSlot - dp(4)
                    HomeIconKind.SETTINGS -> iconSlot - dp(2)
                    else -> iconSlot
                }
                addView(icon, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
            },
            LinearLayout.LayoutParams(iconSlot, iconSlot),
        )

        addView(
            TextView(context).apply {
                text = label
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setAutoSizeTextTypeUniformWithConfiguration(
                    9, if (compact) 11 else 12, 1, TypedValue.COMPLEX_UNIT_SP,
                )
                setShadowLayer(2f, 0f, dp(1).toFloat(), 0xC0000000.toInt())
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(if (compact) 17 else 19),
            ).apply { topMargin = dp(2) },
        )
    }
