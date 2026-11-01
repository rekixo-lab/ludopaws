package com.rekixo.ludopaws.feature.store.presentation.components

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.profile.domain.ProfileProgression
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticUnlockKind
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.feature.store.presentation.actions.handleCosmeticTap

internal fun StoreActivity.cosmeticGrid(
    items: List<StoreCosmetic>,
    borderColor: Int,
): LinearLayout =
    LinearLayout(this).apply {
        orientation =
            LinearLayout.VERTICAL

        items.chunked(
            3,
        ).forEachIndexed {
                rowIndex,
                rowItems ->
            val row =
                LinearLayout(
                    this@cosmeticGrid,
                ).apply {
                    orientation =
                        LinearLayout.HORIZONTAL
                    gravity =
                        Gravity.TOP
                }

            rowItems.forEach {
                    cosmetic ->
                row.addView(
                    cosmeticCard(
                        cosmetic =
                            cosmetic,
                        borderColor =
                            borderColor,
                    ),
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    ).apply {
                        setMargins(
                            dp(4),
                            0,
                            dp(4),
                            0,
                        )
                    },
                )
            }

            repeat(
                3 -
                    rowItems.size,
            ) {
                row.addView(
                    TextView(
                        this@cosmeticGrid,
                    ),
                    LinearLayout.LayoutParams(
                        0,
                        1,
                        1f,
                    ),
                )
            }

            addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (
                        rowIndex >
                        0
                    ) {
                        topMargin =
                            dp(12)
                    }
                },
            )
        }
    }

private fun StoreActivity.cosmeticCard(
    cosmetic: StoreCosmetic,
    borderColor: Int,
): LinearLayout =
    LinearLayout(this).apply {
        orientation =
            LinearLayout.VERTICAL
        gravity =
            Gravity.CENTER_HORIZONTAL
        setPadding(
            dp(8),
            dp(10),
            dp(8),
            dp(10),
        )

        val selected =
            cosmetics
                .selectedId(
                    cosmetic.category,
                ) ==
                cosmetic.id
        val owned =
            cosmetics
                .isOwned(
                    cosmetic.id,
                )
        val currentLevel =
            ProfileProgression
                .levelProgress(
                    ProfileStore(
                        this@cosmeticCard,
                    )
                        .snapshot()
                        .totalXp,
                )
                .level

        background =
            storePlywoodCardBackground(
                cornerRadiusDp = 13f,
                fillColors =
                    if (cosmetic.contentAvailable) {
                        intArrayOf(0xC8164FA3.toInt(), 0xC80C397D.toInt())
                    } else {
                        intArrayOf(0xC834445F.toInt(), 0xC8202D43.toInt())
                    },
                strokeColor =
                    if (selected) {
                        0xFF49F073.toInt()
                    } else {
                        borderColor
                    },
                strokeWidthDp = if (selected) 3 else 2,
                insetDp = 5,
            )

        addView(
            TextView(
                this@cosmeticCard,
            ).apply {
                text =
                    cosmetic.title
                textSize =
                    12.5f
                setTypeface(
                    Typeface.DEFAULT_BOLD,
                )
                setTextColor(
                    Color.WHITE,
                )
                gravity =
                    Gravity.CENTER
                maxLines =
                    1
            },
        )

        addView(
            TextView(
                this@cosmeticCard,
            ).apply {
                text =
                    cosmetic.previewSymbol
                textSize =
                    if (
                        cosmetic.previewSymbol.length >
                        2
                    ) {
                        24f
                    } else {
                        38f
                    }
                setTypeface(
                    Typeface.DEFAULT_BOLD,
                )
                setTextColor(
                    Color.WHITE,
                )
                gravity =
                    Gravity.CENTER
                background =
                    storeRecessedSurfaceBackground(
                        cornerRadiusDp = 12f,
                        fillColors = intArrayOf(
                            Color.argb(
                                190,
                                Color.red(cosmetic.previewPrimary),
                                Color.green(cosmetic.previewPrimary),
                                Color.blue(cosmetic.previewPrimary),
                            ),
                            Color.argb(
                                205,
                                Color.red(cosmetic.previewSecondary),
                                Color.green(cosmetic.previewSecondary),
                                Color.blue(cosmetic.previewSecondary),
                            ),
                        ),
                        strokeColor = 0x885FE1FF.toInt(),
                        insetDp = 7,
                    )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(116),
            ).apply {
                topMargin =
                    dp(9)
            },
        )

        addView(
            Button(
                this@cosmeticCard,
            ).apply {
                text =
                    cosmeticActionText(
                        cosmetic =
                            cosmetic,
                        owned =
                            owned,
                        selected =
                            selected,
                    )
                textSize =
                    12f
                setTypeface(
                    Typeface.DEFAULT_BOLD,
                )
                setTextColor(
                    Color.WHITE,
                )
                minWidth =
                    0
                minHeight =
                    0
                setPadding(
                    dp(2),
                    0,
                    dp(2),
                    0,
                )
                background =
                    LudoProofTheme
                        .rounded(
                            when {
                                !cosmetic.contentAvailable ->
                                    0xFF52657E.toInt()
                                selected || owned ->
                                    0xFF08A849.toInt()
                                else ->
                                    0xFFE23434.toInt()
                            },
                            10f,
                            when {
                                !cosmetic.contentAvailable ->
                                    0xFF8195AE.toInt()
                                selected || owned ->
                                    0xFF40E47B.toInt()
                                else ->
                                    0xFFFF6D6D.toInt()
                            },
                            1f,
                            this@cosmeticCard,
                        )
                setOnClickListener {
                    handleCosmeticTap(
                        cosmetic,
                    )
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44),
            ).apply {
                topMargin =
                    dp(9)
            },
        )

        if (!owned && cosmetic.contentAvailable) {
            unlockAlternativeLabel(cosmetic)?.let { alternative ->
                addView(Button(this@cosmeticCard).apply {
                    text = "Or $alternative"
                    isAllCaps = false
                    textSize = 10f
                    setOnClickListener { requestAlternateCosmetic(cosmetic) }
                }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(4) })
            }
        }

        if (
            cosmetic.requiredLevel >
            0
        ) {
            addView(
                TextView(
                    this@cosmeticCard,
                ).apply {
                    text =
                        if (
                            currentLevel >=
                            cosmetic.requiredLevel
                        ) {
                            "Lv " +
                                cosmetic.requiredLevel +
                                " ✓"
                        } else {
                            "🔒 Lv " +
                                cosmetic.requiredLevel
                        }
                    textSize =
                        10f
                    setTypeface(
                        Typeface.DEFAULT_BOLD,
                    )
                    setTextColor(
                        if (
                            currentLevel >=
                            cosmetic.requiredLevel
                        ) {
                            0xFF74F28B.toInt()
                        } else {
                            LudoProofTheme.GOLD
                        },
                    )
                    gravity =
                        Gravity.CENTER
                    setPadding(
                        0,
                        dp(7),
                        0,
                        0,
                    )
                },
            )
        }
    }

private fun StoreActivity.cosmeticActionText(
    cosmetic: StoreCosmetic,
    owned: Boolean,
    selected: Boolean,
): String = when {
    !cosmetic.contentAvailable -> "SOON"
    selected -> "✓"
    owned -> "USE"
    else -> smartUnlockLabel(cosmetic)
}
