package com.rekixo.ludopaws.ui.offline.gameplay

import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.feature.offline.*
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.ui.quickchat.QuickChatButtonView
import com.rekixo.ludopaws.ui.quickchat.showQuickChatPopup
import com.rekixo.ludopaws.ui.offline.common.*
import com.rekixo.ludopaws.ui.offline.setup.*

internal fun OfflineGameActivity.playerRail(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
        minimumHeight =
            dp(
                if (isCompactSetup()) {
                    70
                } else {
                    78
                },
            )
    }

internal fun OfflineGameActivity.renderPlayerRails(
    state: MatchSnapshot,
) {
    val top = topPlayerRail ?: return
    val bottom = bottomPlayerRail ?: return

    val previousDice = diceView
    diceView = null
    diceHost = null

    val presentationBlocked =
        SystemClock.uptimeMillis() < gameplayActionBlockedUntilMillis
    val presentationEvent =
        state.history
            .lastOrNull()
            ?.takeIf {
                presentationBlocked
            }
    val presentationPlayerId = presentationEvent?.playerId
    val activePlayer =
        state.players
            .firstOrNull {
                it.playerId == presentationPlayerId
            }
            ?: state.players.getOrNull(state.turnSeat)

    val quickChatEnabled = GameSettingsStore(this).snapshot().quickChatEnabled
    val structureKey = buildString {
        append(state.matchId).append('|').append(state.status).append('|')
        append(isCompactSetup()).append('|').append(quickChatEnabled)
        state.players.forEach { append('|').append(it.playerId).append(':').append(it.color).append(':').append(it.seat) }
    }
    if (top.tag != structureKey) {
        top.tag = structureKey
        top.removeAllViews()
        bottom.removeAllViews()
        addPlayerSlot(
            top,
            state,
            OfflinePlayerLayout.Slot.TOP_LEFT,
            alignEnd = false,
            activePlayer = activePlayer,
        )
        addPlayerSlot(
            top,
            state,
            OfflinePlayerLayout.Slot.TOP_RIGHT,
            alignEnd = true,
            activePlayer = activePlayer,
        )
        addPlayerSlot(
            bottom,
            state,
            OfflinePlayerLayout.Slot.BOTTOM_LEFT,
            alignEnd = false,
            activePlayer = activePlayer,
        )
        addPlayerSlot(
            bottom,
            state,
            OfflinePlayerLayout.Slot.BOTTOM_RIGHT,
            alignEnd = true,
            activePlayer = activePlayer,
        )
    }
    state.players.forEach { player ->
        val active = player.playerId == activePlayer?.playerId && state.status == "ACTIVE"
        val slot = OfflinePlayerLayout.slotForColor(player.color, state.players.firstOrNull()?.color ?: "BLUE")
        playerCardViewsBySeat[player.seat]?.bind(
            player = player,
            characterId = activeCharacterIdsBySeat.getOrNull(player.seat),
            active = active,
            computer = engine.isComputerPlayer(player.playerId),
            compact = isCompactSetup(),
            portraitOnEnd = slot == OfflinePlayerLayout.Slot.TOP_RIGHT || slot == OfflinePlayerLayout.Slot.BOTTOM_RIGHT,
        )
        val control = top.findViewWithTag<FrameLayout>("dice:${player.playerId}")
            ?: bottom.findViewWithTag<FrameLayout>("dice:${player.playerId}")
        control?.visibility = if (active) View.VISIBLE else View.GONE
        if (active && control != null) {
            diceHost = control
            diceView = control.getChildAt(0) as DiceView
            control.isEnabled = !engine.isComputerPlayer(player.playerId) && !presentationBlocked
        }
    }

    if (previousDice !== diceView) previousDice?.stopRolling()

    // A committed move advances the authoritative turn immediately, but the
    // previous player's resolved dice face still belongs to the animation that
    // is on screen. Keep that face visible and non-interactive until the pawn
    // has fully finished moving/capturing; the next player's dice is rendered
    // only after the presentation block releases.
    val presentationOutcome =
        presentationEvent?.effectiveOutcome
            ?: presentationEvent?.outcome
    if (presentationOutcome != null) {
        diceView?.showOutcome(
            outcome = presentationOutcome,
            animate = false,
        )
    }
}

private fun OfflineGameActivity.addPlayerSlot(
    rail: LinearLayout,
    state: MatchSnapshot,
    slot: OfflinePlayerLayout.Slot,
    alignEnd: Boolean,
    activePlayer: PlayerSnapshot?,
) {
    val player =
        state.players
            .firstOrNull {
                OfflinePlayerLayout
                    .slotForColor(
                        color = it.color,
                        preferredBottomLeftColor =
                            state.players
                                .firstOrNull()
                                ?.color
                                ?: "BLUE",
                    ) == slot
            }

    val host =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity =
                (
                    if (alignEnd) {
                        Gravity.END
                    } else {
                        Gravity.START
                    }
                    ) or Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }

    if (player != null) {
        val active =
            player.playerId == activePlayer?.playerId &&
                state.status == "ACTIVE"
        val characterId =
            activeCharacterIdsBySeat
                .getOrNull(player.seat)
        val playerWidth =
            dp(
                if (isCompactSetup()) {
                    104
                } else {
                    116
                },
            )
        val computerPlayer = engine.isComputerPlayer(player.playerId)
        val quickChatEnabled =
            GameSettingsStore(this).snapshot().quickChatEnabled
        val hasComputerOpponent =
            state.players.any { engine.isComputerPlayer(it.playerId) }
        val humanPlayerCount =
            state.players.count { !engine.isComputerPlayer(it.playerId) }
        val preferredBottomLeftColor =
            state.players.firstOrNull()?.color ?: "BLUE"
        val humanPlayer =
            state.players.firstOrNull { !engine.isComputerPlayer(it.playerId) }
        val humanSlot =
            humanPlayer?.let {
                OfflinePlayerLayout.slotForColor(
                    color = it.color,
                    preferredBottomLeftColor = preferredBottomLeftColor,
                )
            }
        val slotIsBottom =
            slot == OfflinePlayerLayout.Slot.BOTTOM_LEFT ||
                slot == OfflinePlayerLayout.Slot.BOTTOM_RIGHT
        val humanIsBottom =
            humanSlot == OfflinePlayerLayout.Slot.BOTTOM_LEFT ||
                humanSlot == OfflinePlayerLayout.Slot.BOTTOM_RIGHT
        // Always reserve the same footer on the human's row, even when Quick Chat
        // is disabled. Visibility alone must not remeasure or shift the cards.
        val reserveProfileFooter =
            isComputerMode &&
                hasComputerOpponent &&
                humanPlayerCount == 1 &&
                slotIsBottom == humanIsBottom
        val showQuickChat =
            reserveProfileFooter &&
                quickChatEnabled &&
                !computerPlayer

        val activeDiceView = activeDiceControl(player).apply {
            tag = "dice:${player.playerId}"
            visibility = if (active) View.VISIBLE else View.GONE
            if (reserveProfileFooter) translationY = -dp(15).toFloat()
        }
        if (alignEnd) host.addView(activeDiceView)

        // Preserve the same portrait instance across turns, dice rolls and
        // quick-chat rebinds. Recreating it briefly flashes vector fallback art.
        val playerCard =
            playerCardViewsBySeat.getOrPut(player.seat) {
                LudoPawsPlayerCardView(this)
            }
        (playerCard.parent as? ViewGroup)?.removeView(playerCard)
        playerCard.bind(
            player = player,
            characterId = characterId,
            active = active,
            computer = computerPlayer,
            compact = isCompactSetup(),
            portraitOnEnd = alignEnd,
        )
        val quickChatButton =
            QuickChatButtonView(this).apply {
                visibility = if (showQuickChat) View.VISIBLE else View.GONE
                isEnabled = showQuickChat && state.status == "ACTIVE"
                alpha = if (isEnabled) 1f else .58f
                contentDescription = "Quick Chat for ${player.displayName}"
                setOnClickListener {
                    if (
                        state.status != "ACTIVE" ||
                        !showQuickChat ||
                        computerPlayer ||
                        !GameSettingsStore(this@addPlayerSlot).snapshot().quickChatEnabled
                    ) {
                        return@setOnClickListener
                    }
                    showQuickChatPopup(this) { emoji ->
                        presentQuickReaction(
                            emoji = emoji,
                            senderPlayerId = player.playerId,
                        )
                    }
                }
                AppFocusRing.apply(
                    this,
                    cornerRadiusDp = 999f,
                    strokeWidthDp = 2f,
                )
            }
        val profileColumn =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false
                clipToPadding = false
                setPadding(
                    0,
                    if (reserveProfileFooter) dp(9) else 0,
                    0,
                    0,
                )
                addView(
                    playerCard,
                    LinearLayout.LayoutParams(
                        playerWidth,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
                if (reserveProfileFooter) {
                    // Fixed footer height even with chat disabled or on the CPU side.
                    // The control is GONE, but its parent slot keeps its measured height.
                    val chatSlot =
                        FrameLayout(this@addPlayerSlot).apply {
                            clipChildren = false
                            clipToPadding = false
                            addView(
                                quickChatButton,
                                FrameLayout.LayoutParams(
                                    dp(36),
                                    dp(36),
                                    Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                                ),
                            )
                        }
                    addView(
                        chatSlot,
                        LinearLayout.LayoutParams(
                            playerWidth,
                            dp(37),
                        ).apply {
                            topMargin = dp(1)
                        },
                    )
                }
            }
        host.addView(
            profileColumn,
            LinearLayout.LayoutParams(
                playerWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        if (!alignEnd) {
            host.addView(activeDiceView)
        }
    }

    rail.addView(
        host,
        LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f,
        ),
    )
}

private fun OfflineGameActivity.activeDiceControl(
    player: PlayerSnapshot,
): FrameLayout {
    val control =
        FrameLayout(this).apply {
            isClickable = true
            isFocusable = true
            // The dice itself is the visual control. Keep this host transparent so
            // no extra frame surrounds or clips the face/shadow.
            background = null
            elevation = 0f
            clipChildren = false
            clipToPadding = false
            setOnClickListener {
                rollOffline()
            }

            val newDice =
                DiceView(
                    this@activeDiceControl,
                ).apply {
                    setPlayerTeamColor(player.color)
                }
            addView(
                newDice,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )
        }

    val cpuTurn =
        engine.isComputerPlayer(
            player.playerId,
        )
    control.contentDescription =
        if (cpuTurn) {
            player.displayName + " is thinking"
        } else {
            "Roll dice for " + player.displayName
        }
    control.isEnabled =
        !cpuTurn &&
            SystemClock.uptimeMillis() >= gameplayActionBlockedUntilMillis
    control.alpha = 1f
    control.layoutParams =
        LinearLayout.LayoutParams(
            dp(
                if (isCompactSetup()) {
                    54
                } else {
                    58
                },
            ),
            dp(
                if (isCompactSetup()) {
                    54
                } else {
                    58
                },
            ),
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
            setMargins(dp(1), 0, dp(1), 0)
        }

    return control
}
