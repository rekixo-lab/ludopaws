package com.rekixo.ludopaws.feature.store.presentation.components

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.core.ui.art.playerCountSelectionBackground
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.core.ui.components.playSelectionPopAnimation
import com.rekixo.ludopaws.feature.store.domain.model.StoreTab
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity

internal fun StoreActivity.storeTabs():
    LinearLayout =
    LinearLayout(this).apply {
        orientation =
            LinearLayout.HORIZONTAL
        gravity =
            Gravity.CENTER_VERTICAL

        StoreTab
            .entries
            .filter { com.rekixo.ludopaws.BuildConfig.LUDOPROOF_BILLING_ENABLED || it != StoreTab.GEMS }
            .forEach {
                    tab ->
                val selected =
                    tab ==
                        selectedTab
                addView(
                    TextView(
                        this@storeTabs,
                    ).apply {
                        text =
                            tab.label
                        textSize =
                            13f
                        setTypeface(
                            Typeface.DEFAULT_BOLD,
                        )
                        setTextColor(
                            if (selected) {
                                0xFFFFD45E.toInt()
                            } else {
                                Color.WHITE
                            },
                        )
                        setShadowLayer(
                            2f,
                            0f,
                            dp(1).toFloat(),
                            0x99000000.toInt(),
                        )
                        gravity =
                            Gravity.CENTER
                        isClickable =
                            true
                        isFocusable =
                            true
                        isSelected = selected
                        contentDescription =
                            if (selected) "${tab.label}, selected" else tab.label
                        // Match the Online/Pass & Play selection style: recessed plywood,
                        // warm gold selection wash and the corner checkmark on the active tab.
                        background =
                            playerCountSelectionBackground(
                                context = this@storeTabs,
                                selected = selected,
                                outerCornerRadiusDp = 10f,
                                checkBadgeAtTopEnd = true,
                                emphasizeSelected = true,
                                showCheckBadge = true,
                                checkMarkScale = 2.2f,
                            )
                        elevation = dp(if (selected) 3 else 1).toFloat()
                        setOnClickListener { clicked ->
                            // Delay rerender until the shared press-and-return animation finishes,
                            // otherwise renderStore() would replace this view before the press is visible.
                            clicked.playSelectionPopAnimation {
                                selectTab(tab)
                            }
                        }
                        AppFocusRing.apply(this, cornerRadiusDp = 10f)
                    },
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1f,
                    ).apply {
                        setMargins(
                            dp(2),
                            0,
                            dp(2),
                            0,
                        )
                    },
                )
            }
    }
