package com.rekixo.ludopaws.ui.online

import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.*

internal fun MainActivity.onlineMatchStatusPanel(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(
            dp(if (isCompactOnline()) 8 else 10),
            dp(if (isCompactOnline()) 5 else 7),
            dp(if (isCompactOnline()) 8 else 10),
            dp(if (isCompactOnline()) 5 else 7),
        )
        background =
            LudoProofTheme.hudPanelDrawable(
                this@onlineMatchStatusPanel,
                goldBorder = false,
            )
        elevation = dp(3).toFloat()

        matchInfoText =
            TextView(this@onlineMatchStatusPanel).apply {
                text = "No active match"
                LudoProofTheme.body(
                    this,
                    9f,
                    centered = true,
                    bright = true,
                )
                setTextColor(0xFF6EE7FF.toInt())
                maxLines = 1
            }
        addView(matchInfoText)

        turnText =
            TextView(this@onlineMatchStatusPanel).apply {
                text = "Waiting for a match."
                LudoProofTheme.title(
                    this,
                    if (isCompactOnline()) 16f else 18f,
                    gold = true,
                )
                setPadding(dp(4), dp(2), dp(4), dp(2))
                maxLines = 2
            }
        addView(turnText)

        playersText =
            TextView(this@onlineMatchStatusPanel).apply {
                text = "Players will appear here."
                LudoProofTheme.body(
                    this,
                    if (isCompactOnline()) 9.5f else 10.5f,
                    centered = true,
                    bright = true,
                )
                setPadding(dp(4), 0, dp(4), 0)
                maxLines = 4
            }
        addView(playersText)
    }

internal fun MainActivity.onlineResultPanel(): FrameLayout =
    FrameLayout(this).apply {
        val height =
            dp(
                if (isCompactOnline()) {
                    190
                } else {
                    210
                },
            )

        addView(
            GameResultArtView(
                this@onlineResultPanel,
            ).apply {
                mode = GameResultArtView.Mode.ONLINE
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                height,
            ),
        )

        val copy =
            LinearLayout(
                this@onlineResultPanel,
            ).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dp(if (isCompactOnline()) 108 else 142),
                    dp(12),
                    dp(12),
                    dp(12),
                )

                addView(
                    TextView(
                        this@onlineResultPanel,
                    ).apply {
                        text = "MATCH COMPLETE"
                        LudoProofTheme.body(
                            this,
                            9.5f,
                            bright = true,
                        )
                        setTextColor(0xFF68E8FF.toInt())
                    },
                )

                resultTitleText =
                    TextView(
                        this@onlineResultPanel,
                    ).apply {
                        text = "WINNER"
                        LudoProofTheme.title(
                            this,
                            if (isCompactOnline()) 20f else 23f,
                            gold = true,
                        )
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setPadding(0, dp(3), 0, 0)
                    }
                addView(resultTitleText)

                resultSubtitleText =
                    TextView(
                        this@onlineResultPanel,
                    ).apply {
                        text = "Verified result"
                        LudoProofTheme.body(
                            this,
                            if (isCompactOnline()) 10f else 11f,
                            bright = true,
                        )
                        gravity = Gravity.START
                        setPadding(0, dp(3), 0, 0)
                        maxLines = 10
                    }
                addView(resultSubtitleText)
            }

        addView(
            copy,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                height,
            ),
        )
    }
