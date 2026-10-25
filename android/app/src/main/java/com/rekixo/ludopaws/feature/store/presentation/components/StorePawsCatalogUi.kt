package com.rekixo.ludopaws.feature.store.presentation.components

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.BuildConfig
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.core.ui.art.playerCountSelectionBackground
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.core.ui.components.playSelectionPopAnimation
import com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.characters.domain.model.AnimalCharacter
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.feature.store.presentation.actions.handleCosmeticTap

internal fun StoreActivity.pawsCatalogContent(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL

        // Shipped Starter Paws characters; Lumi and Donkey have individual verified-ad entitlements.
        val starterCharacters =
            LudoPawsCharacterCatalog.charactersForPack(LudoPawsCharacterCatalog.STARTER_PACK_ID)
                .sortedBy { character -> CharacterDefinitions.all.first { it.character.id == character.id }.storeDisplayOrder }
        addView(
            this@pawsCatalogContent.pawsCharacterGrid(starterCharacters),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        // Reserved packs stay visible as plywood-only Coming Soon cards; no unlock costs/actions.
        val upcomingPacks =
            StoreCosmeticCatalog.characterPacks.filter {
                it.id != StoreCosmeticCatalog.DEFAULT_CHARACTER_PACK
            }
        if (upcomingPacks.isNotEmpty()) {
            addView(
                TextView(this@pawsCatalogContent).apply {
                    text = "Only complete content-ready packs can be selected. More Paws are coming soon."
                    textSize = 11f
                    setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
                    setTextColor(0xFFD9EEFF.toInt())
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(8)
                },
            )
            addView(
                this@pawsCatalogContent.pawsComingSoonGrid(upcomingPacks),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(12)
                },
            )
        }
    }

private fun StoreActivity.pawsCharacterGrid(
    characters: List<AnimalCharacter>,
): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val selectedCharacterId =
            CharacterSelectionStore(this@pawsCharacterGrid).load().characterId

        characters.chunked(2).forEachIndexed { rowIndex, rowCharacters ->
            val row =
                LinearLayout(this@pawsCharacterGrid).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                }

            rowCharacters.forEach { character ->
                row.addView(
                    pawsCharacterCard(
                        character = character,
                        selected = character.id == selectedCharacterId,
                    ),
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    ).apply {
                        setMargins(dp(4), 0, dp(4), 0)
                    },
                )
            }

            repeat(2 - rowCharacters.size) {
                row.addView(
                    TextView(this@pawsCharacterGrid),
                    LinearLayout.LayoutParams(0, 1, 1f),
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

private fun StoreActivity.pawsCharacterCard(
    character: AnimalCharacter,
    selected: Boolean,
): LinearLayout =
    LinearLayout(this).apply {
        val unlock = StoreCosmeticCatalog.characterUnlock(character.id)
        val owned = unlock == null || cosmetics.isOwned(unlock.id)
        val progress = unlock?.let { cosmetics.rewardedAdProgress(it.id).coerceAtMost(it.requiredAdViews) } ?: 0
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        clipChildren = false
        clipToPadding = false
        setPadding(dp(8), dp(10), dp(8), dp(10))
        background =
            playerCountSelectionBackground(
                context = this@pawsCharacterCard,
                selected = selected,
                outerCornerRadiusDp = 13f,
                checkBadgeAtTopEnd = true,
                emphasizeSelected = true,
                showCheckBadge = true,
                checkMarkScale = 2.7f,
                useRecessedPlywood = false,
            )
        elevation = dp(if (selected) 5 else 3).toFloat()
        isClickable = true
        isFocusable = true
        contentDescription =
            if (owned) "${character.displayName}, unlocked${if (selected) ", selected" else ""}"
            else "${character.displayName}, locked, $progress of ${unlock?.requiredAdViews} rewarded ads"

        addView(
            LudoPawsCharacterAvatarView(this@pawsCharacterCard).apply {
                // Reuse the same production 3D portrait as gameplay and character selection.
                bind(
                    characterId = character.id,
                    ringColor = 0xFF5BE0FF.toInt(),
                    highlighted = false,
                    showCircularChrome = false,
                )
                importantForAccessibility =
                    android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(
                dp(96),
                dp(96),
            ),
        )

        addView(
            TextView(this@pawsCharacterCard).apply {
                text = character.displayName.uppercase()
                textSize = 15f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                setShadowLayer(2.5f, 0f, dp(1).toFloat(), 0xAA3B2414.toInt())
                setPadding(0, dp(6), 0, dp(3))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        addView(
            TextView(this@pawsCharacterCard).apply {
                text = if (owned) "🔓 UNLOCKED" else "🔒 $progress/${unlock?.requiredAdViews} ADS"
                textSize = 10f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(
                    if (selected) 0xFF67430F.toInt() else 0xFF104B29.toInt(),
                )
                gravity = Gravity.CENTER
                includeFontPadding = false
                letterSpacing = 0.025f
                setPadding(dp(8), dp(5), dp(8), dp(5))
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        if (selected) {
                            intArrayOf(0xFFFFF0AD.toInt(), 0xFFFFCB45.toInt())
                        } else {
                            intArrayOf(0xFFBDF5CB.toInt(), 0xFF7CE69A.toInt())
                        },
                    ).apply {
                        cornerRadius = dp(999).toFloat()
                        setStroke(
                            dp(1),
                            if (selected) 0xFFFFF5C9.toInt() else 0xFF47B86C.toInt(),
                        )
                    }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        setOnClickListener { clicked ->
            clicked.playSelectionPopAnimation {
                if (!owned) {
                    handleCosmeticTap(requireNotNull(unlock))
                    return@playSelectionPopAnimation
                }
                CharacterSelectionStore(this@pawsCharacterCard).select(
                    packId = LudoPawsCharacterCatalog.STARTER_PACK_ID,
                    characterId = character.id,
                )
                renderStore()
            }
        }
        AppFocusRing.apply(this, cornerRadiusDp = 13f)
    }

private fun StoreActivity.pawsComingSoonGrid(
    packs: List<StoreCosmetic>,
): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL

        packs.chunked(3).forEachIndexed { rowIndex, rowPacks ->
            val row =
                LinearLayout(this@pawsComingSoonGrid).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                }

            rowPacks.forEach { pack ->
                row.addView(
                    pawsComingSoonCard(pack),
                    LinearLayout.LayoutParams(
                        0,
                        dp(98),
                        1f,
                    ).apply {
                        setMargins(dp(4), 0, dp(4), 0)
                    },
                )
            }

            repeat(3 - rowPacks.size) {
                row.addView(
                    TextView(this@pawsComingSoonGrid),
                    LinearLayout.LayoutParams(0, dp(98), 1f),
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

private fun StoreActivity.pawsComingSoonCard(
    pack: StoreCosmetic,
): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(8), dp(6), dp(8))
        // Keep only the shared plywood; discard the old tinted arcade face and colour preview.
        background =
            plywoodBackground(
                context = this@pawsComingSoonCard,
                cornerRadiusDp = 13f,
            )
        elevation = dp(2).toFloat()
        contentDescription = "${pack.title}, coming soon"

        addView(
            TextView(this@pawsComingSoonCard).apply {
                text = pack.title
                textSize = 10.5f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 2
                setShadowLayer(2f, 0f, dp(1).toFloat(), 0xAA3B2414.toInt())
                setPadding(0, 0, 0, dp(8))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        // Debug builds can preview registered future-pack characters, but the offer
        // remains unavailable so tapping it cannot equip the pack or spend value.
        val previewPack = pack.characterPackId?.let(LudoPawsCharacterCatalog::pack)
        val previewCharacters = previewPack?.let {
            LudoPawsCharacterCatalog.charactersForPack(it.id)
        }.orEmpty()
        if (BuildConfig.DEBUG && previewCharacters.isNotEmpty()) {
            addView(
                LinearLayout(this@pawsComingSoonCard).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    previewCharacters.take(4).forEach { character ->
                        addView(
                            LudoPawsCharacterAvatarView(this@pawsComingSoonCard).apply {
                                bind(
                                    characterId = character.id,
                                    ringColor = pack.previewSecondary,
                                    highlighted = false,
                                    showCircularChrome = true,
                                )
                                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            },
                            LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                                setMargins(dp(1), 0, dp(1), 0)
                            },
                        )
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(30),
                ),
            )
        }

        addView(
            TextView(this@pawsComingSoonCard).apply {
                text = "🔒 LOCKED"
                textSize = 8.5f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(0xFF65421E.toInt())
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                letterSpacing = 0.015f
                setPadding(dp(7), dp(4), dp(7), dp(4))
                background =
                    GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(0xFFE5D2B5.toInt(), 0xFFC4A47B.toInt()),
                    ).apply {
                        cornerRadius = dp(999).toFloat()
                        setStroke(dp(1), 0xFF987044.toInt())
                    }
            },
        )
        addView(
            TextView(this@pawsComingSoonCard).apply {
                text = "COMING SOON"
                textSize = 7.5f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(0xFF65421E.toInt())
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                letterSpacing = 0.02f
                setPadding(0, dp(3), 0, 0)
            },
        )
    }
