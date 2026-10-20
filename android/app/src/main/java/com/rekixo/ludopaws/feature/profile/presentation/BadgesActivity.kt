package com.rekixo.ludopaws.feature.profile.presentation

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.core.ui.art.blueIconBackground
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.art.transparentDarkBlueStrokeBackground
import com.rekixo.ludopaws.core.ui.components.appIconActionButton
import com.rekixo.ludopaws.core.ui.icons.AppIconName
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.profile.domain.ProfileProgression
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileBadge

/** Complete badge collection shared by Home and the Profile "View All" action. */
class BadgesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LudoProofTheme.configureWindow(this)
        renderBadges()
    }

    private fun renderBadges() {
        val badges = ProfileProgression.badges(ProfileStore(this).snapshot())
        val unlockedCount = badges.count { it.unlocked }
        val compact = LudoProofTheme.isCompactWidth(this)
        val horizontalPadding = dp(if (compact) 12 else 18)
        val pageWidth = minOf(
            resources.displayMetrics.widthPixels - horizontalPadding * 2,
            dp(LudoProofTheme.pageMaxContentWidthDp(this)),
        ).coerceAtLeast(dp(1))

        val (root, host) = LudoProofTheme.arcadeRoot(this)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(26))
        }
        scroll.addView(
            content,
            FrameLayout.LayoutParams(
                pageWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ),
        )
        host.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val topBar = FrameLayout(this).apply {
            addView(
                TextView(this@BadgesActivity).apply {
                    text = "Badges"
                    textSize = 25f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    dp(50),
                    Gravity.CENTER,
                ),
            )
            addView(
                appIconActionButton(
                    context = this@BadgesActivity,
                    contentDescription = "Back",
                    iconName = AppIconName.CHEVRON_LEFT,
                    sizeDp = 50,
                    onClick = { finish() },
                ).apply {
                    background = plywoodBackground(
                        context = this@BadgesActivity,
                        cornerRadiusDp = 25f,
                    )
                },
                FrameLayout.LayoutParams(
                    dp(50),
                    dp(50),
                    Gravity.START or Gravity.CENTER_VERTICAL,
                ),
            )
        }
        content.addView(topBar)

        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = plywoodBackground(
                context = this@BadgesActivity,
                cornerRadiusDp = 20f,
            )
            elevation = dp(4).toFloat()

            addView(
                TextView(this@BadgesActivity).apply {
                    text = "BADGE COLLECTION"
                    textSize = if (compact) 13f else 14f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(0xFFFFD45E.toInt())
                    letterSpacing = 0.045f
                },
            )
            addView(
                TextView(this@BadgesActivity).apply {
                    text = "$unlockedCount / ${badges.size} UNLOCKED"
                    textSize = 22f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(0, dp(5), 0, 0)
                },
            )
            addView(
                TextView(this@BadgesActivity).apply {
                    text = "Complete challenges during your matches to collect every badge."
                    textSize = if (compact) 11f else 12f
                    setTextColor(0xFFD9EDFF.toInt())
                    setPadding(0, dp(3), 0, dp(10))
                },
            )

            val track = FrameLayout(this@BadgesActivity).apply {
                background = LudoProofTheme.rounded(
                    0xFF092D67.toInt(),
                    999f,
                    0xFF397FC5.toInt(),
                    1f,
                    this@BadgesActivity,
                )
                clipToOutline = true
            }
            val fill = View(this@BadgesActivity).apply {
                background = LudoProofTheme.rounded(
                    0xFFFFD45E.toInt(),
                    999f,
                    0xFFFFECA2.toInt(),
                    1f,
                    this@BadgesActivity,
                )
            }
            track.addView(
                fill,
                FrameLayout.LayoutParams(
                    0,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.START,
                ),
            )
            addView(
                track,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(8),
                ),
            )
            track.post {
                if (track.width > 0) {
                    val params = fill.layoutParams as FrameLayout.LayoutParams
                    params.width =
                        (track.width * unlockedCount.toFloat() / badges.size.coerceAtLeast(1))
                            .toInt()
                            .coerceIn(0, track.width)
                    fill.layoutParams = params
                }
            }
        }
        content.addView(
            summary,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) },
        )

        content.addView(
            TextView(this).apply {
                text = "ALL BADGES"
                textSize = 13f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(0xFFFFD45E.toInt())
                letterSpacing = 0.06f
                setPadding(dp(3), dp(18), dp(3), dp(9))
            },
        )

        badges.forEach { badge ->
            content.addView(
                badgeRow(badge, compact),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(9) },
            )
        }

        root.setOnApplyWindowInsetsListener { _, insets ->
            val systemTopInset = insets.systemWindowInsetTop
            val cutoutTopInset =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val cutout = insets.displayCutout
                    maxOf(
                        cutout?.safeInsetTop ?: 0,
                        cutout?.boundingRects
                            ?.filter { it.top == 0 }
                            ?.maxOfOrNull { it.bottom }
                            ?: 0,
                    )
                } else {
                    0
                }
            content.setPadding(
                0,
                maxOf(dp(12), maxOf(systemTopInset, cutoutTopInset) + dp(8)),
                0,
                dp(26),
            )
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
    }

    private fun badgeRow(badge: ProfileBadge, compact: Boolean): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = transparentDarkBlueStrokeBackground(
                context = this@BadgesActivity,
                cornerRadiusDp = 17f,
            )
            contentDescription = buildString {
                append(badge.title)
                append(if (badge.unlocked) ", unlocked" else ", locked")
                append(". ")
                append(badge.requirement)
                append(". Progress ").append(badge.progressCurrent).append(" of ").append(badge.progressGoal)
            }

            val accent = badgeAccent(badge)
            addView(
                TextView(this@BadgesActivity).apply {
                    text = badge.symbol
                    textSize = if (compact) 27f else 30f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(if (badge.unlocked) accent else 0xFF8FA8C9.toInt())
                    gravity = Gravity.CENTER
                    background = blueIconBackground(
                        context = this@BadgesActivity,
                        cornerRadiusDp = 17f,
                        fillColor = if (badge.unlocked) 0xFF073F8C.toInt() else 0xFF182F50.toInt(),
                        strokeColor = if (badge.unlocked) accent else 0xFF526C8D.toInt(),
                        strokeWidthDp = 1.25f,
                    )
                    alpha = if (badge.unlocked) 1f else 0.78f
                },
                LinearLayout.LayoutParams(
                    dp(if (compact) 54 else 60),
                    dp(if (compact) 54 else 60),
                ),
            )

            val details = LinearLayout(this@BadgesActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(7), 0)
                addView(
                    TextView(this@BadgesActivity).apply {
                        text = badge.title
                        textSize = if (compact) 14f else 16f
                        setTypeface(Typeface.DEFAULT_BOLD)
                        setTextColor(Color.WHITE)
                        maxLines = 1
                    },
                )
                addView(
                    TextView(this@BadgesActivity).apply {
                        text = badge.requirement
                        textSize = if (compact) 10.5f else 12f
                        setTextColor(0xFFD7E9FF.toInt())
                        setPadding(0, dp(4), 0, 0)
                    },
                )
                addView(
                    TextView(this@BadgesActivity).apply {
                        text = if (badge.unlocked) "Completed" else
                            "${badge.progressCurrent} / ${badge.progressGoal} progress"
                        textSize = if (compact) 10f else 11f
                        setTypeface(Typeface.DEFAULT_BOLD)
                        setTextColor(if (badge.unlocked) 0xFFBDF8B8.toInt() else 0xFFFFD45E.toInt())
                        setPadding(0, dp(4), 0, 0)
                    },
                )
            }
            addView(
                details,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )

            addView(
                TextView(this@BadgesActivity).apply {
                    text = if (badge.unlocked) "UNLOCKED" else "LOCKED"
                    textSize = if (compact) 8f else 9f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(
                        if (badge.unlocked) 0xFFBDF8B8.toInt() else 0xFFB7CCE6.toInt(),
                    )
                    gravity = Gravity.CENTER
                    setPadding(dp(7), dp(5), dp(7), dp(5))
                    background = LudoProofTheme.rounded(
                        if (badge.unlocked) 0x3335C83D else 0x332B5B91,
                        999f,
                        if (badge.unlocked) 0xFF45C96A.toInt() else 0xFF6487B0.toInt(),
                        1f,
                        this@BadgesActivity,
                    )
                },
            )
        }

    private fun badgeAccent(badge: ProfileBadge): Int =
        when (badge.id) {
            "first-win" -> 0xFFFFD13A.toInt()
            "strategist" -> 0xFF20F06A.toInt()
            "unstoppable" -> 0xFFDA64FF.toInt()
            "friendship" -> 0xFF21D8F8.toInt()
            "social-gamer" -> 0xFFFF5664.toInt()
            else -> 0xFFFFD13A.toInt()
        }

    private fun dp(value: Int): Int = LudoProofTheme.dp(this, value)
}
