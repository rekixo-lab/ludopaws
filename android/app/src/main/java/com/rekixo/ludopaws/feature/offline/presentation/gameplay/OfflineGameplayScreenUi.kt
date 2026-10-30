package com.rekixo.ludopaws.ui.offline.gameplay

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.feature.offline.*
import com.rekixo.ludopaws.feature.offline.presentation.feedback.OfflineFeedbackAction
import com.rekixo.ludopaws.feature.offline.presentation.feedback.OfflineLudoPawsFeedbackDispatcher
import com.rekixo.ludopaws.feature.store.domain.PostWinInterstitialPolicy
import com.rekixo.ludopaws.ui.game.addGameTopActions
import com.rekixo.ludopaws.ui.dialogs.showSettingsDialog
import com.rekixo.ludopaws.ui.offline.common.*
import com.rekixo.ludopaws.ui.offline.setup.*

internal fun OfflineGameActivity.showGame(snapshot: MatchSnapshot?) {
    val state =
        snapshot ?: run {
            showSetup()
            return
        }

    prepareOfflineUiTransition()

    if (
        PostWinInterstitialPolicy.shouldShowOffline(
            matchStatus = state.status,
            winnerPlayerId = state.winnerPlayerId,
            humanPlayerId = session.localPlayerId(),
            isComputerMode = isComputerMode,
        )
    ) {
        preparePostWinInterstitialAd()
    }

    val (root, host) =
        LudoProofTheme.arcadeRoot(
            context = this,
            gameplayBackground = true,
            gameplayBoardEnvironment = true,
        )

    val horizontalPaddingDp =
        LudoProofTheme
            .pageHorizontalPaddingDp(this)
    val isExpandedWidth =
        LudoProofTheme
            .isExpandedWidth(this)
    val contentWidth =
        if (isExpandedWidth) {
            minOf(
                resources.displayMetrics.widthPixels,
                dp(
                    LudoProofTheme
                        .pageMaxContentWidthDp(this),
                ),
            )
        } else {
            resources.displayMetrics.widthPixels
        }
    val sectionSideMargin =
        if (isExpandedWidth) {
            dp(8)
        } else {
            dp(horizontalPaddingDp)
        }
    // BOARD WIDTH LOCK:
    // Use the available gameplay width; the 1dp side inset is the only intentional gap.
    // The board stage stays square, so increasing width increases height uniformly.
    val boardStageWidth =
        (contentWidth - dp(2))
            .coerceAtLeast(dp(1))

    val content =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(
                0,
                dp(if (isCompactSetup()) 6 else 8),
                0,
                dp(if (isCompactSetup()) 8 else 10),
            )
        }

    host.addView(
        content,
        FrameLayout.LayoutParams(
            contentWidth,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ),
    )

    resultPanel =
        offlineResultPanel()
            .apply {
                visibility = View.GONE
            }
    host.addView(
        resultPanel,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(if (isCompactSetup()) 188 else 220),
            Gravity.CENTER,
        ),
    )

    boardView =
        LudoPawsReactiveBoardView(this).apply {
            elevation = dp(8).toFloat()
            onTokenSelected = {
                    tokenIndex ->
                val previous =
                    session.snapshot()
                runCatching {
                    session.move(
                        tokenIndex,
                    )
                }.onSuccess {
                        next ->
                    OfflineLudoPawsFeedbackDispatcher.committed(
                        context = this@showGame,
                        previous = previous,
                        current = next,
                        action = OfflineFeedbackAction.MOVE,
                    )
                    renderCommittedMove(
                        previous = previous,
                        current = next,
                    )
                }.onFailure {
                        error ->
                    showStatus(
                        error.message
                            ?: "Move failed",
                    )
                }
            }
        }

    topPlayerRail = playerRail()
    content.addView(
        requireNotNull(topPlayerRail),
        gameplaySectionParams(
            if (isCompactSetup()) 4 else 6,
        ),
    )

    // BOARD GEOMETRY LOCK:
    // Keep the approved board size independent from optional gameplay controls.
    // A weighted vertical slot would remeasure/shrink the square board whenever
    // quick chat or another future action adds height below it.
    val boardStage =
        FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            addView(
                requireNotNull(boardView),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )
        }
    content.addView(
        boardStage,
        LinearLayout.LayoutParams(
            boardStageWidth,
            boardStageWidth,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(if (isCompactSetup()) 2 else 4)
        },
    )

    bottomPlayerRail = playerRail()
    content.addView(
        requireNotNull(bottomPlayerRail),
        gameplaySectionParams(
            if (isCompactSetup()) 2 else 4,
        ),
    )

    content.addView(
        gameplayActionPanel(),
        gameplaySectionParams(
            if (isCompactSetup()) 2 else 4,
        ).apply {
            leftMargin = sectionSideMargin
            rightMargin = sectionSideMargin
        },
    )

    addGameTopActions(
        host = host,
        context = this,
        onBack = { requestOfflineExit() },
        centerContent = if (state.status == "ACTIVE") {
            com.rekixo.ludopaws.feature.store.presentation.components.GamePawsPromotionView(this).also {
                gameplayPawsPromotion = it
            }.apply {
                protect(listOfNotNull(topPlayerRail, bottomPlayerRail, boardView))
            }
        } else null,
        onSettings = {
            showSettingsDialog(
                this,
            ) {
                val current = engine.snapshot()
                if (current != null) {
                    showGame(current)
                } else {
                    showSetup()
                }
            }
        },
    )

    setContentView(root)

    // CENTER LOCK:
    // Center the board in the real device viewport, not in the vertical flow between
    // the player rails. This preserves the existing rail/dice spacing around the board.
    host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        centerBoardInViewport()
    }
    boardView?.post {
        centerBoardInViewport()
        renderGame(state)
    }
}

internal fun OfflineGameActivity.centerBoardInViewport() {
    val boardStage =
        boardView?.parent as? FrameLayout
            ?: return
    val content =
        boardStage.parent as? LinearLayout
            ?: return
    val viewport =
        content.parent as? FrameLayout
            ?: return

    if (viewport.height <= 0 || boardStage.height <= 0) {
        return
    }

    val boardCenterY =
        boardStage.top + boardStage.height / 2f
    val viewportCenterY =
        viewport.height / 2f

    content.translationY =
        viewportCenterY - boardCenterY
}

internal fun OfflineGameActivity.gameplayHud(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        contentDescription =
            if (isComputerMode) {
                "VS COMPUTER"
            } else {
                "LOCAL MATCH"
            }
        setPadding(
            dp(if (isCompactSetup()) 10 else 14),
            dp(if (isCompactSetup()) 5 else 7),
            dp(if (isCompactSetup()) 10 else 14),
            dp(if (isCompactSetup()) 5 else 7),
        )
        background = offlineGameplayHudBackground()
        elevation = dp(3).toFloat()

        turnText =
            TextView(
                this@gameplayHud,
            ).apply {
                LudoProofTheme.title(
                    this,
                    if (isCompactSetup()) 16f else 18f,
                    gold = true,
                )
                setPadding(
                    dp(3),
                    0,
                    dp(3),
                    0,
                )
            }
        addView(requireNotNull(turnText))

        infoText =
            TextView(
                this@gameplayHud,
            ).apply {
                LudoProofTheme.body(
                    this,
                    if (isCompactSetup()) 9.5f else 10.5f,
                    centered = true,
                    bright = true,
                )
                setTextColor(0xFFBFE8FF.toInt())
                setPadding(
                    dp(3),
                    dp(1),
                    dp(3),
                    0,
                )
            }
        addView(requireNotNull(infoText))
    }

internal fun OfflineGameActivity.offlineResultPanel(): FrameLayout =
    FrameLayout(this).apply {
        clipChildren = false
        clipToPadding = false

        addView(
            GameResultArtView(
                this@offlineResultPanel,
            ).apply {
                mode = GameResultArtView.Mode.OFFLINE
                contentDescription = "Match celebration"
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        resultTitleText =
            TextView(
                this@offlineResultPanel,
            ).apply {
                text = "PLAYER WINS"
                LudoProofTheme.title(
                    this,
                    if (isCompactSetup()) 22f else 28f,
                    gold = true,
                )
                gravity = Gravity.CENTER
                includeFontPadding = false
                setShadowLayer(
                    dp(5).toFloat(),
                    0f,
                    dp(2).toFloat(),
                    0xB0000000.toInt(),
                )
                contentDescription = "Winner"
            }

        addView(
            android.widget.ScrollView(this@offlineResultPanel).apply {
                isFillViewport = true
                addView(resultTitleText, FrameLayout.LayoutParams(-1, -2))
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ).apply {
                leftMargin = dp(18)
                rightMargin = dp(18)
                topMargin = dp(12)
                bottomMargin = dp(12)
            },
        )
    }


/** Low-gloss HUD surface tuned for the Pass & Play and Computer game viewport. */
private fun OfflineGameActivity.offlineGameplayHudBackground(): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(0xE50C1A28.toInt())
        cornerRadius = dp(if (isCompactSetup()) 14 else 16).toFloat()
        setStroke(
            dp(1),
            if (isComputerMode) 0xCCFFD45E.toInt() else 0xAA58DFF0.toInt(),
        )
    }
