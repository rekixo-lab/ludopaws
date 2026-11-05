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

internal fun StoreActivity.diceCatalogContent(friendshipEntry: View? = null): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL

        val panels = StoreCosmeticCatalog.dice.filter { it.contentAvailable } // Released products only.
        val ownedIds = cosmetics.ownedCosmeticIds()
        val selectedId = cosmetics.selectedId(com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory.DICE)

        panels.chunked(2).forEachIndexed { rowIndex, rowDice ->
            val row =
                LinearLayout(this@diceCatalogContent).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                }

            rowDice.forEach { dice ->
                row.addView(
                    dicePanel(dice, dice.id in ownedIds, dice.id in ownedIds && selectedId == dice.id),
                    LinearLayout.LayoutParams(
                        0,
                        dp(208),
                        1f,
                    ).apply {
                        setMargins(dp(4), 0, dp(4), 0)
                    },
                )
            }

            repeat(2 - rowDice.size) {
                row.addView(
                    TextView(this@diceCatalogContent),
                    LinearLayout.LayoutParams(0, dp(208), 1f),
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

private fun StoreActivity.dicePanel(
    dice: StoreCosmetic,
    owned: Boolean,
    selected: Boolean,
): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(8), dp(6), dp(8))

        // Each released item has real artwork; draft items never reach this view.
        background =
            if (!owned) {
                plywoodBackground(this@dicePanel, cornerRadiusDp = 13f)
            } else {
                playerCountSelectionBackground(
                    context = this@dicePanel,
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
        isClickable = true
        isFocusable = true
        contentDescription =
            if (!owned) "${dice.title}, available to unlock"
            else "${dice.title}, unlocked" + if (selected) ", selected" else ""

        addView(
            TextView(this@dicePanel).apply {
                text = dice.title
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
            StoreCatalogArtwork.dice(context, dice.id),
            LinearLayout.LayoutParams(
                dp(104),
                dp(104),
            ).apply {
                topMargin = dp(1)
                bottomMargin = dp(1)
            },
        )

        addView(
            diceStatusBadge(dice = dice, owned = owned, selected = selected),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            },
        )

        setOnClickListener { handleCosmeticTap(dice) }
        AppFocusRing.apply(this, cornerRadiusDp = 13f)
    }

private fun StoreActivity.diceStatusBadge(
    dice: StoreCosmetic,
    owned: Boolean,
    selected: Boolean,
): TextView =
    TextView(this).apply {
        text = when {
            selected -> "✓ EQUIPPED"
            owned -> "🔓 UNLOCKED"
            else -> cosmeticPurchaseLabel(dice)
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
