package com.rekixo.ludopaws.ui.offline.setup

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.LudoPawsCharacterAvatarView
import com.rekixo.ludopaws.core.ui.art.playerCountSelectionBackground
import com.rekixo.ludopaws.core.ui.components.playSelectionPopAnimation
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.OfflineGameActivity
import com.rekixo.ludopaws.OfflinePlayerLayout
import com.rekixo.ludopaws.LudoPawsTeamIdentityPolicy
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.characters.domain.model.AnimalCharacter
import com.rekixo.ludopaws.feature.characters.domain.selection.StarterPawsAssignmentPolicy
import com.rekixo.ludopaws.feature.offline.domain.engine.RobotTeamComposition
import com.rekixo.ludopaws.feature.offline.data.local.OfflineCharacterSetupSnapshot
import com.rekixo.ludopaws.ui.offline.common.*

internal fun OfflineGameActivity.initializeCharacterSetup() {
    val preferred = characterSelectionStore.load()
    if (isComputerMode) {
        selectedPlayers = offlineCharacterSetupStore.loadSavedPlayerCount(
            mode = gameMode,
            fallback = selectedPlayers,
        )
    }
    val saved =
        offlineCharacterSetupStore.loadSetup(
            mode = gameMode,
            playerCount = selectedPlayers,
            preferredCharacterId = preferred.characterId,
        )
    selectedHumans = saved.humanCount
    selectedCharacterSlot = saved.selectedSlot
    selectedCharacterIds = saved.characterIds
    selectedColorsBySeat = saved.colorsBySeat
    // The robot color selector always controls the human player's seat.
    selectedColor = selectedColorsBySeat[if (isComputerMode) 0 else selectedCharacterSlot]
}

internal fun OfflineGameActivity.updateSelectedPlayerCount(playerCount: Int) {
    require(gameMode.supportsPlayerCount(playerCount)) {
        "Unsupported local player count: $playerCount"
    }
    // A newly selected total begins at 1 Human; re-tapping it cycles ratios.
    // This matches the preview printed on unselected player tiles.
    selectedHumans = if (isComputerMode) {
        RobotTeamComposition.humansAfterPlayerTileTap(
            playerCount = playerCount,
            previouslySelectedPlayerCount = selectedPlayers,
            previouslySelectedHumans = selectedHumans,
        )
    } else playerCount
    selectedPlayers = playerCount
    selectedCharacterSlot = selectedCharacterSlot.coerceIn(0, playerCount - 1)
    selectedCharacterIds =
        StarterPawsAssignmentPolicy.normalize(
            playerCount = playerCount,
            requestedCharacterIds = selectedCharacterIds,
            preferredCharacterId = characterSelectionStore.load().characterId,
            computerMode = isComputerMode,
            unlockedCharacterIds = characterSelectionStore.unlockedCharacterIds(),
            humanCount = selectedHumans,
        )
    selectedColorsBySeat = if (isComputerMode) {
        // Robot seat colors are assigned by the game engine from the human color.
        OfflinePlayerLayout.colorsFor(playerCount, selectedColorsBySeat.firstOrNull() ?: "BLUE")
    } else {
        OfflinePlayerLayout.normalizeColorsForSeats(
            playerCount, selectedColorsBySeat, selectedColorsBySeat.firstOrNull() ?: "BLUE",
        )
    }
    selectedColor = selectedColorsBySeat[if (isComputerMode) 0 else selectedCharacterSlot]
    persistCharacterSetup()
    rebuildCharacterSlotButtons()
    refreshSetupSelections()
}

internal fun OfflineGameActivity.selectCharacterSlot(slot: Int) {
    if (slot !in 0 until selectedPlayers) return
    selectedCharacterSlot = slot
    // The color picker in Robot mode always belongs to the human seat.
    selectedColor = selectedColorsBySeat[if (isComputerMode) 0 else selectedCharacterSlot]
    persistCharacterSetup()
    refreshSetupSelections()
}

internal fun OfflineGameActivity.selectColorForActiveSlot(color: String) {
    // Pass & Play seats own independently chosen colors. In Robot mode only
    // the human chooses a color; CPU colors must mirror the actual match engine.
    selectedColorsBySeat = if (isComputerMode) {
        OfflinePlayerLayout.colorsFor(selectedPlayers, color)
    } else {
        OfflinePlayerLayout.selectColorForSeat(
            playerCount = selectedPlayers,
            currentColors = selectedColorsBySeat,
            selectedSeat = selectedCharacterSlot,
            requestedColor = color,
        )
    }
    selectedColor = selectedColorsBySeat[if (isComputerMode) 0 else selectedCharacterSlot]
    // Save on selection, not just when PLAY is tapped.
    persistCharacterSetup()
    refreshSetupSelections()
}

internal fun OfflineGameActivity.selectCharacterForActiveSlot(characterId: String) {
    // Local Robot opponents are user-selected cosmetics, so they have the
    // same ownership requirement as local human players.
    if (!characterSelectionStore.isUnlocked(characterId)) {
        val store = com.rekixo.ludopaws.feature.store.presentation.StoreActivity::class.java
        val offerId = com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
            .characterUnlock(characterId)?.id
        startActivity(android.content.Intent(this, store).apply {
            putExtra(com.rekixo.ludopaws.feature.store.presentation.StoreActivity.EXTRA_INITIAL_TAB, "PAWS")
            putExtra(com.rekixo.ludopaws.feature.store.presentation.StoreActivity.EXTRA_FEATURED_CHARACTER_ID, characterId)
            if (offerId != null) {
                putExtra(com.rekixo.ludopaws.feature.store.presentation.StoreActivity.EXTRA_PROMOTED_COSMETIC_ID, offerId)
                putExtra(com.rekixo.ludopaws.feature.store.presentation.StoreActivity.EXTRA_START_SMART_UNLOCK, true)
                com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog.find(offerId)?.let { offer ->
                    val action = com.rekixo.ludopaws.feature.store.domain.SmartUnlockPolicy.decide(
                        cosmetic = offer,
                        owned = false,
                        gems = com.rekixo.ludopaws.feature.store.data.local.GemWalletStore(this@selectCharacterForActiveSlot).balance(),
                        adProgress = com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore(this@selectCharacterForActiveSlot).rewardedAdProgress(offer.id),
                        playAvailable = com.rekixo.ludopaws.BuildConfig.LUDOPROOF_BILLING_ENABLED &&
                            com.rekixo.ludopaws.feature.store.domain.StoreCatalog.cosmeticProductId(offer.id) != null,
                        adsAvailable = com.rekixo.ludopaws.BuildConfig.LUDOPROOF_ADS_ENABLED,
                    ).primary
                    putExtra(com.rekixo.ludopaws.feature.store.presentation.StoreActivity.EXTRA_EXPECTED_UNLOCK_ACTION, action.name)
                    if (action == com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction.GEMS) {
                        putExtra(com.rekixo.ludopaws.feature.store.presentation.StoreActivity.EXTRA_EXPECTED_GEMS_PRICE, offer.gemPrice)
                    }
                }
            }
        })
        return
    }
    if (isComputerMode && selectedCharacterSlot >= selectedHumans &&
        selectedCharacterIds.take(selectedHumans).contains(characterId)
    ) {
        android.widget.Toast.makeText(
            this,
            "This paw belongs to a human player. Choose another robot paw.",
            android.widget.Toast.LENGTH_SHORT,
        ).show()
        return
    }
    selectedCharacterIds =
        StarterPawsAssignmentPolicy.select(
            playerCount = selectedPlayers,
            currentCharacterIds = selectedCharacterIds,
            selectedSlot = selectedCharacterSlot,
            requestedCharacterId = characterId,
            computerMode = isComputerMode,
            unlockedCharacterIds = characterSelectionStore.unlockedCharacterIds(),
            humanCount = selectedHumans,
        )

    if (!isComputerMode || selectedCharacterSlot == 0) {
        selectedCharacterIds
            .firstOrNull()
            ?.let { playerOneCharacterId ->
                characterSelectionStore.select(
                    packId = LudoPawsCharacterCatalog.STARTER_PACK_ID,
                    characterId = playerOneCharacterId,
                )
            }
    }

    persistCharacterSetup()
    refreshCharacterSelectionUi()
}

internal fun OfflineGameActivity.persistCharacterSetup() {
    val repaired =
        offlineCharacterSetupStore.saveSetup(
            mode = gameMode,
            snapshot =
                OfflineCharacterSetupSnapshot(
                    playerCount = selectedPlayers,
                    selectedSlot = selectedCharacterSlot,
                    characterIds = selectedCharacterIds,
                    colorsBySeat = selectedColorsBySeat,
                    humanCount = selectedHumans,
                ),
        )
    selectedCharacterSlot = repaired.selectedSlot
    selectedCharacterIds = repaired.characterIds
    selectedColorsBySeat = repaired.colorsBySeat
    selectedColor = selectedColorsBySeat[if (isComputerMode) 0 else selectedCharacterSlot]
}

internal fun OfflineGameActivity.persistActiveCharacterSetup() {
    persistCharacterSetup()
    offlineCharacterSetupStore.saveActive(
        mode = gameMode,
        playerCount = selectedPlayers,
        preferredColor = if (isComputerMode) selectedColor else selectedColorsBySeat.first(),
        characterIds = selectedCharacterIds,
        humanCount = selectedHumans,
    )
}

internal fun OfflineGameActivity.characterPanel(): LinearLayout =
    selectionPanel(
        if (isComputerMode) "CHOOSE YOUR PAWS" else "CHOOSE YOUR PAW",
        "",
    ).apply {
        // Match Pass & Play's seat tabs, including their plywood selection
        // style, while naming computer-controlled opponents CPU 1/2/3.
        characterSlotsRow =
            LinearLayout(this@characterPanel).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }.also(::addView)
        rebuildCharacterSlotButtons()

        val scroll =
            HorizontalScrollView(this@characterPanel).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = false
                overScrollMode = HorizontalScrollView.OVER_SCROLL_NEVER
            }
        val cardsRow =
            LinearLayout(this@characterPanel).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(10), dp(4), dp(4))
            }
        val cardMap = linkedMapOf<String, LinearLayout>()

        LudoPawsCharacterCatalog
            .charactersForPack(LudoPawsCharacterCatalog.STARTER_PACK_ID)
            .forEach { character ->
                val card = characterChoiceCard(character)
                cardMap[character.id] = card
                cardsRow.addView(
                    card,
                    LinearLayout.LayoutParams(
                        dp(if (isCompactSetup()) 100 else 112),
                        dp(if (isCompactSetup()) 140 else 152),
                    ).apply {
                        setMargins(dp(4), 0, dp(4), 0)
                    },
                )
            }
        characterCards = cardMap
        scroll.addView(cardsRow)
        addView(scroll)

        // On both Pass & Play and Computer/Robot entry, briefly reveal the
        // far-right character and smoothly return to the starting position.
        scroll.post {
            val maxScrollX = (cardsRow.width - scroll.width).coerceAtLeast(0)
            if (maxScrollX > 0 && scroll.isAttachedToWindow) {
                scroll.animateCharacterRowTo(maxScrollX) {
                    scroll.postDelayed({
                        if (scroll.isAttachedToWindow) {
                            scroll.animateCharacterRowTo(0)
                        }
                    }, 180L)
                }
            }
        }





        refreshCharacterSelectionUi()
    }

private fun HorizontalScrollView.animateCharacterRowTo(
    targetX: Int,
    onEnd: (() -> Unit)? = null,
) {
    ValueAnimator.ofInt(scrollX, targetX).apply {
        duration = 420L
        interpolator = DecelerateInterpolator()
        addUpdateListener { animator ->
            scrollTo(animator.animatedValue as Int, 0)
        }
        if (onEnd != null) {
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onEnd()
                    }
                },
            )
        }
    }.start()
}

private fun OfflineGameActivity.rebuildCharacterSlotButtons() {
    val slotsRow = characterSlotsRow ?: return
    slotsRow.removeAllViews()

    val slotMap = linkedMapOf<Int, Button>()
    val visibleSlots = (0 until selectedPlayers).toList()

    visibleSlots.forEach { slot ->
        val button =
            tileButton(
                if (isComputerMode) {
                    if (slot == 0) "YOU" else if (slot < selectedHumans) "H${slot + 1}" else "CPU ${slot - selectedHumans + 1}"
                } else {
                    "P${slot + 1}"
                },
            ) {}
        // Long paw names should stay on exactly two readable lines inside
        // the same compact tabs used by Pass & Play.
        button.isAllCaps = false
        button.maxLines = 2
        button.ellipsize = android.text.TextUtils.TruncateAt.END
        button.setAutoSizeTextTypeUniformWithConfiguration(
            8,
            if (isCompactSetup()) 12 else 14,
            1,
            TypedValue.COMPLEX_UNIT_SP,
        )
        button.setOnClickListener { clicked ->
            clicked.playSelectionPopAnimation()
            selectCharacterSlot(slot)
        }
        slotMap[slot] = button
        slotsRow.addView(
            button,
            setupTileParams(
                if (isCompactSetup()) 58 else 62,
                marginDp = 4,
            ),
        )
    }
    characterSlotButtons = slotMap
}

private fun OfflineGameActivity.characterChoiceCard(
    character: AnimalCharacter,
): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        clipChildren = false
        clipToPadding = false
        setPadding(dp(5), dp(7), dp(5), dp(7))

        // Keep the existing production 3D portrait, but show it as a clean
        // cut-out inside the selection card rather than a white circular badge.
        LudoPawsCharacterAvatarView(this@characterChoiceCard).also { avatar ->
            avatar.tag = CHARACTER_AVATAR_TAG
            avatar.bind(
                characterId = character.id,
                ringColor = LudoPawsTeamIdentityPolicy.resolve(
                    selectedColorsBySeat.getOrNull(selectedCharacterSlot) ?: selectedColor,
                )?.argb ?: 0xFF5BE0FF.toInt(),
                modelTeamColor = selectedColorsBySeat.getOrNull(selectedCharacterSlot) ?: selectedColor,
                showCircularChrome = false,
                showTeamBadge = true,
            )
            addView(
                avatar,
                LinearLayout.LayoutParams(
                    dp(if (isCompactSetup()) 70 else 80),
                    dp(if (isCompactSetup()) 70 else 80),
                ),
            )
        }

        addView(
            TextView(this@characterChoiceCard).apply {
                text = character.displayName
                textSize = if (isCompactSetup()) 14f else 15f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setShadowLayer(2f, 0f, dp(1).toFloat(), 0xAA00112D.toInt())
                tag = CHARACTER_NAME_TAG
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(1) },
        )

        addView(
            TextView(this@characterChoiceCard).apply {
                text = characterChoiceSubtitle(character.id)
                textSize = if (isCompactSetup()) 9.5f else 10.5f
                setTextColor(0xFFBCD5FF.toInt())
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(dp(5), dp(4), dp(5), dp(4))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                // Keep the personality subtitle text unboxed over the card surface.
                background = null
                tag = CHARACTER_PERSONALITY_TAG
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(3)
                // Keep the shared subtitle stroke tucked inside the card edges.
                marginStart = dp(4)
                marginEnd = dp(4)
            },
        )

        contentDescription = "Choose ${character.displayName}"
        setOnClickListener { clicked ->
            clicked.playSelectionPopAnimation()
            selectCharacterForActiveSlot(character.id)
        }
    }

internal fun OfflineGameActivity.refreshCharacterSelectionUi() {
    selectedCharacterIds = StarterPawsAssignmentPolicy.normalize(
        playerCount = selectedPlayers,
        requestedCharacterIds = selectedCharacterIds,
        preferredCharacterId = characterSelectionStore.load().characterId,
        computerMode = isComputerMode,
        unlockedCharacterIds = characterSelectionStore.unlockedCharacterIds(),
        humanCount = selectedHumans,
    )

    val expectedSlots = (0 until selectedPlayers).toSet()
    if (
        characterSlotsRow != null &&
        characterSlotButtons.keys != expectedSlots
    ) {
        rebuildCharacterSlotButtons()
    }

    characterSlotButtons.forEach { (slot, button) ->
        val selected = slot == selectedCharacterSlot
        button.background = playerCountSelectionBackground(
            context = this@refreshCharacterSelectionUi,
            selected = selected,
            outerCornerRadiusDp = 12f,
            checkBadgeAtTopEnd = true,
            emphasizeSelected = true,
            showCheckBadge = true,
            checkMarkScale = 2f,
        )
        button.elevation = dp(if (selected) 3 else 1).toFloat()
        button.isSelected = selected
        val characterName =
            selectedCharacterIds
                .getOrNull(slot)
                ?.let(LudoPawsCharacterCatalog::character)
                ?.displayName
                .orEmpty()
        val seatColor = selectedColorsBySeat.getOrNull(slot) ?: "BLUE"
        val seatTint = LudoPawsTeamIdentityPolicy.resolve(seatColor)?.argb
            ?: Color.WHITE
        val slotName = if (isComputerMode) {
            if (slot == 0) "YOU" else if (slot < selectedHumans) "H${slot + 1}" else "CPU ${slot - selectedHumans + 1}"
        } else {
            "P${slot + 1}"
        }
        val label = "● $slotName\n$characterName"
        button.text = SpannableString(label).apply {
            setSpan(
                ForegroundColorSpan(seatTint),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        button.setTextColor(if (selected) 0xFFFFD45E.toInt() else Color.WHITE)
        val accessibleSlot = if (isComputerMode) {
            if (slot == 0) "You" else if (slot < selectedHumans) "Human player ${slot + 1}" else "Robot ${slot - selectedHumans + 1}"
        } else {
            "Player ${slot + 1}"
        }
        button.contentDescription = "$accessibleSlot, $characterName, $seatColor color"
    }

    val activeCharacterId = selectedCharacterIds.getOrNull(selectedCharacterSlot)
    characterCards.forEach { (characterId, card) ->
        val selected = characterId == activeCharacterId
        // Share the Friends/Computer recessed plywood card and gold check treatment.
        card.background = playerCountSelectionBackground(
            context = this@refreshCharacterSelectionUi,
            selected = selected,
            outerCornerRadiusDp = 12f,
            checkBadgeAtTopEnd = true,
            emphasizeSelected = true,
            showCheckBadge = true,
            checkMarkScale = 3f,
        )
        card.elevation = dp(if (selected) 6 else 4).toFloat()
        card.findViewWithTag<LudoPawsCharacterAvatarView>(CHARACTER_AVATAR_TAG)
            ?.bind(
                characterId = characterId,
                ringColor = LudoPawsTeamIdentityPolicy.resolve(
                    selectedColorsBySeat.getOrNull(selectedCharacterSlot) ?: selectedColor,
                )?.argb ?: 0xFF5BE0FF.toInt(),
                highlighted = selected,
                modelTeamColor = selectedColorsBySeat.getOrNull(selectedCharacterSlot) ?: selectedColor,
                showCircularChrome = false,
                showTeamBadge = true,
            )
        card.findViewWithTag<TextView>(CHARACTER_NAME_TAG)
            ?.setTextColor(if (selected) 0xFFFFD45E.toInt() else Color.WHITE)
        card.findViewWithTag<TextView>(CHARACTER_PERSONALITY_TAG)?.apply {
            text = characterChoiceSubtitle(characterId)
            setTextColor(0xFFBCD5FF.toInt())
            background = null
        }
    }


}

private fun OfflineGameActivity.characterChoiceSubtitle(characterId: String): CharSequence {
    if (!characterSelectionStore.isUnlocked(characterId)) {
        return characterUnlockLabel(characterId)
    }
    return LudoPawsCharacterCatalog.character(characterId)
        ?.personality?.name?.lowercase()?.replaceFirstChar(Char::uppercase).orEmpty()
}

private fun OfflineGameActivity.characterUnlockLabel(characterId: String): CharSequence {
    val offer = com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog.characterUnlock(characterId)
        ?: return "🔒 LOCKED"
    val action = com.rekixo.ludopaws.feature.store.domain.SmartUnlockPolicy.decide(
        cosmetic = offer,
        owned = false,
        gems = com.rekixo.ludopaws.feature.store.data.local.GemWalletStore(this).balance(),
        adProgress = com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore(this).rewardedAdProgress(offer.id),
        playAvailable = com.rekixo.ludopaws.BuildConfig.LUDOPROOF_BILLING_ENABLED &&
            com.rekixo.ludopaws.feature.store.domain.StoreCatalog.cosmeticProductId(offer.id) != null,
        adsAvailable = com.rekixo.ludopaws.BuildConfig.LUDOPROOF_ADS_ENABLED,
    ).primary
    return when (action) {
        com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction.GEMS -> "🔒 ${offer.gemPrice} Gems · Unlock"
        com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction.PLAY_PRODUCT -> "🔒 BUY"
        com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction.WATCH_AD -> "🔒 WATCH AD"
        com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction.CLAIM_ADS -> "🔒 UNLOCK"
        com.rekixo.ludopaws.feature.store.domain.SmartUnlockAction.GET_GEMS -> "🔒 GET GEMS"
        else -> "🔒 LOCKED"
    }
}

private const val CHARACTER_AVATAR_TAG =
    "ludo_paws_character_avatar"
private const val CHARACTER_NAME_TAG =
    "ludo_paws_character_name"
private const val CHARACTER_PERSONALITY_TAG =
    "ludo_paws_character_personality"
