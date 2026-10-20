package com.rekixo.ludopaws.feature.profile.presentation.components

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground
import com.rekixo.ludopaws.core.ui.art.transparentDarkBlueStrokeBackground
import com.rekixo.ludopaws.core.ui.art.playGamesSignInBackground
import com.rekixo.ludopaws.core.ui.art.experienceProgressTrackBackground
import com.rekixo.ludopaws.core.ui.art.experienceProgressFillBackground
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.core.ui.components.appIconActionButton
import com.rekixo.ludopaws.core.ui.icons.AppIconName
import com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore
import com.rekixo.ludopaws.feature.profile.domain.ProfileProgression
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.profile.domain.model.ProfileSnapshot
import com.rekixo.ludopaws.feature.profile.presentation.ProfileActivity
import com.rekixo.ludopaws.feature.store.domain.model.StoreTab
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity

internal fun ProfileActivity.profileTopBar(): FrameLayout =
    FrameLayout(this).apply {
        // Keep the title centered independently of the two symmetric action buttons.
        addView(
            TextView(this@profileTopBar).apply {
                text = "Profile"
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
                context = this@profileTopBar,
                contentDescription = "Back",
                iconName = AppIconName.CHEVRON_LEFT,
                sizeDp = 50,
                onClick = { this@profileTopBar.finish() },
            ).apply {
                // Keep both navigation controls on the same circular plywood skin.
                background = plywoodBackground(this@profileTopBar, cornerRadiusDp = 25f)
            },
            FrameLayout.LayoutParams(dp(50), dp(50), Gravity.START or Gravity.CENTER_VERTICAL),
        )

        val menuButton =
            appIconActionButton(
                context = this@profileTopBar,
                contentDescription = "Open profile menu",
                iconName = AppIconName.MENU,
                sizeDp = 50,
                onClick = {},
            ).apply {
                background = plywoodBackground(this@profileTopBar, cornerRadiusDp = 25f)
            }
        menuButton.setOnClickListener {
            this@profileTopBar.showProfileMenu(menuButton)
        }
        addView(
            menuButton,
            FrameLayout.LayoutParams(dp(50), dp(50), Gravity.END or Gravity.CENTER_VERTICAL),
        )
    }

// Character personality is presentation-only and never changes dice or legal moves.
internal fun ProfileActivity.profileIdentityPanel(profile: ProfileSnapshot): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(10), dp(10), dp(10))
        // Match the Purchased Products plywood treatment across the Profile hero card.
        background = plywoodBackground(this@profileIdentityPanel)
        elevation = dp(6).toFloat()

        val selection = CharacterSelectionStore(this@profileIdentityPanel).load()
        val character = LudoPawsCharacterCatalog.character(selection.characterId)
            ?: LudoPawsCharacterCatalog.character(LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID)

        val heroRow = LinearLayout(this@profileIdentityPanel).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // One portrait only: the persisted in-game Paw selection. No separate profile-photo avatar.
        val pawColumn = LinearLayout(this@profileIdentityPanel).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val pawFrame = FrameLayout(this@profileIdentityPanel).apply {
            // Carved plywood pocket makes the existing 3D Paw feel seated into the panel.
            background = recessedPlywoodBackground(this@profileIdentityPanel, outerCornerRadiusDp = 18f)
            clipChildren = false
            clipToPadding = false
        }
        // Reuse the existing production 3D character portrait. On this screen,
        // render it directly over the blue glow without the generic white disc,
        // coloured selection ring, or the flat decorative oval.
        pawFrame.addView(
            LudoPawsCharacterAvatarView(this@profileIdentityPanel).apply {
                setPadding(0, 0, 0, 0)
                bind(
                    characterId = selection.characterId,
                    ringColor = 0xFF5BE0FF.toInt(),
                    highlighted = false,
                    showCircularChrome = false,
                )
                contentDescription = "Selected Paw: ${character?.displayName ?: "Paw"}"
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ),
        )
        pawFrame.addView(
            LinearLayout(this@profileIdentityPanel).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                background = plywoodBackground(this@profileIdentityPanel)
                contentDescription = "Change selected Paw"
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(
                        Intent(this@profileIdentityPanel, StoreActivity::class.java)
                            .putExtra(StoreActivity.EXTRA_INITIAL_TAB, StoreTab.PAWS.name),
                    )
                }

                // Keep the label first, with the pen immediately to its right.
                // The parent row centers the combined label + icon as one group.
                addView(
                    TextView(this@profileIdentityPanel).apply {
                        text = "Change"
                        textSize = 12f
                        setTypeface(Typeface.DEFAULT_BOLD)
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER_VERTICAL
                        includeFontPadding = false
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
                addView(
                    ImageView(this@profileIdentityPanel).apply {
                        setImageDrawable(
                            context.getDrawable(com.rekixo.ludopaws.R.drawable.ic_profile_edit_pen)
                                ?.mutate()
                                ?.apply { setTint(Color.WHITE) },
                        )
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = null
                    },
                    LinearLayout.LayoutParams(dp(14), dp(14)).apply {
                        marginStart = dp(3)
                    },
                )
                AppFocusRing.apply(this, cornerRadiusDp = 999f)
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(36),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
                bottomMargin = dp(3)
            },
        )
        pawColumn.addView(
            pawFrame,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(178)),
        )
        heroRow.addView(
            pawColumn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.43f),
        )

        val details = LinearLayout(this@profileIdentityPanel).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), 0, 0, 0)
        }
        val signInRow = LinearLayout(this@profileIdentityPanel).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        signInRow.addView(
            Button(this@profileIdentityPanel).apply {
                text = when {
                    playGamesSignInInProgress -> "CONNECTING…"
                    playGamesAuthenticated -> "✓  CONNECTED"
                    else -> "SIGN IN"
                }
                if (!playGamesSignInInProgress && !playGamesAuthenticated) {
                    val playGamesIcon = context.getDrawable(com.rekixo.ludopaws.R.drawable.ic_google_play_games)?.mutate()
                    playGamesIcon?.setBounds(0, 0, dp(18), dp(18))
                    setCompoundDrawablesRelative(playGamesIcon, null, null, null)
                    compoundDrawablePadding = dp(2)
                } else {
                    setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    compoundDrawablePadding = 0
                }
                textSize = 11f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                minWidth = 0
                minHeight = 0
                setPadding(dp(8), 0, dp(8), 0)
                background = playGamesSignInBackground(this@profileIdentityPanel)
                contentDescription = "Sign in to Google Play Games"
                isEnabled = !playGamesAuthenticated && !playGamesSignInInProgress
                alpha = if (isEnabled || playGamesAuthenticated) 1f else 0.78f
                setOnClickListener { beginPlayGamesSignIn() }
                AppFocusRing.apply(this, cornerRadiusDp = 13f)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(38),
            ),
        )
        details.addView(signInRow)

        val profileScreenWidthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val pageHorizontalPaddingDp =
            if (LudoProofTheme.isCompactWidth(this@profileIdentityPanel)) 24f else 36f
        val maxProfileContentWidthDp = minOf(
            profileScreenWidthDp - pageHorizontalPaddingDp,
            LudoProofTheme.pageMaxContentWidthDp(this@profileIdentityPanel).toFloat(),
        )
        val maxNameTextWidthDp =
            (((maxProfileContentWidthDp - 20f) * 0.57f) - 50f)
                .toInt()
                .coerceIn(90, 176)
        val nameRow = LinearLayout(this@profileIdentityPanel).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), dp(6), 0)
        }
        nameRow.addView(
            TextView(this@profileIdentityPanel).apply {
                text = profile.displayName
                textSize = 21f
                maxLines = 1
                maxWidth = dp(maxNameTextWidthDp)
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        nameRow.addView(
            ImageView(this@profileIdentityPanel).apply {
                setImageDrawable(
                    context.getDrawable(com.rekixo.ludopaws.R.drawable.ic_profile_edit_pen)
                        ?.mutate()
                        ?.apply { setTint(Color.WHITE) },
                )
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                isClickable = true
                isFocusable = true
                contentDescription = "Edit profile name"
                setOnClickListener { showNameEditor() }
                AppFocusRing.apply(this, cornerRadiusDp = 15f)
            },
            LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginStart = dp(3) },
        )
        details.addView(nameRow)

        val progress = ProfileProgression.levelProgress(profile.totalXp)
        val xpRow = LinearLayout(this@profileIdentityPanel).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, 0)
        }
        xpRow.addView(
            TextView(this@profileIdentityPanel).apply {
                text = "★${progress.level}"
                textSize = 21f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(0xFFFFD43B.toInt())
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(dp(43), dp(34)),
        )

        val track = FrameLayout(this@profileIdentityPanel).apply {
            background = experienceProgressTrackBackground(this@profileIdentityPanel)
            clipToOutline = true
        }
        val fill = View(this@profileIdentityPanel).apply {
            background = experienceProgressFillBackground(this@profileIdentityPanel)
        }
        track.addView(
            fill,
            FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.START),
        )
        track.addView(
            TextView(this@profileIdentityPanel).apply {
                text = "${progress.xpIntoLevel} / ${progress.xpForNextLevel} XP"
                textSize = 10.5f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        track.post {
            if (track.width > 0) {
                val params = fill.layoutParams as FrameLayout.LayoutParams
                params.width = (track.width * progress.fraction).toInt()
                    .coerceIn(0, track.width)
                fill.layoutParams = params
            }
        }
        xpRow.addView(
            track,
            LinearLayout.LayoutParams(0, dp(32), 1f),
        )
        details.addView(xpRow)

        val statsStrip = LinearLayout(this@profileIdentityPanel).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(7), dp(2), dp(7))
            background = transparentDarkBlueStrokeBackground(this@profileIdentityPanel)
        }
        statsStrip.addView(
            statCell("🎮", profile.totalGames.toString(), "Games"),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        statsStrip.addView(statDivider())
        statsStrip.addView(
            statCell("🔥", profile.currentDayStreak.toString(), "Day Streak"),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        statsStrip.addView(statDivider())
        statsStrip.addView(
            statCell("🏆", profile.totalWins.toString(), "Wins"),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        details.addView(
            statsStrip,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )

        heroRow.addView(
            details,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.57f),
        )
        addView(
            heroRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

private fun ProfileActivity.statCell(symbol: String, value: String, label: String): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(
            TextView(this@statCell).apply {
                text = symbol
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                contentDescription = label
            },
            LinearLayout.LayoutParams(dp(26), dp(22)),
        )
        addView(
            TextView(this@statCell).apply {
                text = value
                textSize = 17f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            TextView(this@statCell).apply {
                text = label
                textSize = 9f
                maxLines = 1
                setTextColor(0xFFD5F0FF.toInt())
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

private fun ProfileActivity.statDivider(): View =
    View(this).apply {
        setBackgroundColor(0xFF176FC2.toInt())
        layoutParams = LinearLayout.LayoutParams(dp(1), dp(48))
    }
