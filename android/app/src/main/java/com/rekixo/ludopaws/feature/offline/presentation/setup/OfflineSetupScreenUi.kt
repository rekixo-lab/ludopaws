package com.rekixo.ludopaws.ui.offline.setup

import android.view.Gravity
import android.view.View
import android.graphics.Color
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.os.Build
import android.view.WindowInsets
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.art.playGamesSignInBackground
import com.rekixo.ludopaws.ui.game.addGameTopActions
import com.rekixo.ludopaws.ui.dialogs.showSettingsDialog
import com.rekixo.ludopaws.feature.offline.*
import com.rekixo.ludopaws.ui.offline.common.*
import com.rekixo.ludopaws.ui.offline.gameplay.*

internal fun OfflineGameActivity.showSetup() {
    prepareOfflineUiTransition()

    val (root, host) =
        LudoProofTheme.arcadeRoot(this)

    val scroll =
        ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
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
    val availableWidth =
        (
            resources.displayMetrics.widthPixels -
                dp(horizontalPaddingDp * 2)
            ).coerceAtLeast(1)
    val contentWidth =
        minOf(
            availableWidth,
            dp(
                LudoProofTheme
                    .pageMaxContentWidthDp(this),
            ),
        )

    val content =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            // Robot and Pass & Play show independent headings and selection
            // tiles on the bare page, without any parent plywood panels.
            setPadding(
                dp(8),
                if (isComputerMode) dp(if (isCompactSetup()) 64 else 70)
                    else dp(if (isCompactSetup()) 10 else 12),
                dp(8),
                dp(20),
            )
            // No page backing and no section backing. Each selectable tile
            // still owns its small selection/highlight treatment.
            background = null
        }
    contentHost.addView(
        content,
        FrameLayout.LayoutParams(
            contentWidth,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            // Both modes now follow the same top-aligned page flow.
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

    var fixedTopActionsBar: FrameLayout? = null
    if (isComputerMode) {
        // Back/Settings and the Robot icon/title share the exact fixed top row.
        // Selections scroll below without moving the action/title bar.
        fixedTopActionsBar = addGameTopActions(
            host = host,
            context = this,
            onBack = { requestOfflineExit() },
            onSettings = {
                showSettingsDialog(this) {
                    val current = engine.snapshot()
                    if (current != null) showGame(current) else showSetup()
                }
            },
        ).apply {
            // Robot's title belongs BETWEEN the two 52dp action controls,
            // vertically centered on their 14dp-from-top baseline.
            val availableTitleWidth = (
                resources.displayMetrics.widthPixels - dp(2 * (16 + 52 + 8))
            ).coerceAtLeast(dp(108))
            addView(
                centeredSetupTitle("ROBOT"),
                FrameLayout.LayoutParams(
                    minOf(availableTitleWidth, dp(if (isCompactSetup()) 194 else 224)),
                    dp(52),
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                ).apply {
                    topMargin = dp(14)
                },
            )
        }
    } else {
        content.addView(backHeader("PASS & PLAY"))
    }

    // Both modes: the same three flat sections with identical vertical
    // gaps, heading insets and selectable tile layouts.
    content.addView(
        characterPanel(),
        setupSectionParams(if (isCompactSetup()) 10 else 12),
    )
    content.addView(
        playersPanel(),
        setupSectionParams(if (isCompactSetup()) 10 else 12),
    )
    content.addView(
        colorPanel(),
        setupSectionParams(if (isCompactSetup()) 10 else 12),
    )

    refreshSetupSelections()

    content.addView(
        Button(this).apply {
            text = "▶  PLAY"
            textSize = if (isCompactSetup()) 21f else 24f
            isAllCaps = false
            LudoProofTheme.primary(this)
            // Reuse Online/Team Up's approved green fill and cyan stroke for
            // the Pass & Play action as well as Computer's existing PLAY action.
            background = playGamesSignInBackground(this@showSetup)
            setTextColor(Color.WHITE)
            contentDescription = if (isComputerMode) {
                "Start game against computer"
            } else {
                "Start Pass and Play game"
            }
            setOnClickListener {
                persistActiveCharacterSetup()
                showGame(
                    session.start(
                        playerCount = selectedPlayers,
                        preferredColor = if (isComputerMode) selectedColor
                            else selectedColorsBySeat.first(),
                        colorsBySeat = if (isComputerMode) null else selectedColorsBySeat,
                    ),
                )
            }
        },
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(if (isCompactSetup()) 60 else 66),
        ).apply {
            val sideMargin = if (isCompactSetup()) dp(10) else dp(34)
            setMargins(
                sideMargin,
                dp(if (isCompactSetup()) 10 else 14),
                sideMargin,
                0,
            )
        },
    )

    setContentView(root)
    root.setOnApplyWindowInsetsListener { _, insets ->
        val cutoutTop =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                insets.displayCutout?.safeInsetTop ?: 0
            } else {
                0
            }
        val statusTop =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop
            }
        val safeTopInset = maxOf(cutoutTop, statusTop)
        content.setPadding(
            content.paddingLeft,
            (if (isComputerMode) dp(if (isCompactSetup()) 64 else 70)
                else dp(if (isCompactSetup()) 10 else 12)) + safeTopInset,
            content.paddingRight,
            content.paddingBottom,
        )
        fixedTopActionsBar?.translationY = safeTopInset.toFloat()
        insets
    }
    root.requestApplyInsets()
}

internal fun OfflineGameActivity.setupHero(): FrameLayout =
    FrameLayout(this).apply {
        val heroHeight =
            dp(
                if (isCompactSetup()) {
                    162
                } else {
                    184
                },
            )

        addView(
            OfflineSetupArtView(
                this@setupHero,
            ),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                heroHeight,
            ),
        )

        val copy =
            LinearLayout(
                this@setupHero,
            ).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dp(if (isCompactSetup()) 98 else 138),
                    dp(14),
                    dp(if (isCompactSetup()) 12 else 26),
                    dp(14),
                )
            }

        copy.addView(
            TextView(this@setupHero).apply {
                text = "LUDO PAWS"
                LudoProofTheme.title(
                    this,
                    if (isCompactSetup()) 23f else 28f,
                    gold = true,
                )
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            },
        )

        copy.addView(
            TextView(this@setupHero).apply {
                text =
                    if (isComputerMode) {
                        "Pick a paw and challenge the computer"
                    } else {
                        "Pick your paws and play together"
                    }
                LudoProofTheme.body(
                    this,
                    if (isCompactSetup()) 12f else 13f,
                    bright = true,
                )
                gravity = Gravity.START
                setPadding(0, dp(4), 0, 0)
            },
        )

        copy.addView(
            TextView(this@setupHero).apply {
                text = "Classic board • four paws race home"
                LudoProofTheme.body(
                    this,
                    if (isCompactSetup()) 10.5f else 11.5f,
                )
                gravity = Gravity.START
                setPadding(0, dp(3), 0, 0)
            },
        )

        addView(
            copy,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                heroHeight,
            ),
        )
    }

internal fun OfflineGameActivity.gameTypePanel(): LinearLayout =
    selectionPanel(
        "CLASSIC LUDO",
        "Four paws race from the yard to home.",
    ).apply {
        val row =
            LinearLayout(
                this@gameTypePanel,
            ).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

        row.addView(
            choiceTile(
                "▦",
                "CLASSIC",
                "Standard Ludo rules",
                true,
                true,
            ) {},
            setupTileParams(
                if (isCompactSetup()) {
                    118
                } else {
                    128
                },
                marginDp = 4,
            ),
        )
        addView(row)

        addView(
            TextView(
                this@gameTypePanel,
            ).apply {
                text = "Board and dice looks can be changed in Settings."
                LudoProofTheme.body(
                    this,
                    10.5f,
                    centered = true,
                    bright = true,
                )
                setPadding(dp(8), dp(10), dp(8), 0)
            },
        )
    }
