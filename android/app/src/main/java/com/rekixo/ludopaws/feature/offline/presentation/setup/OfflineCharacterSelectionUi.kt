package com.rekixo.ludopaws.ui.offline.setup

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.characters.domain.model.AnimalCharacter
import com.rekixo.ludopaws.feature.characters.domain.selection.StarterPawsAssignmentPolicy
import com.rekixo.ludopaws.feature.offline.data.local.OfflineCharacterSetupSnapshot
import com.rekixo.ludopaws.ui.offline.common.*

internal fun OfflineGameActivity.initializeCharacterSetup() {
    val preferred = characterSelectionStore.load()
    val saved =
        offlineCharacterSetupStore.loadSetup(
            mode = gameMode,
            playerCount = selectedPlayers,
            preferredCharacterId = preferred.characterId,
        )
    selectedCharacterSlot = saved.selectedSlot
    selectedCharacterIds = saved.characterIds
    selectedColorsBySeat = saved.colorsBySeat
    if (!isComputerMode) selectedColor = selectedColorsBySeat[selectedCharacterSlot]
}

internal fun OfflineGameActivity.updateSelectedPlayerCount(playerCount: Int) {
    require(gameMode.supportsPlayerCount(playerCount)) {
        "Unsupported local player count: $playerCount"
    }
    selectedPlayers = playerCount
    selectedCharacterSlot =
        if (isComputerMode) {
            0
        } else {
            selectedCharacterSlot.coerceIn(0, playerCount - 1)
        }
    selectedCharacterIds =
        StarterPawsAssignmentPolicy.normalize(
            playerCount = playerCount,
            requestedCharacterIds = selectedCharacterIds,
            preferredCharacterId = characterSelectionStore.load().characterId,
            computerMode = isComputerMode,
        )
    selectedColorsBySeat = OfflinePlayerLayout.normalizeColorsForSeats(
        playerCount, selectedColorsBySeat, selectedColorsBySeat.firstOrNull() ?: "BLUE",
    )
    if (!isComputerMode) selectedColor = selectedColorsBySeat[selectedCharacterSlot]
    persistCharacterSetup()
    rebuildCharacterSlotButtons()
    refreshSetupSelections()
}

internal fun OfflineGameActivity.selectCharacterSlot(slot: Int) {
    if (isComputerMode) {
        selectedCharacterSlot = 0
    } else if (slot in 0 until selectedPlayers) {
        selectedCharacterSlot = slot
    }
    if (!isComputerMode) selectedColor = selectedColorsBySeat[selectedCharacterSlot]
    persistCharacterSetup()
    refreshSetupSelections()
}

internal fun OfflineGameActivity.selectColorForActiveSlot(color: String) {
    if (isComputerMode) {
        selectedColor = color
    } else {
        selectedColorsBySeat = OfflinePlayerLayout.selectColorForSeat(
            playerCount = selectedPlayers,
            currentColors = selectedColorsBySeat,
            selectedSeat = selectedCharacterSlot,
            requestedColor = color,
        )
        selectedColor = selectedColorsBySeat[selectedCharacterSlot]
        persistCharacterSetup()
    }
    refreshSetupSelections()
}

internal fun OfflineGameActivity.selectCharacterForActiveSlot(characterId: String) {
    selectedCharacterIds =
        StarterPawsAssignmentPolicy.select(
            playerCount = selectedPlayers,
            currentCharacterIds = selectedCharacterIds,
            selectedSlot = selectedCharacterSlot,
            requestedCharacterId = characterId,
            computerMode = isComputerMode,
        )

    selectedCharacterIds
        .firstOrNull()
        ?.let { playerOneCharacterId ->
            characterSelectionStore.select(
                packId = LudoPawsCharacterCatalog.STARTER_PACK_ID,
                characterId = playerOneCharacterId,
            )
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
                ),
        )
    selectedCharacterSlot = repaired.selectedSlot
    selectedCharacterIds = repaired.characterIds
    selectedColorsBySeat = repaired.colorsBySeat
}

internal fun OfflineGameActivity.persistActiveCharacterSetup() {
    persistCharacterSetup()
    offlineCharacterSetupStore.saveActive(
        mode = gameMode,
        playerCount = selectedPlayers,
        preferredColor = if (isComputerMode) selectedColor else selectedColorsBySeat.first(),
        characterIds = selectedCharacterIds,
    )
}

internal fun OfflineGameActivity.characterPanel(
    embeddedInCombinedPanel: Boolean = false,
): LinearLayout =
    selectionPanel(
        "CHOOSE YOUR PAW",
        if (isComputerMode) "CPU rivals use the other paws." else "",
        embeddedInCombinedPanel = embeddedInCombinedPanel,
    ).apply {
        if (isComputerMode) {
            // Computer mode needs no YOU/character slot tab; paw selection is direct.
            characterSlotsRow = null
            characterSlotButtons = emptyMap()
        } else {
            characterSlotsRow =
                LinearLayout(this@characterPanel).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }.also(::addView)
            rebuildCharacterSlotButtons()
        }

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
    val visibleSlots =
        if (isComputerMode) {
            listOf(0)
        } else {
            (0 until selectedPlayers).toList()
        }

    visibleSlots.forEach { slot ->
        val button =
            tileButton(
                if (isComputerMode) {
                    "YOU"
                } else {
                    "P${slot + 1}"
                },
            ) {}
        button.setOnClickListener { clicked ->
            clicked.playSelectionPopAnimation()
            selectCharacterSlot(slot)
        }
        slotMap[slot] = button
        slotsRow.addView(
            button,
            if (isComputerMode) {
                LinearLayout.LayoutParams(dp(126), dp(54)).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                }
            } else {
                setupTileParams(
                    if (isCompactSetup()) 52 else 56,
                    marginDp = 4,
                )
            },
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
                ringColor = 0xFF5BE0FF.toInt(),
                showCircularChrome = false,
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
                text =
                    character.personality.name
                        .lowercase()
                        .replaceFirstChar(Char::uppercase)
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
    if (selectedCharacterIds.size != selectedPlayers) {
        selectedCharacterIds =
            StarterPawsAssignmentPolicy.normalize(
                playerCount = selectedPlayers,
                requestedCharacterIds = selectedCharacterIds,
                preferredCharacterId = characterSelectionStore.load().characterId,
                computerMode = isComputerMode,
            )
    }

    val expectedSlots =
        if (isComputerMode) {
            emptySet()
        } else {
            (0 until selectedPlayers).toSet()
        }
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
        button.text =
            when {
                isComputerMode -> "YOU\n$characterName"
                else -> "P${slot + 1}\n$characterName"
            }
        button.setTextColor(if (selected) 0xFFFFD45E.toInt() else Color.WHITE)
        val seatColor = selectedColorsBySeat.getOrNull(slot) ?: "BLUE"
        button.contentDescription = "Player ${slot + 1}, $characterName, $seatColor color"
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
                ringColor = 0xFF5BE0FF.toInt(),
                highlighted = false,
                showCircularChrome = false,
            )
        card.findViewWithTag<TextView>(CHARACTER_NAME_TAG)
            ?.setTextColor(if (selected) 0xFFFFD45E.toInt() else Color.WHITE)
        card.findViewWithTag<TextView>(CHARACTER_PERSONALITY_TAG)?.apply {
            setTextColor(0xFFBCD5FF.toInt())
            background = null
        }
    }


}

private const val CHARACTER_AVATAR_TAG =
    "ludo_paws_character_avatar"
private const val CHARACTER_NAME_TAG =
    "ludo_paws_character_name"
private const val CHARACTER_PERSONALITY_TAG =
    "ludo_paws_character_personality"
