package com.rekixo.ludopaws

import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.ui.game.addGameTopActions
import com.rekixo.ludopaws.ui.online.*

/**
 * Online / Team Up / Friends Android view composition.
 *
 * Kept separate from MainActivity lifecycle, session recovery and transport
 * callbacks. Reuses the same view IDs, handlers and inset strategy.
 */
internal fun MainActivity.buildOnlineMatchScreen() {
        val (root, host) =
            LudoProofTheme
                .arcadeRoot(this)
        arcadeRootView = root

        val scroll =
            ScrollView(this).apply {
                isFillViewport =
                    true
                overScrollMode =
                    View.OVER_SCROLL_NEVER
            }

        val contentHost =
            FrameLayout(this)
        scroll.addView(
            contentHost,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
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
                0
            } else {
                dp(horizontalPaddingDp)
            }

        val content =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setPadding(
                    0,
                    dp(14),
                    0,
                    dp(28),
                )
        }

        contentHost.addView(
            content,
            FrameLayout.LayoutParams(
                contentWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or
                    Gravity.CENTER_HORIZONTAL,
            ),
        )

        host.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        registerOnlineBackCallback()

        if (
            gameMode !=
            GameMode.ONLINE
        ) {
            val hero = onlineHero()
            onlineHeroPanel = hero
            content.addView(
                hero,
                onlineSectionParams(
                    if (isCompactOnline()) 12 else 16,
                ).apply {
                    leftMargin =
                        sectionSideMargin
                    rightMargin =
                        sectionSideMargin
                },
            )
        }

        lobbyPanel =
            onlineLobbyPanel()
        content.addView(
            lobbyPanel,
            onlineSectionParams(
                if (isCompactOnline()) 12 else 16,
            ).apply {
                leftMargin =
                    sectionSideMargin
                rightMargin =
                    sectionSideMargin
            },
        )

        matchStatusPanel =
            onlineMatchStatusPanel()
                .apply {
                    visibility =
                        View.GONE
                }
        content.addView(
            matchStatusPanel,
            onlineSectionParams(
                if (isCompactOnline()) 4 else 6,
            ).apply {
                leftMargin =
                    sectionSideMargin
                rightMargin =
                    sectionSideMargin
            },
        )

        resultPanel =
            onlineResultPanel()
                .apply {
                    visibility =
                        View.GONE
                }
        content.addView(
            resultPanel,
            onlineSectionParams(
                if (isCompactOnline()) 12 else 16,
            ).apply {
                leftMargin =
                    sectionSideMargin
                rightMargin =
                    sectionSideMargin
            },
        )

        boardView =
            LudoBoardView(this).apply {
                onTokenSelected = {
                        tokenIndex ->
                    moveToken(
                        tokenIndex,
                    )
                }
            }

        boardFrame =
            FrameLayout(this).apply {
                visibility =
                    View.GONE
                clipChildren =
                    false
                clipToPadding =
                    false
            }
        boardFrame.addView(
            boardView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(
            boardFrame,
            onlineSectionParams(
                if (isCompactOnline()) 4 else 6,
            ).apply {
                leftMargin = dp(1)
                rightMargin = dp(1)
            },
        )

        actionPanel =
            onlineActionPanel()
                .apply {
                    visibility =
                        View.GONE
                }
        content.addView(
            actionPanel,
            onlineSectionParams(
                if (isCompactOnline()) 2 else 4,
            ),
        )

        val topActionsBar =
            addGameTopActions(
                host = host,
                context = this,
                onBack = { requestRemoteExitAfterWinInterstitial() },
                onSettings = {
                    ArcadeDialogs.showSettings(this) {
                        currentState?.let { state ->
                            OnlineLudoPawsPresentation.render(
                                activity = this,
                                previous = state,
                                current = state,
                            )
                        }
                    }
                },
            ).apply {
                // Use the shared plywood skin only for the Online screen's Back and Settings.
                for (index in 0 until childCount) {
                    getChildAt(index).background =
                        plywoodBackground(
                            context = this@buildOnlineMatchScreen,
                            cornerRadiusDp = 999f,
                        )
                }
            }

        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, insets ->
            val cutoutTopInset =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    insets.displayCutout?.safeInsetTop ?: 0
                } else {
                    0
                }
            val statusBarTopInset =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetTop
                }
            val safeTopInset = maxOf(cutoutTopInset, statusBarTopInset)
            content.setPadding(
                content.paddingLeft,
                dp(14) + safeTopInset,
                content.paddingRight,
                content.paddingBottom,
            )
            topActionsBar.translationY = safeTopInset.toFloat()
            insets
        }
        root.requestApplyInsets()
}
