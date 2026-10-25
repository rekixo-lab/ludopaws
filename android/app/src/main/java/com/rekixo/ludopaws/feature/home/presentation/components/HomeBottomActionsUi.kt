package com.rekixo.ludopaws.ui.home

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.HomeActivity
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.ui.dialogs.showSettingsDialog

internal fun HomeActivity.homeBottomActions(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        addView(
            bottomActionButton(
                label = if (com.rekixo.ludopaws.BuildConfig.DEBUG) "Dev tools" else "Rate on Google",
                description =
                    if (com.rekixo.ludopaws.BuildConfig.DEBUG) "Open developer tools" else getString(R.string.rate_accessibility_label),
                iconKind = if (com.rekixo.ludopaws.BuildConfig.DEBUG) HomeIconKind.SETTINGS else HomeIconKind.RATING,
                secondaryText = if (com.rekixo.ludopaws.BuildConfig.DEBUG) null else "★★★★★",
            ) {
                if (com.rekixo.ludopaws.BuildConfig.DEBUG) {
                    startActivity(android.content.Intent().setClassName(packageName, "com.rekixo.ludopaws.DeveloperToolsActivity"))
                } else showHomeRatingDialog()
            },
            bottomActionParams(),
        )

        addView(
            bottomActionButton(
                label = "Share",
                description = "Share",
                iconKind = HomeIconKind.SHARE,
            ) {
                shareLudoPaws()
            },
            bottomActionParams(),
        )

        addView(
            bottomActionButton(
                label = "Settings",
                description = "Settings",
                iconKind = HomeIconKind.SETTINGS,
            ) {
                showSettingsDialog(
                    this@homeBottomActions,
                )
            },
            bottomActionParams(),
        )
    }

private fun HomeActivity.bottomActionParams(): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(
        0,
        dp(if (isCompact()) 50 else 56),
        1f,
    ).apply {
        leftMargin = dp(if (isCompact()) 2 else 3)
        rightMargin = dp(if (isCompact()) 2 else 3)
    }

private fun HomeActivity.bottomActionButton(
    label: String,
    description: String,
    iconKind: HomeIconKind,
    secondaryText: String? = null,
    action: () -> Unit,
): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = description
        background =
            plywoodBackground(
                context = this@bottomActionButton,
                cornerRadiusDp = if (isCompact()) 25f else 28f,
            )
        elevation = dp(7).toFloat()
        setPadding(
            dp(if (isCompact()) 6 else 8),
            dp(4),
            dp(if (isCompact()) 6 else 8),
            dp(4),
        )
        setOnClickListener {
            action()
        }

        val actionIcon: View =
            if (iconKind == HomeIconKind.RATING) {
                HomeGooglePlayIconView(
                    this@bottomActionButton,
                )
            } else {
                HomeIconView(this@bottomActionButton).apply {
                    kind = iconKind
                    iconColor = Color.WHITE
                    importantForAccessibility =
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
            }

        actionIcon.importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_NO

        addView(
            actionIcon,
            LinearLayout.LayoutParams(
                dp(if (isCompact()) 31 else 36),
                dp(if (isCompact()) 31 else 36),
            ).apply {
                marginEnd =
                    when (iconKind) {
                        HomeIconKind.SHARE -> dp(0)
                        HomeIconKind.RATING -> dp(if (isCompact()) 3 else 5)
                        else -> dp(if (isCompact()) 3 else 5)
                    }
            },
        )

        addView(
            LinearLayout(this@bottomActionButton).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER

                addView(
                    TextView(this@bottomActionButton).apply {
                        text = label
                        textSize =
                            when {
                                iconKind == HomeIconKind.RATING && isCompact() -> 9.2f
                                iconKind == HomeIconKind.RATING -> 10.4f
                                isCompact() && label.length > 8 -> 8.5f
                                label.length > 8 -> 9.5f
                                isCompact() -> 11f
                                else -> 12.5f
                            }
                        setTypeface(Typeface.DEFAULT_BOLD)
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        maxLines = 1
                        setShadowLayer(
                            2f,
                            0f,
                            dp(1).toFloat(),
                            0xC0000000.toInt(),
                        )
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )

                if (secondaryText != null) {
                    if (iconKind == HomeIconKind.RATING) {
                        addView(
                            HomeRatingStarsView(
                                this@bottomActionButton,
                            ).apply {
                                importantForAccessibility =
                                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            },
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                dp(if (isCompact()) 13 else 15),
                            ).apply {
                                topMargin = dp(1)
                            },
                        )
                    } else {
                        addView(
                            TextView(this@bottomActionButton).apply {
                                text = secondaryText
                                textSize = if (isCompact()) 8.5f else 9.5f
                                setTypeface(Typeface.DEFAULT_BOLD)
                                setTextColor(0xFFFFCF32.toInt())
                                letterSpacing = 0.01f
                                gravity = Gravity.CENTER
                                maxLines = 1
                                setShadowLayer(
                                    1.5f,
                                    0f,
                                    dp(1).toFloat(),
                                    0xB0000000.toInt(),
                                )
                            },
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ),
                        )
                    }
                }
            }.apply {
                when (iconKind) {
                    HomeIconKind.RATING ->
                        translationX = -dp(if (isCompact()) 2 else 3).toFloat()
                    HomeIconKind.SHARE ->
                        translationX = -dp(if (isCompact()) 4 else 5).toFloat()
                    else -> Unit
                }
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )
    }
