package com.rekixo.ludopaws.feature.store.presentation.components

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.core.ui.art.playerCountSelectionBackground
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.characters.domain.model.AnimalCharacter
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity
import com.rekixo.ludopaws.feature.store.presentation.actions.handleCosmeticTap

internal fun StoreActivity.pawsCatalogContent(friendshipEntry: View? = null): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        // Shipped Starter Paws characters; Lumi and Donkey have individual verified-ad entitlements.
        val starterCharacters =
            LudoPawsCharacterCatalog.charactersForPack(LudoPawsCharacterCatalog.STARTER_PACK_ID)
                .sortedBy { character -> CharacterDefinitions.all.first { it.character.id == character.id }.storeDisplayOrder }
        CharacterRegistry.prewarm(starterCharacters.take(4).map { it.id })
        addView(
            this@pawsCatalogContent.pawsCharacterGrid(starterCharacters, friendshipEntry),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

    }

private fun StoreActivity.pawsCharacterGrid(
    characters: List<AnimalCharacter>,
    friendshipEntry: View? = null,
): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val selectedCharacterId =
            CharacterSelectionStore(this@pawsCharacterGrid).load().characterId
        val ownedIds = cosmetics.ownedCosmeticIds()

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
                        ownedIds = ownedIds,
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
            if (rowIndex == 0 && friendshipEntry != null) {
                addView(friendshipEntry, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                })
            }
        }
    }

private fun StoreActivity.pawsCharacterCard(
    character: AnimalCharacter,
    selected: Boolean,
    ownedIds: Set<String>,
): LinearLayout =
    LinearLayout(this).apply {
        val availableOffer = StoreCosmeticCatalog.characterUnlock(character.id)
        val unlock = availableOffer ?: StoreCosmeticCatalog.findBuiltIn("character_${character.id}")
        val owned = unlock == null || unlock.id in ownedIds
        val offerUnavailable = availableOffer == null && unlock != null && !owned
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
            else if (offerUnavailable) "${character.displayName}, currently unavailable"
            else if (unlock != null) "${character.displayName}, ${smartUnlockLabel(unlock)}"
            else "${character.displayName}, unlocked"

        addView(
            StoreCatalogArtwork.paw(context, character.id),
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

        val paymentOptions = availableOffer?.takeIf { !owned }?.let { cosmeticPaymentOptions(it) }
        addView(
            paymentOptions ?: TextView(this@pawsCharacterCard).apply {
                if (owned) {
                    applyCosmeticOwnershipBadge(equipped = selected)
                    return@apply
                }
                text = when {
                    offerUnavailable -> "🔒 UNAVAILABLE"
                    unlock != null -> cosmeticPurchaseLabel(unlock)
                    else -> "🔒 UNAVAILABLE"
                }
                textSize = 10f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
                letterSpacing = 0.025f
                setPadding(dp(8), dp(5), dp(8), dp(5))
                background = storeButtonBackground(context)
            },
            LinearLayout.LayoutParams(
                if (paymentOptions != null) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        setOnClickListener {
            when {
                owned -> showFeaturedCharacter(character.id)
                availableOffer != null -> handleCosmeticTap(availableOffer)
                else -> showStoreMessage("This Paw is temporarily unavailable.")
            }
        }
        AppFocusRing.apply(this, cornerRadiusDp = 13f)
    }
