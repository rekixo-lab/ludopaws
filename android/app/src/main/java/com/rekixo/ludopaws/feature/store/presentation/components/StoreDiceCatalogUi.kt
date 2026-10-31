package com.rekixo.ludopaws.feature.store.presentation.components

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.DiceView
import com.rekixo.ludopaws.core.ui.art.playerCountSelectionBackground
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.core.ui.components.playSelectionPopAnimation
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.feature.store.presentation.actions.handleCosmeticTap

private const val LOCKED_DICE_TITLE = "LOCKED DICE"

internal fun StoreActivity.diceCatalogContent(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL

        val panels = StoreCosmeticCatalog.dice // Keep all Coming Soon dice visible, never purchasable.
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

        val locked = !dice.contentAvailable
        // Keep the same plywood and selected-check styling as Board Store.
        background =
            if (locked || !owned) {
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
        isClickable = !locked
        isFocusable = !locked
        contentDescription =
            when {
                locked -> "$LOCKED_DICE_TITLE, locked, coming soon"
                !owned -> "${dice.title}, available to unlock"
                else -> "${dice.title}, unlocked" + if (selected) ", selected" else ""
            }

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

        if (locked) {
            // No invented dice artwork: future dice remain unavailable placeholders.
            addView(
                View(this@dicePanel).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(82),
                ),
            )
        } else {
            addView(
                DiceView(this@dicePanel).apply {
                    // Preview this offer's actual renderer palette without equipping it.
                    setPreviewStyle(dice.id)
                    showOutcome(com.rekixo.ludopaws.feature.dice.domain.DiceRegistry.resolve(dice.id).previewFace, animate = false)
                    setPlayerTeamColor(null)
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    contentDescription = "${dice.title} dice preview"
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
            diceStatusBadge(dice = dice, locked = locked, owned = owned, selected = selected),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            },
        )

        if (locked) {
            addView(
                TextView(this@dicePanel).apply {
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
                    handleCosmeticTap(dice)
                }
            }
            AppFocusRing.apply(this, cornerRadiusDp = 13f)
        }
    }

private fun StoreActivity.diceStatusBadge(
    dice: StoreCosmetic,
    locked: Boolean,
    owned: Boolean,
    selected: Boolean,
): TextView =
    TextView(this).apply {
        text = when {
            locked -> "🔒 COMING SOON"
            selected -> "✓ EQUIPPED"
            owned -> "🔓 UNLOCKED"
            dice.gemPrice > 0 && dice.requiredAdViews > 0 -> android.text.SpannableStringBuilder("🔒 ").append(
                CosmeticPriceLabels.gems(this@diceStatusBadge, dice.gemPrice,
                    "\nOR ${cosmetics.rewardedAdProgress(dice.id).coerceAtMost(dice.requiredAdViews)}/${dice.requiredAdViews} ADS"))
            dice.gemPrice > 0 -> CosmeticPriceLabels.gems(this@diceStatusBadge, dice.gemPrice)
            dice.requiredAdViews > 0 -> "${dice.requiredAdViews} ADS"
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
        maxLines = 2
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
