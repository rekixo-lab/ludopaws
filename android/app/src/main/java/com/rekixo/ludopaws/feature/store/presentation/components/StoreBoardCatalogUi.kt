package com.rekixo.ludopaws.feature.store.presentation.components

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.core.ui.art.playerCountSelectionBackground
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.core.ui.components.playSelectionPopAnimation
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.feature.store.presentation.actions.handleCosmeticTap

private const val LOCKED_BOARD_TITLE = "LOCKED BOARD"

internal fun StoreActivity.boardCatalogContent(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL

        // Render the full catalog. Draft content stays non-clickable until its
        // renderer/artwork gate is explicitly enabled in the catalog.
        val panels = StoreCosmeticCatalog.boards

        // Use the same two-card row rhythm as Paws so every board preview gets more room.
        panels.chunked(2).forEachIndexed { rowIndex, rowBoards ->
            val row =
                LinearLayout(this@boardCatalogContent).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                }

            rowBoards.forEach { board ->
                row.addView(
                    boardPanel(board),
                    LinearLayout.LayoutParams(
                        0,
                        dp(188),
                        1f,
                    ).apply {
                        setMargins(dp(4), 0, dp(4), 0)
                    },
                )
            }

            repeat(2 - rowBoards.size) {
                row.addView(
                    TextView(this@boardCatalogContent),
                    LinearLayout.LayoutParams(0, dp(188), 1f),
                )
            }

            addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (rowIndex > 0) topMargin = dp(10)
                },
            )
        }
    }

private fun StoreActivity.boardPanel(
    board: StoreCosmetic,
): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(8), dp(6), dp(8))

        val locked = !board.contentAvailable
        val owned = cosmetics.isOwned(board.id)
        val selected =
            owned &&
                cosmetics.selectedId(board.category) == board.id

        // Only the timber panel is used. No blue/purple arcade face or coloured icon well.
        background =
            if (locked || !owned) {
                plywoodBackground(this@boardPanel, cornerRadiusDp = 13f)
            } else {
                playerCountSelectionBackground(
                    context = this@boardPanel,
                    selected = selected,
                    outerCornerRadiusDp = 13f,
                    checkBadgeAtTopEnd = true,
                    emphasizeSelected = true,
                    showCheckBadge = selected,
                    checkMarkScale = 2.0f,
                    useRecessedPlywood = false,
                )
            }
        elevation = dp(if (selected) 4 else 2).toFloat()
        isClickable = !locked
        isFocusable = !locked
        contentDescription =
            when {
                locked && board.remoteManaged -> "${board.title}, not available in this app build yet"
                locked -> "$LOCKED_BOARD_TITLE, locked, coming soon"
                !owned -> "${board.title}, available to unlock"
                else -> "${board.title}, unlocked" + if (selected) ", selected" else ""
            }

        addView(
            TextView(this@boardPanel).apply {
                text = if (locked && !board.remoteManaged) LOCKED_BOARD_TITLE else board.title
                textSize = 14.5f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                setShadowLayer(2f, 0f, dp(1).toFloat(), 0xAA3B2414.toInt())
                setPadding(0, 0, 0, dp(8))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        if (locked) {
            // Leave the locked placeholder blank; its title and lock state are the only preview.
            addView(
                View(this@boardPanel).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(82),
                ),
            )
        } else {
            addView(
                BoardIconView(this@boardPanel, board.id).apply {
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    contentDescription = "${board.title} board preview"
                },
                LinearLayout.LayoutParams(
                    dp(104),
                    dp(104),
                ).apply {
                    topMargin = dp(1)
                    bottomMargin = dp(1)
                },
            )
        }

        addView(
            boardStatusBadge(board = board, locked = locked, owned = owned, selected = selected),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            },
        )

        if (locked) {
            addView(
                TextView(this@boardPanel).apply {
                    text = "COMING SOON"
                    textSize = 8.5f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(0xFF65421E.toInt())
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    maxLines = 1
                    letterSpacing = 0.02f
                    setPadding(0, dp(3), 0, 0)
                },
            )
        } else {
            setOnClickListener { clicked ->
                clicked.playSelectionPopAnimation {
                    handleCosmeticTap(board)
                }
            }
            AppFocusRing.apply(this, cornerRadiusDp = 13f)
        }
    }

private fun StoreActivity.boardStatusBadge(
    board: StoreCosmetic,
    locked: Boolean,
    owned: Boolean,
    selected: Boolean,
): TextView =
    TextView(this).apply {
        text = when {
            locked -> "🔒 COMING SOON"
            selected -> "✓ EQUIPPED"
            owned -> "🔓 UNLOCKED"
            board.gemPrice > 0 -> "${board.gemPrice} GEMS"
            board.requiredAdViews > 0 -> "${board.requiredAdViews} ADS"
            else -> "GET"
        }
        textSize = 9.5f
        setTypeface(Typeface.DEFAULT_BOLD)
        setTextColor(
            when {
                locked -> 0xFF65421E.toInt()
                selected -> 0xFF67430F.toInt()
                owned -> 0xFF104B29.toInt()
                else -> 0xFF235A86.toInt()
            },
        )
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        letterSpacing = 0.015f
        setPadding(dp(7), dp(4), dp(7), dp(4))
        background =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                when {
                    locked ->
                        intArrayOf(0xFFE5D2B5.toInt(), 0xFFC4A47B.toInt())
                    selected ->
                        intArrayOf(0xFFFFF0AD.toInt(), 0xFFFFCB45.toInt())
                    else ->
                        intArrayOf(0xFFBDF5CB.toInt(), 0xFF7CE69A.toInt())
                },
            ).apply {
                cornerRadius = dp(999).toFloat()
                setStroke(
                    dp(1),
                    when {
                        locked -> 0xFF987044.toInt()
                        selected -> 0xFFFFF5C9.toInt()
                        else -> 0xFF47B86C.toInt()
                    },
                )
            }
    }


/** The same board, relief, garden and joined houses used in gameplay; no duplicate flat artwork. */
private class BoardIconView(
    context: android.content.Context,
    previewThemeId: String,
) : android.widget.FrameLayout(context) {
    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        contentDescription = "3D gameplay board preview"
        addView(com.rekixo.ludopaws.LudoPawsReactiveBoardView(context).apply {
            setBoardThemeOverride(previewThemeId)
            bind(com.rekixo.ludopaws.MatchSnapshot(
                matchId = "store-$previewThemeId-preview", status = "WAITING", hostPlayerId = "",
                players = emptyList(), turnSeat = 0, randomEventIndex = 0, pendingRoll = null,
                winnerPlayerId = null, rulesetId = "ludoproof-standard-v5", history = emptyList(),
            ), playerId = null, perspectiveColor = "YELLOW")
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    // The store card owns the tap; this is a passive preview, with no selectable pawns.
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean = false
}
