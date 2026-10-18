package com.rekixo.ludopaws.ui.home

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.*

internal fun HomeActivity.modeSection(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        // Keep the exact pre-polish section height so the user-approved bottom
        // spacing does not move: compact 262dp, regular 296dp.
        val rowHeight = dp(if (isCompact()) 82 else 92)
        val rowGap = dp(if (isCompact()) 8 else 10)

        addView(
            homeModeRow(
                cards =
                    listOf(
                        HomeModeCard(
                            mode = GameMode.ONLINE,
                            title = "Online",
                            subtitle = "Find match",
                            artMode = ModeArtView.Mode.ONLINE,
                            tone = HomeModeTone.ONLINE,
                        ),
                        HomeModeCard(
                            mode = GameMode.TEAM_UP,
                            title = "Team Up",
                            subtitle = "2 vs 2",
                            artMode = ModeArtView.Mode.TEAM_UP,
                            tone = HomeModeTone.TEAM_UP,
                        ),
                    ),
                height = rowHeight,
            ),
        )

        addView(
            homeModeRow(
                cards =
                    listOf(
                        HomeModeCard(
                            mode = GameMode.FRIENDS,
                            title = "Friends",
                            subtitle = "Private",
                            artMode = ModeArtView.Mode.FRIENDS,
                            tone = HomeModeTone.FRIENDS,
                        ),
                        HomeModeCard(
                            mode = GameMode.COMPUTER,
                            title = "Robot",
                            subtitle = "CPU",
                            artMode = ModeArtView.Mode.COMPUTER,
                            tone = HomeModeTone.COMPUTER,
                        ),
                    ),
                height = rowHeight,
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                rowHeight,
            ).apply {
                topMargin = rowGap
            },
        )

        addView(
            homeWideModeCard(
                HomeModeCard(
                    mode = GameMode.PASS_AND_PLAY,
                    title = "Pass & Play",
                    subtitle = "Offline",
                    artMode = ModeArtView.Mode.PASS_AND_PLAY,
                    tone = HomeModeTone.PASS_AND_PLAY,
                ),
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                rowHeight,
            ).apply {
                topMargin = rowGap
                marginStart = dp(2)
                marginEnd = dp(2)
            },
        )
    }

private data class HomeModeCard(
    val mode: GameMode,
    val title: String,
    val subtitle: String,
    val artMode: ModeArtView.Mode,
    val tone: HomeModeTone,
)

private fun HomeActivity.homeModeRow(
    cards: List<HomeModeCard>,
    height: Int,
): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER

        cards.forEach { card ->
            addView(
                homeGameCard(card),
                LinearLayout.LayoutParams(
                    0,
                    height,
                    1f,
                ).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                },
            )
        }
    }

private fun HomeActivity.homeGameCard(
    card: HomeModeCard,
): FrameLayout =
    FrameLayout(this).apply {
        background =
            homeModeButtonBackground(
                context = this@homeGameCard,
                tone = card.tone,
            )
        elevation = dp(8).toFloat()
        isClickable = true
        isFocusable = true
        contentDescription = "${card.title}, ${card.subtitle}"
        setOnClickListener {
            openGameMode(card.mode)
        }

        addView(
            LinearLayout(this@homeGameCard).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dp(if (isCompact()) 9 else 11),
                    dp(5),
                    dp(if (isCompact()) 18 else 20),
                    dp(5),
                )

                addView(
                    ModeArtView(this@homeGameCard).apply {
                        mode = card.artMode
                        importantForAccessibility =
                            View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(
                        dp(if (isCompact()) 50 else 56),
                        dp(if (isCompact()) 54 else 62),
                    ),
                )

                addView(
                    modeLabel(
                        title = card.title,
                        subtitle = card.subtitle,
                    ),
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1f,
                    ),
                )
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

private fun HomeActivity.homeWideModeCard(
    card: HomeModeCard,
): FrameLayout =
    FrameLayout(this).apply {
        background =
            homeModeButtonBackground(
                context = this@homeWideModeCard,
                tone = card.tone,
                wide = true,
            )
        elevation = dp(8).toFloat()
        isClickable = true
        isFocusable = true
        contentDescription = "${card.title}, ${card.subtitle}"
        setOnClickListener {
            openGameMode(card.mode)
        }

        addView(
            LinearLayout(this@homeWideModeCard).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(
                    dp(if (isCompact()) 38 else 46),
                    dp(5),
                    dp(if (isCompact()) 38 else 46),
                    dp(5),
                )

                addView(
                    ModeArtView(this@homeWideModeCard).apply {
                        mode = card.artMode
                        importantForAccessibility =
                            View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(
                        dp(if (isCompact()) 76 else 84),
                        dp(if (isCompact()) 58 else 66),
                    ),
                )

                addView(
                    modeLabel(
                        title = card.title,
                        subtitle = card.subtitle,
                        wide = true,
                    ),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                    ).apply {
                        marginStart = dp(if (isCompact()) 2 else 4)
                    },
                )
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

private fun HomeActivity.modeLabel(
    title: String,
    subtitle: String,
    wide: Boolean = false,
): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER

        addView(
            modeTitle(title, wide),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        addView(
            TextView(this@modeLabel).apply {
                text = subtitle
                textSize =
                    when {
                        wide && isCompact() -> 10f
                        wide -> 12f
                        isCompact() -> 9.5f
                        else -> 11f
                    }
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                setTextColor(0xFFE3F6FF.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
                letterSpacing = 0.015f
                setShadowLayer(
                    dp(1).toFloat(),
                    0f,
                    dp(1).toFloat(),
                    0x90001A38.toInt(),
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(2)
            },
        )
    }

private fun HomeActivity.modeTitle(
    text: String,
    wide: Boolean = false,
): TextView =
    TextView(this).apply {
        this.text = text
        textSize =
            when {
                wide && isCompact() -> 19f
                wide -> 22f
                isCompact() -> 16f
                else -> 18f
            }
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        letterSpacing = 0.005f
        setShadowLayer(
            dp(2).toFloat(),
            0f,
            dp(1).toFloat(),
            0xC9001A38.toInt(),
        )
    }

internal fun HomeActivity.fairPlayStrip(): TextView =
    TextView(this).apply {
        text =
            "✓  FAIR PLAY\nOnline: remote authority  •  Local: on-device v4 recomputation"
        LudoProofTheme.body(
            this,
            if (isCompact()) 11f else 12f,
            centered = true,
            bright = true,
        )
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background =
            LudoProofTheme.hudPanelDrawable(
                this@fairPlayStrip,
                goldBorder = true,
            )
        elevation = dp(4).toFloat()
        isClickable = true
        isFocusable = true
        contentDescription = "Fair play information"
        setOnClickListener {
            ArcadeDialogs.showProofHistory(
                this@fairPlayStrip,
                "FAIR PLAY",
                "Online dice results are generated and persisted by the native Ludo Paws match server. Offline dice use Android SecureRandom and a local receipt; local history is not independent remote verification.",
            )
        }
    }

internal fun HomeActivity.footer(): TextView =
    TextView(this).apply {
        text = "SERVER-AUTHORITATIVE ONLINE • NO OUTCOME REROLLS"
        LudoProofTheme.body(
            this,
            10f,
            centered = true,
        )
        setPadding(dp(8), dp(4), dp(8), 0)
    }

internal fun HomeActivity.shareLudoPaws() {
    val storeUrl =
        "https://play.google.com/store/apps/details?id=$packageName"
    val shareText =
        buildString {
            append(getString(R.string.share_message))
            append("\n\n")
            append(storeUrl)
        }

    val share =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_SUBJECT,
                getString(R.string.share_subject),
            )
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

    startActivity(
        Intent.createChooser(
            share,
            getString(R.string.share_chooser_title),
        ),
    )
}
