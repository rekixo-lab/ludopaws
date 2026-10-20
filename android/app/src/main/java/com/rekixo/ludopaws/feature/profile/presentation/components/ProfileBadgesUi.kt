package com.rekixo.ludopaws.feature.profile.presentation.components

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.core.ui.art.blueIconBackground
import com.rekixo.ludopaws.feature.profile.domain.ProfileProgression
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileSnapshot
import com.rekixo.ludopaws.feature.profile.presentation.ProfileActivity

internal fun ProfileActivity.profileBadgesPanel(
    profile: ProfileSnapshot,
):
    LinearLayout =
    profilePanel().apply {
        val badges =
            ProfileProgression
                .badges(
                    profile,
                )

        val heading =
            LinearLayout(
                this@profileBadgesPanel,
            ).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
            }
        heading.addView(
            profileTitle(
                "Badges ${badges.count { it.unlocked }}/${badges.size}",
            ),
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )
        heading.addView(
            Button(
                this@profileBadgesPanel,
            ).apply {
                text =
                    "VIEW ALL  ›"
                textSize =
                    11f
                setTypeface(
                    Typeface.DEFAULT_BOLD,
                )
                setTextColor(
                    0xFFFFD43B.toInt(),
                )
                minWidth =
                    0
                minHeight =
                    0
                background =
                    null
                setOnClickListener {
                    viewAllBadges()
                }
            },
        )
        addView(
            heading,
        )

        val row =
            LinearLayout(
                this@profileBadgesPanel,
            ).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER
                setPadding(
                    0,
                    dp(8),
                    0,
                    0,
                )
            }

        // The Home/Profile preview must stay readable as the catalog grows.
        badges.sortedWith(compareByDescending<com.rekixo.ludopaws.feature.profile.domain.model.ProfileBadge> { it.unlocked }
            .thenByDescending { it.progressCurrent.toFloat() / it.progressGoal.coerceAtLeast(1) })
            .take(4).forEach {
                badge ->
            row.addView(
                LinearLayout(
                    this@profileBadgesPanel,
                ).apply {
                    orientation =
                        LinearLayout.VERTICAL
                    gravity =
                        Gravity.CENTER
                    alpha =
                        if (badge.unlocked) 1f else .75f
                    contentDescription = "${badge.title}, " +
                        if (badge.unlocked) "unlocked" else
                            "${badge.progressCurrent} of ${badge.progressGoal} completed"

                    addView(
                        TextView(
                            this@profileBadgesPanel,
                        ).apply {
                            text =
                                badge.symbol
                            textSize =
                                24f
                            setTypeface(
                                Typeface.DEFAULT_BOLD,
                            )
                            setTextColor(
                                if (!badge.unlocked) {
                                    0xFF8FA8C9.toInt()
                                } else {
                                    when (badge.id) {
                                        "first-win" -> 0xFFFFD13A.toInt()
                                        "strategist" -> 0xFF20F06A.toInt()
                                        "unstoppable" -> 0xFFDA64FF.toInt()
                                        "friendship" -> 0xFF21D8F8.toInt()
                                        "social-gamer" -> 0xFFFF5664.toInt()
                                        else -> 0xFFFFD13A.toInt()
                                    }
                                },
                            )
                            gravity =
                                Gravity.CENTER
                            background =
                                blueIconBackground(this@profileBadgesPanel)
                        },
                        LinearLayout.LayoutParams(
                            dp(44),
                            dp(44),
                        ),
                    )

                    addView(
                        TextView(
                            this@profileBadgesPanel,
                        ).apply {
                            text =
                                badge.title
                            textSize =
                                9f
                            setTextColor(
                                Color.WHITE,
                            )
                            gravity =
                                Gravity.CENTER
                            maxLines =
                                2
                            setPadding(
                                0,
                                dp(4),
                                0,
                                0,
                            )
                        },
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
        }

        addView(
            row,
        )
    }
