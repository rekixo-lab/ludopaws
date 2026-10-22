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
            // Both local modes show three separate recessed-plywood selectors.
            // There is deliberately no enclosing wooden group/background.
            setPadding(
                dp(8),
                if (isComputerMode) 0 else dp(if (isCompactSetup()) 10 else 12),
                dp(8),
                if (isComputerMode) 0 else dp(24),
            )
            // The page itself has no backing panel; each selector draws its
            // own recessed wood background with independent rounded corners.
            background = null
        }
    contentHost.addView(
        content,
        FrameLayout.LayoutParams(
            contentWidth,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            if (isComputerMode) {
                // Center the complete VS COMPUTER setup in the mobile
                // viewport when it fits; Pass & Play keeps its existing top flow.
                Gravity.CENTER
            } else {
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            },
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
        // Back/Settings stay fixed; the ROBOT heading sits freely on
        // the page above the independent selection panels.
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
            // Use the same plywood action surfaces as the Online page.
            for (index in 0 until childCount) {
                getChildAt(index).background =
                    plywoodBackground(context = this@showSetup, cornerRadiusDp = 999f)
            }
        }
    } else {
        content.addView(backHeader("PASS & PLAY"))
    }

    // Paw, player count and color are three INDEPENDENT recessed-plywood
    // sections placed directly on the page. No combined plywood parent shell.
    // Robot retains a standalone title; Pass & Play keeps its existing header.
    if (isComputerMode) {
        content.addView(
            centeredSetupTitle("ROBOT"),
            setupSectionParams(if (isCompactSetup()) 8 else 10),
        )
    }

    content.addView(
        characterPanel(),
        setupSectionParams(if (isCompactSetup()) 8 else 10),
    )
    content.addView(
        playersPanel(),
        setupSectionParams(if (isCompactSetup()) 7 else 9),
    )
    content.addView(
        colorPanel(),
        setupSectionParams(if (isCompactSetup()) 7 else 9),
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
                dp(if (isCompactSetup()) 14 else 18),
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
            if (isComputerMode) 0 else dp(if (isCompactSetup()) 10 else 12) + safeTopInset,
            content.paddingRight,
            if (isComputerMode) 0 else content.paddingBottom,
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
