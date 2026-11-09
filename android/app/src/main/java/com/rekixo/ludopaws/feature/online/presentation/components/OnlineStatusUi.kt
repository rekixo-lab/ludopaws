package com.rekixo.ludopaws.ui.online

import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.feature.online.abandonRemoteSessionState
import com.rekixo.ludopaws.feature.online.proofDetails

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

internal fun MainActivity.onlineResultPanel(): FrameLayout {
    resultCard = LudoPawsResultCard(this)
    resultTitleText = resultCard.titleView
    resultSubtitleText = resultCard.subtitleView
    return resultCardOverlay(this, resultCard, reserveBottomDp = 148).apply {
        addView(GameResultArtView(this@onlineResultPanel).apply {
            mode = GameResultArtView.Mode.ONLINE
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, 0, FrameLayout.LayoutParams(-1, -1))

        val actions = LinearLayout(this@onlineResultPanel).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), 0)
        }
        actions.addView(Button(this@onlineResultPanel).apply {
            text = "SHARE"
            isAllCaps = false
            LudoProofTheme.secondary(this)
            setOnClickListener {
                currentState?.let { state ->
                    val mode = when (state.matchMode) {
                        "TEAM_UP" -> "Team Up"
                        "FRIENDS" -> "Friends"
                        else -> "Online"
                    }
                    com.rekixo.ludopaws.feature.sharing.MatchShareCard.share(
                        this@onlineResultPanel, state, mode, playerId,
                        com.rekixo.ludopaws.feature.online.domain.OnlineLudoPawsCharacterPolicy.characterIdsBySeat(state),
                    )
                }
            }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(5) })
        actions.addView(Button(this@onlineResultPanel).apply {
            text = if (gameMode == GameMode.FRIENDS) "INVITE AGAIN" else "PLAY AGAIN"
            isAllCaps = false
            LudoProofTheme.positive(this)
            setOnClickListener {
                if (currentState?.status != "FINISHED" || !canSubmitOnlineGameplayAction()) return@setOnClickListener
                val mode = gameMode
                continueAfterRemoteResultAd {
                    openGameMode(
                        mode,
                        resumeSavedMatch = false,
                        beforeLaunch = {
                            runCatching { this@onlineResultPanel.abandonRemoteSessionState() }.getOrDefault(false)
                        },
                        onLaunched = { finish() },
                    )
                }
            }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(7) })
        actions.addView(Button(this@onlineResultPanel).apply {
            text = "HOME"
            isAllCaps = false
            LudoProofTheme.secondary(this)
            setOnClickListener { requestRemoteExitAfterWinInterstitial() }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        for (index in 0 until actions.childCount) {
            (actions.getChildAt(index) as Button).apply {
                setPadding(dp(4), 0, dp(4), 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setAutoSizeTextTypeUniformWithConfiguration(9, 16, 1, TypedValue.COMPLEX_UNIT_SP)
            }
        }
        addView(Button(this@onlineResultPanel).apply {
            text = "MATCH DETAILS"
            LudoProofTheme.secondary(this)
            textSize = 13f
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                val state = currentState ?: return@setOnClickListener
                ArcadeDialogs.showProofHistory(this@onlineResultPanel, "LAST ROLL", proofDetails(state))
            }
        }, FrameLayout.LayoutParams(-2, dp(48), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(94)
        })
        addView(actions, FrameLayout.LayoutParams(
            minOf(dp(360), resources.displayMetrics.widthPixels - dp(28)),
            dp(52),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { bottomMargin = dp(36) })
    }
}
