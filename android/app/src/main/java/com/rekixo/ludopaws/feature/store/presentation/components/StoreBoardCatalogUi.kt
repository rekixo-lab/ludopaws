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
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.feature.store.presentation.actions.handleCosmeticTap

internal fun StoreActivity.boardCatalogContent(friendshipEntry: View? = null): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL

        val panels = StoreCosmeticCatalog.boards.filter { it.contentAvailable } // Released products only.
        val ownedIds = cosmetics.ownedCosmeticIds()
        val selectedId = cosmetics.selectedId(com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory.BOARD)
        val selectionUpdates = mutableMapOf<String, (Boolean, Boolean) -> Unit>()
        refreshBoardSelection = {
            val currentOwned = cosmetics.ownedCosmeticIds()
            val currentSelected = cosmetics.selectedId(com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory.BOARD)
            selectionUpdates.forEach { (id, update) ->
                update(id in currentOwned, id in currentOwned && id == currentSelected)
            }
        }

        // Use the same two-card row rhythm as Paws so every board preview gets more room.
        panels.chunked(2).forEachIndexed { rowIndex, rowBoards ->
            val row =
                LinearLayout(this@boardCatalogContent).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                }

            val optionCount = rowBoards.maxOf { item ->
                if (item.id in ownedIds) 0 else listUnlockOptions(item).size
            }
            val cardHeight = dp(if (optionCount < 2) 208 else 145 + 51 * optionCount)

            rowBoards.forEach { board ->
                row.addView(
                    boardPanel(board, board.id in ownedIds, board.id in ownedIds && selectedId == board.id, selectionUpdates),
                    LinearLayout.LayoutParams(
                        0,
                        cardHeight,
                        1f,
                    ).apply {
                        setMargins(dp(4), 0, dp(4), 0)
                    },
                )
            }

            repeat(2 - rowBoards.size) {
                row.addView(
                    TextView(this@boardCatalogContent),
                    LinearLayout.LayoutParams(0, cardHeight, 1f),
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
            if (rowIndex == 0 && friendshipEntry != null) {
                addView(friendshipEntry, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                })
            }
        }
    }

private fun StoreActivity.boardPanel(
    board: StoreCosmetic,
    owned: Boolean,
    selected: Boolean,
    selectionUpdates: MutableMap<String, (Boolean, Boolean) -> Unit>,
): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(8), dp(6), dp(8))

        isClickable = true
        isFocusable = true

        addView(
            TextView(this@boardPanel).apply {
                text = board.title
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

        addView(
            StoreCatalogArtwork.board(context, board.id),
            LinearLayout.LayoutParams(
                dp(104),
                dp(104),
            ).apply {
                topMargin = dp(1)
                bottomMargin = dp(1)
            },
        )

        val statusBadge = boardStatusBadge(board = board, owned = owned, selected = selected)
        addView(
            statusBadge,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            },
        )

        val paymentOptions = if (!owned) cosmeticPaymentOptions(board) else null
        paymentOptions?.let { addView(it, LinearLayout.LayoutParams(-1, -2)) }

        val updateSelection: (Boolean, Boolean) -> Unit = { currentOwned, currentSelected ->
            isSelected = currentSelected
            background = if (!currentOwned) {
                plywoodBackground(this@boardPanel, cornerRadiusDp = 13f)
            } else {
                playerCountSelectionBackground(
                    context = this@boardPanel,
                    selected = currentSelected,
                    outerCornerRadiusDp = 13f,
                    checkBadgeAtTopEnd = true,
                    emphasizeSelected = true,
                    showCheckBadge = currentSelected,
                    checkMarkScale = 2.0f,
                    useRecessedPlywood = false,
                )
            }
            elevation = dp(if (currentSelected) 4 else 2).toFloat()
            contentDescription = when {
                !currentOwned -> "${board.title}, available to unlock"
                currentSelected -> "${board.title}, selected"
                else -> "${board.title}, unlocked, tap to select"
            }
            boardStatusBadge(board, currentOwned, currentSelected, statusBadge)
            statusBadge.visibility = if (currentOwned || paymentOptions == null) View.VISIBLE else View.GONE
            paymentOptions?.visibility = if (currentOwned) View.GONE else View.VISIBLE
        }
        selectionUpdates[board.id] = updateSelection
        updateSelection(owned, selected)

        setOnClickListener { handleCosmeticTap(board) }
        AppFocusRing.apply(this, cornerRadiusDp = 13f)
    }

private fun StoreActivity.boardStatusBadge(
    board: StoreCosmetic,
    owned: Boolean,
    selected: Boolean,
    badge: TextView = TextView(this),
): TextView =
    badge.apply {
        text = when {
            selected -> "✓ EQUIPPED"
            owned -> "TAP TO SELECT"
            else -> cosmeticPurchaseLabel(board)
        }
        textSize = 9.5f
        setTypeface(Typeface.DEFAULT_BOLD)
        setTextColor(
            when {
                selected -> 0xFF67430F.toInt()
                owned -> 0xFF104B29.toInt()
                else -> 0xFF235A86.toInt()
            },
        )
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 2
        letterSpacing = 0.015f
        setPadding(dp(7), dp(4), dp(7), dp(4))
        background =
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                when {
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
                        selected -> 0xFFFFF5C9.toInt()
                        else -> 0xFF47B86C.toInt()
                    },
                )
            }
    }
