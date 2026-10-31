package com.rekixo.ludopaws.feature.store.presentation.components

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.feature.store.domain.StoreCatalog
import com.rekixo.ludopaws.feature.store.domain.model.StoreProduct
import com.rekixo.ludopaws.feature.store.domain.model.StoreTab
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.feature.store.presentation.art.GemClusterView
import com.rekixo.ludopaws.ui.home.HomeIconKind
import com.rekixo.ludopaws.ui.home.HomeIconView

internal fun StoreActivity.pinnedRemoveAdsContent(): LinearLayout {
    val product =
        requireNotNull(
            StoreCatalog.find(StoreCatalog.REMOVE_ADS),
        )
    return featuredRemoveAdsCard(product)
}

internal fun StoreActivity.storeContent(): LinearLayout {
    val catalog = when (selectedTab) {
        StoreTab.GEMS ->
            gemsContent()

        StoreTab.PAWS ->
            pawsCatalogContent()

        StoreTab.BOARD ->
            boardCatalogContent()

        StoreTab.DICE ->
            diceCatalogContent()

    }
    val bundleItem = when (selectedTab) {
        StoreTab.PAWS -> "character_rabbit"
        StoreTab.BOARD -> "board_blossom_garden"
        StoreTab.DICE -> "dice_pearl_paw"
        else -> null
    }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        promotedCosmeticId?.let { com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog.find(it) }
            ?.takeIf { com.rekixo.ludopaws.feature.store.presentation.PromotionInterest.tabFor(it) == selectedTab }
            ?.let { addView(promotedItemCard(it), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }) }
        bundleItem?.let { addView(referralPromotions.storeEntry(it), LinearLayout.LayoutParams(-1, -2)) }
        val category = when (selectedTab) {
            StoreTab.PAWS -> com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory.CHARACTER_PACK
            StoreTab.BOARD -> com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory.BOARD
            StoreTab.DICE -> com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory.DICE
            else -> null
        }
        if (category != null && promotedCosmeticId?.let { com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog.find(it)?.category } != category) {
            val tab = selectedTab
            val initial = spotlightPositions[tab] ?: if (tab == StoreTab.PAWS) featuredCharacterId?.let {
                com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog.characterUnlock(it)?.id
            } else null
            val spotlight = StoreLockedSpotlightView(this@storeContent, category, initial) { spotlightPositions[tab] = it }
            storeSpotlight = spotlight
            addView(spotlight, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            spotlight.setActive(showcaseResumed)
        }
        addView(catalog, LinearLayout.LayoutParams(-1, -2))
    }
}

private fun StoreActivity.gemsContent():
    LinearLayout =
    LinearLayout(this).apply {
        orientation =
            LinearLayout.VERTICAL

        val packs =
            StoreCatalog
                .products
                .filter {
                    !it.featured && it.kind == com.rekixo.ludopaws.feature.store.domain.model.StoreProductKind.CONSUMABLE_GEMS
                }

        packs.chunked(
            2,
        ).forEach {
                rowProducts ->
            val row =
                LinearLayout(
                    this@gemsContent,
                ).apply {
                    orientation =
                        LinearLayout.HORIZONTAL
                    gravity =
                        Gravity.TOP
                }

            rowProducts.forEach {
                    product ->
                row.addView(
                    gemProductCard(
                        product,
                    ),
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    ).apply {
                        setMargins(
                            dp(5),
                            0,
                            dp(5),
                            0,
                        )
                    },
                )
            }

            repeat(
                2 -
                    rowProducts.size,
            ) {
                row.addView(
                    TextView(
                        this@gemsContent,
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
                    topMargin =
                        dp(10)
                },
            )
        }

        if (adsEnabledForBuild || BuildConfig.LUDOPROOF_INTERSTITIAL_ADS_ENABLED) {
            addView(
                TextView(this@gemsContent).apply {
                    text = "Ad privacy options"
                    textSize = 12f
                    setTextColor(0xFFB8D9FF.toInt())
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(10), dp(8), dp(4))
                    isFocusable = true
                    setOnClickListener {
                        showAdPrivacyOptions()
                    }
                    AppFocusRing.apply(this, cornerRadiusDp = 8f)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        addView(
            storeSafetyNote(),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin =
                    dp(16)
            },
        )
    }

private fun StoreActivity.featuredRemoveAdsCard(
    product: StoreProduct,
):
    LinearLayout =
    LinearLayout(this).apply {
        orientation =
            LinearLayout.VERTICAL
        // Keep the wood surface visible without a purple colour overlay.
        background =
            plywoodBackground(
                context = this@featuredRemoveAdsCard,
                cornerRadiusDp = 15f,
            )

        // Keep the icon, centered Remove Ads copy and rewarded action on one compact row.
        val body =
            LinearLayout(
                this@featuredRemoveAdsCard,
            ).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
                setPadding(
                    dp(8),
                    dp(7),
                    dp(8),
                    dp(7),
                )
            }

        body.addView(
            FrameLayout(
                this@featuredRemoveAdsCard,
            ).apply {
                background =
                    plywoodBackground(
                        context = this@featuredRemoveAdsCard,
                        cornerRadiusDp = 30f,
                    )
                elevation = dp(2).toFloat()

                addView(
                    HomeIconView(this@featuredRemoveAdsCard).apply {
                        kind = HomeIconKind.AD_BLOCKER
                        importantForAccessibility =
                            android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    FrameLayout.LayoutParams(
                        dp(52),
                        dp(52),
                        Gravity.CENTER,
                    ),
                )
            },
            LinearLayout.LayoutParams(
                dp(60),
                dp(60),
            ),
        )

        val copy =
            LinearLayout(
                this@featuredRemoveAdsCard,
            ).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(2), 0, dp(2), 0)
            }

        val gemReward =
            LinearLayout(
                this@featuredRemoveAdsCard,
            ).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 0, dp(2), 0)
                contentDescription = "One-time bonus of 5 gems with Remove Ads"
            }
        gemReward.addView(
            TextView(this@featuredRemoveAdsCard).apply {
                text = "+${product.gemAmount}"
                textSize = 14f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(LudoProofTheme.GOLD)
                gravity = Gravity.CENTER
            },
        )
        gemReward.addView(
            GemClusterView(this@featuredRemoveAdsCard).apply {
                contentDescription = "Gems"
            },
            LinearLayout.LayoutParams(
                dp(18),
                dp(18),
            ),
        )
        copy.addView(
            gemReward,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val titleCopy =
            LinearLayout(
                this@featuredRemoveAdsCard,
            ).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), 0, dp(2), 0)
            }
        titleCopy.addView(
            TextView(this@featuredRemoveAdsCard).apply {
                text = "Remove Ads"
                textSize = 15f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
            },
        )
        titleCopy.addView(
            TextView(this@featuredRemoveAdsCard).apply {
                text = "ONE-TIME BONUS"
                textSize = 8f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(0xFFEED9B7.toInt())
                gravity = Gravity.CENTER
                maxLines = 1
                letterSpacing = 0.02f
            },
        )
        copy.addView(
            titleCopy,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = dp(2)
            },
        )

        body.addView(
            copy,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )
        body.addView(
            rewardedAdCard(),
            LinearLayout.LayoutParams(
                dp(122),
                dp(42),
            ).apply {
                marginStart = dp(4)
            },
        )
        addView(
            body,
        )

        addView(
            Button(
                this@featuredRemoveAdsCard,
            ).apply {
                text =
                    priceFor(
                        product.productId,
                        product.fallbackPrice,
                    )
                textSize =
                    18f
                setTypeface(
                    Typeface.DEFAULT_BOLD,
                )
                setTextColor(
                    Color.WHITE,
                )
                minHeight =
                    0
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                            0xFF86E23C.toInt(),
                            0xFF4BAD1D.toInt(),
                        ),
                    ).apply {
                        cornerRadii =
                            floatArrayOf(
                                0f,
                                0f,
                                0f,
                                0f,
                                dp(15).toFloat(),
                                dp(15).toFloat(),
                                dp(15).toFloat(),
                                dp(15).toFloat(),
                            )
                    }
                setOnClickListener {
                    requestPurchase(
                        product.productId,
                    )
                }
                AppFocusRing.apply(this, cornerRadiusDp = 10f)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52),
            ),
        )
    }

internal fun StoreActivity.rewardedAdCard():
    LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = "Watch ad to earn 2 gems"
        setPadding(dp(7), 0, dp(7), 0)
        background =
            LudoProofTheme.rounded(
                0xFFE93434.toInt(),
                9f,
                0xFFFF7373.toInt(),
                1f,
                this@rewardedAdCard,
            )
        setOnClickListener {
            requestRewardedAd()
        }
        AppFocusRing.apply(this, cornerRadiusDp = 9f)

        addView(
            TextView(this@rewardedAdCard).apply {
                text = com.rekixo.ludopaws.feature.store.domain.StoreCatalog.REWARDED_AD_GEMS.toString()
                textSize = 12f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            },
        )
        addView(
            GemClusterView(this@rewardedAdCard).apply {
                contentDescription = "Gems"
            },
            LinearLayout.LayoutParams(
                dp(18),
                dp(18),
            ).apply {
                marginStart = dp(2)
                marginEnd = dp(5)
            },
        )
        addView(
            TextView(this@rewardedAdCard).apply {
                text = "WATCH AD"
                textSize = 10.5f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 1
            },
        )
    }

private fun StoreActivity.gemProductCard(
    product: StoreProduct,
):
    LinearLayout =
    LinearLayout(this).apply {
        orientation =
            LinearLayout.VERTICAL
        gravity =
            Gravity.CENTER
        setPadding(
            dp(8),
            dp(12),
            dp(8),
            dp(10),
        )
        // Show the plywood surface directly; avoid the coloured arcade face overlay.
        background =
            plywoodBackground(
                context = this@gemProductCard,
                cornerRadiusDp = 16f,
            )
        elevation = dp(3).toFloat()

        val badge =
            product.title
                .substringAfter("·", "")
                .trim()
                .takeIf(String::isNotBlank)
                ?: when (product.productId) {
                    StoreCatalog.GEMS_20000 -> "Mega"
                    StoreCatalog.GEMS_40000 -> "Ultra"
                    else -> "Gems"
                }
        addView(
            TextView(this@gemProductCard).apply {
                text = badge.uppercase()
                textSize = 9f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
            },
        )

        addView(
            GemClusterView(
                this@gemProductCard,
                gemCount = gemClusterSize(product.gemAmount),
            ).apply {
                // Keep the gem art on a transparent canvas; no recessed pocket behind it.
                contentDescription = "${product.gemAmount} gems"
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(78),
            ),
        )

        addView(
            TextView(this@gemProductCard).apply {
                text = formatGemAmount(product.gemAmount)
                textSize = 19f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(LudoProofTheme.GOLD)
                gravity = Gravity.CENTER
            },
        )
        addView(
            TextView(this@gemProductCard).apply {
                text = "GEMS"
                textSize = 10f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(0xFFB9D8F7.toInt())
                gravity = Gravity.CENTER
                letterSpacing = 0.12f
                setPadding(0, dp(1), 0, dp(8))
            },
        )

        addView(
            Button(
                this@gemProductCard,
            ).apply {
                text =
                    priceFor(
                        product.productId,
                        product.fallbackPrice,
                    )
                textSize =
                    13f
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
                            0xFF70D72F.toInt(),
                            10f,
                            0xFF9EFF57.toInt(),
                            1f,
                            this@gemProductCard,
                        )
                setOnClickListener {
                    requestPurchase(
                        product.productId,
                    )
                }
                AppFocusRing.apply(this, cornerRadiusDp = 10f)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42),
            ),
        )
    }

private fun StoreActivity.storeSafetyNote():
    TextView =
    TextView(this).apply {
        text =
            "Payments are handled by Google Play; no Google account linking in this game is required. Gems and items are granted after verification. Guest purchases stay with this game account; optional account backup is in Profile."
        textSize =
            10f
        setTextColor(
            0xFFC8D8EE.toInt(),
        )
        gravity =
            Gravity.CENTER
        setPadding(
            dp(14),
            dp(10),
            dp(14),
            dp(10),
        )
    }

private fun StoreActivity.formatGemAmount(
    amount: Int,
): String =
    when {
        amount >=
            1_000 &&
            amount %
                1_000 ==
                0 ->
            (
                amount /
                    1_000
                )
                .toString() +
                "K"

        else ->
            amount
                .toString()
    }

private fun StoreActivity.gemClusterSize(amount: Int): Int =
    when {
        amount >= 20_000 -> 5
        amount >= 3_500 -> 4
        amount >= 1_500 -> 3
        else -> 2
    }
