package com.rekixo.ludopaws.feature.online

import com.rekixo.ludopaws.BuildConfig
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.LudoPawsPlayerCardView
import com.rekixo.ludopaws.DiceTurnCountdownPolicy
import com.rekixo.ludopaws.DiceView
import com.rekixo.ludopaws.LudoPawsReactiveBoardView
import com.rekixo.ludopaws.MainActivity
import com.rekixo.ludopaws.GameMode
import com.rekixo.ludopaws.MatchSnapshot
import com.rekixo.ludopaws.OfflinePlayerLayout
import com.rekixo.ludopaws.PlayerSnapshot
import com.rekixo.ludopaws.renderFirstMatchCoach
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsFeedbackLedger
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsReactionEngine
import com.rekixo.ludopaws.feature.online.domain.OnlineLudoPawsCharacterPolicy
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.feature.settings.data.local.GameSoundFeedback
import com.rekixo.ludopaws.feature.settings.data.local.LudoPawsHaptics
import com.rekixo.ludopaws.ui.online.button
import com.rekixo.ludopaws.ui.quickchat.QuickChatButtonView
import com.rekixo.ludopaws.ui.quickchat.animateQuickChatReaction
import com.rekixo.ludopaws.ui.quickchat.showQuickChatPopup
import kotlin.math.roundToInt

/** Presentation bridge for the shared online Ludo Paws board. */
internal object OnlineLudoPawsPresentation {
    private data class Host(
        val board: LudoPawsReactiveBoardView,
        val topRail: LinearLayout,
        val bottomRail: LinearLayout,
        val voiceControls: LinearLayout,
        val voiceStatus: TextView,
        val voiceToggle: Button,
        val micToggle: Button,
        val feedbackLedger: LudoPawsFeedbackLedger = LudoPawsFeedbackLedger(),
        var quickChatReactionView: View? = null,
        var lastQuickChatAtMs: Long = 0L,
        var railBindingKey: String? = null,
        var activeDiceControl: FrameLayout? = null,
        var activeDicePlayerId: String? = null,
        var railAlignmentListener: ViewTreeObserver.OnGlobalLayoutListener? = null,
        var railDiagnosticKey: String? = null,
        val topProfileCards: MutableList<View> = mutableListOf(),
        val bottomProfileCards: MutableList<View> = mutableListOf(),
        val playerCardsById: MutableMap<String, LudoPawsPlayerCardView> = mutableMapOf(),
        var activeDiceProfileCard: View? = null,
    )

    fun render(
        activity: MainActivity,
        previous: MatchSnapshot?,
        current: MatchSnapshot,
    ) {
        val host =
            host(activity)
                ?: createHost(activity).also {
                    activity.boardFrame.tag = it
                }
        activity.gameplayPawsPromotion?.apply {
            protect(listOf(host.topRail, host.board, host.bottomRail))
            setMatchActive(current.status == "ACTIVE")
            quietFor((activity.onlineGameplayActionBlockedUntilMillis - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0L))
        }

        ensureRailAlignmentObserver(activity, host)
        val characterIdsBySeat =
            OnlineLudoPawsCharacterPolicy.characterIdsBySeat(current)
        val localColor =
            current.players.firstOrNull {
                it.playerId == activity.playerId
            }?.color

        activity.boardView.visibility = View.GONE
        host.board.visibility = View.VISIBLE
        host.board.bind(
            state = current,
            playerId = activity.playerId.takeIf { activity.canSubmitOnlineGameplayAction() },
            perspectiveColor = localColor,
            characterIdsBySeat = characterIdsBySeat,
        )
        renderRails(
            activity = activity,
            host = host,
            state = current,
            characterIdsBySeat = characterIdsBySeat,
            perspectiveColor = localColor,
        )
        refreshVoiceControls(activity, current)
        activity.renderFirstMatchCoach(current)
        dispatchFeedback(
            activity = activity,
            host = host,
            previous = previous,
            current = current,
            characterIdsBySeat = characterIdsBySeat,
        )
    }

    /**
     * Marks the input-time sound so the eventual authoritative snapshot cannot
     * replay it on the same device.
     */
    fun noteLocalRollStarted(
        activity: MainActivity,
        matchId: String,
        eventIndex: Int,
    ) {
        val host = host(activity) ?: return
        host.feedbackLedger.once(
            matchId = matchId,
            key = "ROLL_START:${eventIndex}",
        )
    }

    fun clear(activity: MainActivity) {
        activity.gameplayPawsPromotion?.setMatchActive(false)
        val host = host(activity) ?: return
        com.rekixo.ludopaws.ui.quickchat.clearQuickChatReactions(host.board)
        host.quickChatReactionView = null
        host.railAlignmentListener?.let { listener ->
            runCatching {
                activity.arcadeRootView.viewTreeObserver.let { observer ->
                    if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
                }
            }
        }
        host.railAlignmentListener = null
        host.railDiagnosticKey = null
        host.topProfileCards.clear()
        host.bottomProfileCards.clear()
        host.activeDiceProfileCard = null
        host.feedbackLedger.clear()
        host.railBindingKey = null
        host.playerCardsById.clear()
        host.activeDiceControl = null
        host.activeDicePlayerId = null
        host.activeDiceProfileCard = null
        host.topProfileCards.clear()
        host.bottomProfileCards.clear()
        host.board.bind(
            state = null,
            playerId = null,
            characterIdsBySeat = emptyList(),
        )
        host.board.visibility = View.GONE
        host.topRail.visibility = View.GONE
        host.bottomRail.visibility = View.GONE
        host.voiceControls.visibility = View.GONE
        runCatching {
            activity.boardFrame.removeView(host.board)
            activity.arcadeRootView.removeView(host.topRail)
            activity.arcadeRootView.removeView(host.bottomRail)
        }
        activity.boardFrame.tag = null
        activity.boardView.visibility = View.VISIBLE
    }

    fun refreshActionControls(
        activity: MainActivity,
        state: MatchSnapshot,
    ) {
        val host = host(activity) ?: return
        val characterIdsBySeat = OnlineLudoPawsCharacterPolicy.characterIdsBySeat(state)
        val localColor = state.players.firstOrNull {
            it.playerId == activity.playerId
        }?.color
        host.board.bind(
            state = state,
            playerId = activity.playerId.takeIf { activity.canSubmitOnlineGameplayAction() },
            perspectiveColor = localColor,
            characterIdsBySeat = characterIdsBySeat,
        )
        renderRails(
            activity = activity,
            host = host,
            state = state,
            characterIdsBySeat = characterIdsBySeat,
            perspectiveColor = localColor,
        )
    }

    fun beginPendingMovePreview(
        activity: MainActivity,
        state: MatchSnapshot,
        tokenIndex: Int,
    ): Boolean {
        val playerId = activity.playerId ?: return false
        val boardHost = host(activity) ?: return false
        return boardHost.board.beginPendingMovePreview(
            state = state,
            playerId = playerId,
            tokenIndex = tokenIndex,
        )
    }

    fun cancelPendingMovePreview(
        activity: MainActivity,
        authoritative: MatchSnapshot?,
    ) {
        host(activity)?.board?.cancelPendingMovePreview(authoritative)
    }

    fun presentRemoteQuickChat(
        activity: MainActivity,
        senderPlayerId: String,
        displayName: String,
        emoji: String,
    ) {
        if (senderPlayerId == activity.playerId) return
        if (emoji !in com.rekixo.ludopaws.ui.quickchat.QuickChatEmojiCatalog.EMOJIS) return
        if (!GameSettingsStore(activity).snapshot().quickChatEnabled) return
        val state = activity.currentState ?: return
        if (state.status != "ACTIVE") return
        if (state.players.none { it.playerId == senderPlayerId }) return
        val host = host(activity) ?: return
        host.quickChatReactionView =
            animateQuickChatReaction(
                parent = host.board,
                emoji = emoji,
                senderPlayerId = senderPlayerId,
                anchor = host.playerCardsById[senderPlayerId] ?: return,
            )
    }

    private fun host(activity: MainActivity): Host? =
        activity.boardFrame.tag as? Host

    private fun createHost(activity: MainActivity): Host {
        val reactiveBoard =
            LudoPawsReactiveBoardView(activity).apply {
                onTokenSelected = { tokenIndex ->
                    activity.moveToken(tokenIndex)
                }
            }
        activity.boardFrame.addView(
            reactiveBoard,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )

        // Player rails must live in the screen-level overlay, not inside the
        // small matchStatusPanel/actionPanel containers. Alignment intentionally
        // moves them across the content column, and those original parents clip
        // children once the translation carries a rail outside the panel bounds.
        val topRail = playerRail(activity)
        val bottomRail = playerRail(activity)
        activity.arcadeRootView.clipChildren = false
        activity.arcadeRootView.clipToPadding = false
        activity.arcadeRootView.addView(
            topRail,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )
        activity.arcadeRootView.addView(
            bottomRail,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )
        topRail.bringToFront()
        bottomRail.bringToFront()

        val voiceStatus =
            TextView(activity).apply {
                textSize = 10f
                setTextColor(0xFFEAF7FF.toInt())
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
            }
        val voiceToggle =
            activity.button("VOICE ON") {
                activity.toggleFriendsVoice()
            }.apply {
                textSize = 10f
                setPadding(dp(activity, 7), 0, dp(activity, 7), 0)
                minHeight = dp(activity, 34)
            }
        val micToggle =
            activity.button("MIC ON") {
                activity.toggleFriendsMic()
            }.apply {
                textSize = 10f
                setPadding(dp(activity, 7), 0, dp(activity, 7), 0)
                minHeight = dp(activity, 34)
            }
        val voiceControls =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(activity, 5), dp(activity, 2), dp(activity, 5), dp(activity, 2))
                addView(
                    voiceStatus,
                    LinearLayout.LayoutParams(0, dp(activity, 38), 1f),
                )
                addView(
                    voiceToggle,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(activity, 38),
                    ).apply { marginStart = dp(activity, 4) },
                )
                addView(
                    micToggle,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(activity, 38),
                    ).apply { marginStart = dp(activity, 4) },
                )
                visibility = View.GONE
            }
        activity.matchStatusPanel.addView(
            voiceControls,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        return Host(
            board = reactiveBoard,
            topRail = topRail,
            bottomRail = bottomRail,
            voiceControls = voiceControls,
            voiceStatus = voiceStatus,
            voiceToggle = voiceToggle,
            micToggle = micToggle,
        )
    }

    fun refreshVoiceControls(activity: MainActivity, state: MatchSnapshot) {
        val host = host(activity) ?: return
        val visible = !BuildConfig.LUDOPAWS_FAMILY_MODE && activity.gameMode == GameMode.FRIENDS &&
            state.matchMode == "FRIENDS" &&
            state.status == "ACTIVE"
        host.voiceControls.visibility = if (visible) View.VISIBLE else View.GONE
        host.voiceStatus.text = activity.friendsVoiceStatus
        host.voiceToggle.text = if (activity.friendsVoiceEnabled) "VOICE OFF" else "VOICE ON"
        host.micToggle.text = if (activity.friendsMicEnabled) "MIC ON" else "MIC MUTED"
        host.micToggle.isEnabled = activity.friendsVoiceEnabled
        host.micToggle.alpha = if (activity.friendsVoiceEnabled) 1f else .55f
    }

    private fun playerRail(activity: MainActivity): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            minimumHeight = dp(activity, 62)
        }

    private fun renderRails(
        activity: MainActivity,
        host: Host,
        state: MatchSnapshot,
        characterIdsBySeat: List<String>,
        perspectiveColor: String?,
    ) {
        val activePlayer =
            state.players
                .getOrNull(state.actingSeat ?: state.turnSeat)
        val preferredBottomLeftColor =
            perspectiveColor
                ?: state.players.firstOrNull()?.color
                ?: "BLUE"
        val quickChatEnabled = GameSettingsStore(activity).snapshot().quickChatEnabled
        val bindingKey = buildString {
            append(state.matchId).append('|')
            append(state.matchMode).append('|')
            append(state.status).append('|')
            append(activity.playerId).append('|')
            append(perspectiveColor).append('|')
            append(quickChatEnabled)
            characterIdsBySeat.forEach { append('|').append(it) }
            state.players.forEach { player ->
                append('|').append(player.playerId)
                append(':').append(player.displayName)
                append(':').append(player.color)
                append(':').append(player.teamId)
                append(':').append(player.playerType)
                append(':').append(player.seat)
            }
        }

        fun bindRailState() {
            val previousDice = activity.diceView
            host.activeDiceControl = null
            host.activeDicePlayerId = null
            host.activeDiceProfileCard = null
            state.players.forEach { player ->
                val card = host.playerCardsById[player.playerId] ?: return@forEach
                val active = state.status == "ACTIVE" && player.playerId == activePlayer?.playerId
                card.bind(
                    player = player,
                    characterId = characterIdsBySeat.getOrNull(player.seat),
                    active = active,
                    computer = player.playerType == "BOT",
                    compact = true,
                    localPlayer = player.playerId == activity.playerId,
                    portraitOnEnd = card.layoutDirection == View.LAYOUT_DIRECTION_RTL,
                )
                val control = host.topRail.findViewWithTag<FrameLayout>("dice:${player.playerId}")
                    ?: host.bottomRail.findViewWithTag<FrameLayout>("dice:${player.playerId}")
                control?.visibility = if (active) View.VISIBLE else View.GONE
                if (active && control != null) {
                    host.activeDiceControl = control
                    host.activeDicePlayerId = player.playerId
                    host.activeDiceProfileCard = card
                    activity.diceView = control.getChildAt(0) as DiceView
                    val enabled = player.playerId == activity.playerId &&
                        activity.canSubmitOnlineGameplayAction() && activity.onlineRollActionDecision(state).enabled
                    control.isEnabled = enabled
                    control.isClickable = enabled
                    control.isFocusable = enabled
                    activity.diceView.setAttentionEnabled(enabled)
                }
            }
            if (previousDice !== activity.diceView || host.activeDiceControl == null) {
                previousDice.setTurnDeadlineAt(null)
                previousDice.stopRolling()
            }
        }

        if (host.railBindingKey == bindingKey) {
            bindRailState()
            alignPlayerRailsToBoard(activity, host)
            bindTurnCountdown(activity, state)
            return
        }

        host.railBindingKey = bindingKey
        host.activeDiceControl = null
        host.activeDicePlayerId = null
        host.activeDiceProfileCard = null
        host.topProfileCards.clear()
        host.bottomProfileCards.clear()
        host.topRail.visibility = View.VISIBLE
        host.bottomRail.visibility = View.VISIBLE
        host.topRail.removeAllViews()
        host.bottomRail.removeAllViews()

        addPlayerSlot(
            activity = activity,
            quickChatHost = host,
            rail = host.topRail,
            state = state,
            slot = OfflinePlayerLayout.Slot.TOP_LEFT,
            alignEnd = false,
            activePlayer = activePlayer,
            preferredBottomLeftColor = preferredBottomLeftColor,
            characterIdsBySeat = characterIdsBySeat,
        )
        addPlayerSlot(
            activity = activity,
            quickChatHost = host,
            rail = host.topRail,
            state = state,
            slot = OfflinePlayerLayout.Slot.TOP_RIGHT,
            alignEnd = true,
            activePlayer = activePlayer,
            preferredBottomLeftColor = preferredBottomLeftColor,
            characterIdsBySeat = characterIdsBySeat,
        )
        addPlayerSlot(
            activity = activity,
            quickChatHost = host,
            rail = host.bottomRail,
            state = state,
            slot = OfflinePlayerLayout.Slot.BOTTOM_LEFT,
            alignEnd = false,
            activePlayer = activePlayer,
            preferredBottomLeftColor = preferredBottomLeftColor,
            characterIdsBySeat = characterIdsBySeat,
        )
        addPlayerSlot(
            activity = activity,
            quickChatHost = host,
            rail = host.bottomRail,
            state = state,
            slot = OfflinePlayerLayout.Slot.BOTTOM_RIGHT,
            alignEnd = true,
            activePlayer = activePlayer,
            preferredBottomLeftColor = preferredBottomLeftColor,
            characterIdsBySeat = characterIdsBySeat,
        )
        bindRailState()
        alignPlayerRailsToBoard(activity, host)
        bindTurnCountdown(activity, state)
    }

    /**
     * The match panels are laid out before and after the board in the scrolling page.
     * On tall phones this leaves a large visual gap, even though the board itself is
     * correctly positioned. Move only the player rails toward the board; never resize
     * or reposition the board view.
     */
    private fun ensureRailAlignmentObserver(activity: MainActivity, host: Host) {
        if (host.railAlignmentListener != null) return
        val listener =
            ViewTreeObserver.OnGlobalLayoutListener {
                // render() runs before updateControls() switches the active match
                // into its full-viewport layout. Retry after each completed layout
                // until the board and both rails have usable measured bounds.
                alignPlayerRailsToBoard(activity, host)
            }
        host.railAlignmentListener = listener
        activity.arcadeRootView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        Log.d(TAG, "alignment observer installed")
    }

    /**
     * Align from final screen bounds once the active layout has completed.
     * Translation is absolute and repeatable; the board itself is never moved.
     */
    /**
     * Align visible profile-card/dice bounds to the actual square board.
     * The boardFrame is a flexible viewport slot on tall phones, not the visible
     * board bounds, and player rails reserve a quick-chat row outside the card.
     */
    private fun alignPlayerRailsToBoard(activity: MainActivity, host: Host) {
        val boardFrame = activity.boardFrame
        activity.matchStatusPanel.clipChildren = false
        activity.matchStatusPanel.clipToPadding = false
        activity.actionPanel.clipChildren = false
        activity.actionPanel.clipToPadding = false

        alignDiceWithProfileCard(host)

        val boardBounds = host.board.visibleBoardBoundsOnScreen()
        val topContentBounds =
            visiblePlayerContentBounds(
                rail = host.topRail,
                profileCards = host.topProfileCards,
                diceControl = host.activeDiceControl,
            )
        val bottomContentBounds =
            visiblePlayerContentBounds(
                rail = host.bottomRail,
                profileCards = host.bottomProfileCards,
                diceControl = host.activeDiceControl,
            )

        if (
            boardFrame.visibility != View.VISIBLE ||
            boardBounds == null ||
            host.topRail.visibility != View.VISIBLE ||
            host.bottomRail.visibility != View.VISIBLE ||
            topContentBounds == null ||
            bottomContentBounds == null
        ) {
            val frameLocation = IntArray(2)
            boardFrame.getLocationOnScreen(frameLocation)
            val deferredKey =
                "deferred:frame=" + boardFrame.width + "x" + boardFrame.height +
                    "@y" + frameLocation[1] +
                    ",square=" + (boardBounds?.toShortString() ?: "unmeasured") +
                    ",topContent=" + (topContentBounds?.toString() ?: "unmeasured") +
                    ",bottomContent=" + (bottomContentBounds?.toString() ?: "unmeasured")
            if (host.railDiagnosticKey != deferredKey) {
                host.railDiagnosticKey = deferredKey
                Log.w(TAG, "alignment deferred: " + deferredKey)
            }
            return
        }

        val frameLocation = IntArray(2)
        boardFrame.getLocationOnScreen(frameLocation)
        val gap = dp(activity, 8)
        // Child locations include the rail translation. Remove it so the policy
        // returns absolute values and repeated relayouts cannot accumulate drift.
        val topContentBaseTop =
            topContentBounds.first - host.topRail.translationY.roundToInt()
        val bottomContentBaseTop =
            bottomContentBounds.first - host.bottomRail.translationY.roundToInt()
        val topContentHeight = topContentBounds.second - topContentBounds.first
        val translation = OnlineBoardRailAlignmentPolicy.calculate(
            boardTopOnScreen = boardBounds.top,
            boardHeight = boardBounds.height(),
            topAnchorTopOnScreen = topContentBaseTop,
            topAnchorHeight = topContentHeight,
            bottomAnchorTopOnScreen = bottomContentBaseTop,
            gapPx = gap,
        )

        val diagnosticKey =
            "frame=" + boardFrame.width + "x" + boardFrame.height +
                "@y" + frameLocation[1] +
                ",square=" + boardBounds.toShortString() +
                ",topContent=" + topContentBounds +
                ",topBaseY=" + topContentBaseTop +
                ",topTarget=" + translation.top +
                ",bottomContent=" + bottomContentBounds +
                ",bottomBaseY=" + bottomContentBaseTop +
                ",bottomTarget=" + translation.bottom
        if (host.railDiagnosticKey != diagnosticKey) {
            host.railDiagnosticKey = diagnosticKey
            Log.d(TAG, "alignment calculated: " + diagnosticKey + " gapPx=" + gap)
        }

        if (kotlin.math.abs(host.topRail.translationY - translation.top) > 0.5f) {
            host.topRail.translationY = translation.top
        }
        if (kotlin.math.abs(host.bottomRail.translationY - translation.bottom) > 0.5f) {
            host.bottomRail.translationY = translation.bottom
        }
    }

    private fun visiblePlayerContentBounds(
        rail: LinearLayout,
        profileCards: List<View>,
        diceControl: FrameLayout?,
    ): Pair<Int, Int>? {
        var top = Int.MAX_VALUE
        var bottom = Int.MIN_VALUE

        fun include(view: View) {
            if (view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0) return
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            top = minOf(top, location[1])
            bottom = maxOf(bottom, location[1] + view.height)
        }

        profileCards.forEach(::include)
        if (diceControl != null && isDescendantOf(diceControl, rail)) {
            include(diceControl)
        }
        return if (top < bottom) top to bottom else null
    }

    private fun alignDiceWithProfileCard(host: Host) {
        val dice = host.activeDiceControl ?: return
        val card = host.activeDiceProfileCard ?: return
        if (
            dice.visibility != View.VISIBLE ||
            card.visibility != View.VISIBLE ||
            dice.width <= 0 ||
            dice.height <= 0 ||
            card.width <= 0 ||
            card.height <= 0
        ) {
            return
        }

        val diceLocation = IntArray(2)
        val cardLocation = IntArray(2)
        dice.getLocationOnScreen(diceLocation)
        card.getLocationOnScreen(cardLocation)
        val cardCenter = cardLocation[1] + card.height / 2f
        val diceCenter = diceLocation[1] + dice.height / 2f
        val correction = cardCenter - diceCenter
        if (kotlin.math.abs(correction) > 0.5f) {
            dice.translationY += correction
        }
    }

    private fun isDescendantOf(view: View, ancestor: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    private fun bindTurnCountdown(activity: MainActivity, state: MatchSnapshot) {
        val versionSupportsTimer =
            state.rulesetId == "ludoproof-standard-v4" ||
                state.rulesetId == "ludoproof-team-v4" ||
                state.rulesetId == "ludoproof-standard-v5" || state.rulesetId == "ludoproof-team-v5"
        val hasTrustedTimerContract =
            state.serverTimeAtSnapshot != null &&
                state.turnRollTimeoutMillis?.let { it > 0L } == true
        val deadline = state.turnDeadlineAt.takeIf {
            versionSupportsTimer &&
                hasTrustedTimerContract &&
                state.status == "ACTIVE" &&
                state.pendingRoll == null
        }
        activity.diceView.setTurnDeadlineAt(
            deadlineEpochMillis = deadline,
            serverNowEpochMillis = state.serverTimeAtSnapshot,
            timeoutDurationMillis = state.turnRollTimeoutMillis
                ?: DiceTurnCountdownPolicy.TURN_DURATION_MILLIS,
        )
    }

    private fun addPlayerSlot(
        activity: MainActivity,
        quickChatHost: Host,
        rail: LinearLayout,
        state: MatchSnapshot,
        slot: OfflinePlayerLayout.Slot,
        alignEnd: Boolean,
        activePlayer: PlayerSnapshot?,
        preferredBottomLeftColor: String,
        characterIdsBySeat: List<String>,
    ) {
        val player =
            state.players.firstOrNull {
                val resolvedSlot =
                    if (state.players.size == 2) {
                        if (it.playerId == activity.playerId) {
                            OfflinePlayerLayout.Slot.BOTTOM_LEFT
                        } else {
                            OfflinePlayerLayout.Slot.TOP_RIGHT
                        }
                    } else {
                        OfflinePlayerLayout.slotForColor(
                            color = it.color,
                            preferredBottomLeftColor = preferredBottomLeftColor,
                        )
                    }
                resolvedSlot == slot
            }

        val slotHost =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity =
                    (if (alignEnd) Gravity.END else Gravity.START) or
                        Gravity.CENTER_VERTICAL
                clipChildren = false
                clipToPadding = false
            }

        if (player != null) {
            val active =
                state.status == "ACTIVE" &&
                    player.playerId == activePlayer?.playerId
            val localPlayer = player.playerId == activity.playerId
            val playerWidth = dp(activity, 112)
            val quickChatEnabled = GameSettingsStore(activity).snapshot().quickChatEnabled
            // Footer geometry stays fixed when Quick Chat is switched on/off.
            val reserveProfileFooter =
                state.status == "ACTIVE"
            val activeDiceControl = activeOnlineDiceControl(
                activity = activity,
                player = player,
                enabled = active && localPlayer && activity.canSubmitOnlineGameplayAction() &&
                    activity.onlineRollActionDecision(state).enabled,
            ).apply {
                tag = "dice:${player.playerId}"
                visibility = if (active) View.VISIBLE else View.GONE
            }
            if (alignEnd) slotHost.addView(activeDiceControl)
            // Reuse the portrait view across authoritative snapshot refreshes.
            val playerCard =
                quickChatHost.playerCardsById.getOrPut(player.playerId) {
                    LudoPawsPlayerCardView(activity)
                }
            (playerCard.parent as? ViewGroup)?.removeView(playerCard)
            playerCard.bind(
                player = player,
                characterId = characterIdsBySeat.getOrNull(player.seat),
                active = active,
                computer = player.playerType == "BOT",
                compact = true,
                localPlayer = localPlayer,
                portraitOnEnd = alignEnd,
            )
            if (rail === quickChatHost.topRail) {
                quickChatHost.topProfileCards += playerCard
            } else {
                quickChatHost.bottomProfileCards += playerCard
            }
            val showQuickChat =
                quickChatEnabled && localPlayer && state.status == "ACTIVE"
            val quickChatButton =
                QuickChatButtonView(activity).apply {
                    // Never render disabled chat controls under remote player profiles.
                    visibility = if (showQuickChat) View.VISIBLE else View.GONE
                    isEnabled = showQuickChat
                    alpha = if (isEnabled) 1f else .58f
                    contentDescription = "Quick Chat for ${player.displayName}"
                    setOnClickListener {
                        if (
                            !localPlayer ||
                            GameSettingsStore(activity).snapshot().quickChatEnabled.not()
                        ) {
                            return@setOnClickListener
                        }
                        showQuickChatPopup(this) { emoji ->
                            val current = activity.currentState ?: return@showQuickChatPopup
                            if (current.status != "ACTIVE") return@showQuickChatPopup
                            val sender = current.players.firstOrNull {
                                it.playerId == player.playerId &&
                                    it.playerId == activity.playerId
                            } ?: return@showQuickChatPopup
                            val now = SystemClock.elapsedRealtime()
                            if (
                                quickChatHost.lastQuickChatAtMs != 0L &&
                                now - quickChatHost.lastQuickChatAtMs < 700L
                            ) {
                                return@showQuickChatPopup
                            }
                            if (!activity.realtimeClient.sendQuickChat(emoji)) {
                                activity.statusText.text = "Quick Chat needs a live connection."
                                return@showQuickChatPopup
                            }
                            quickChatHost.lastQuickChatAtMs = now
                            quickChatHost.quickChatReactionView =
                                animateQuickChatReaction(
                                    parent = quickChatHost.board,
                                    emoji = emoji,
                                    senderPlayerId = sender.playerId,
                                    anchor = quickChatHost.playerCardsById[sender.playerId]
                                        ?: return@showQuickChatPopup,
                                )
                            GameSoundFeedback.click(activity)
                        }
                    }
                }
            val isTopRail = rail === quickChatHost.topRail
            val chatSlot =
                if (reserveProfileFooter) {
                    FrameLayout(activity).apply {
                        clipChildren = false
                        clipToPadding = false
                        addView(
                            quickChatButton,
                            FrameLayout.LayoutParams(
                                dp(activity, 36),
                                dp(activity, 36),
                                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                            ),
                        )
                    }
                } else {
                    null
                }
            val chatSlotParams =
                LinearLayout.LayoutParams(
                    playerWidth,
                    dp(activity, 37),
                ).apply {
                    topMargin = dp(activity, 1)
                }
            val profileColumn =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    clipChildren = false
                    clipToPadding = false
                    // Keep the reserved chat row on the outward side of the card,
                    // rather than between the board and the profile.
                    setPadding(
                        0,
                        if (reserveProfileFooter && !isTopRail) dp(activity, 18) else 0,
                        0,
                        0,
                    )
                    if (reserveProfileFooter && isTopRail) {
                        addView(requireNotNull(chatSlot), chatSlotParams)
                    }
                    addView(
                        playerCard,
                        LinearLayout.LayoutParams(
                            playerWidth,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                    if (reserveProfileFooter && !isTopRail) {
                        addView(requireNotNull(chatSlot), chatSlotParams)
                    }
                }
            slotHost.addView(
                profileColumn,
                LinearLayout.LayoutParams(
                    playerWidth,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            if (!alignEnd) {
                slotHost.addView(activeDiceControl)
            }
        }

        rail.addView(
            slotHost,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )
    }

    private fun activeOnlineDiceControl(
        activity: MainActivity,
        player: PlayerSnapshot,
        enabled: Boolean,
    ): FrameLayout {
        val control =
            FrameLayout(activity).apply {
                isClickable = true
                isFocusable = true
                background = null
                elevation = 0f
                clipChildren = false
                clipToPadding = false
                contentDescription = "Roll dice for ${player.displayName}"
                isEnabled = enabled
                alpha = 1f
                setOnClickListener {
                    if (isEnabled) activity.rollVerifiedDice()
                }
            }
        val dice =
            com.rekixo.ludopaws.DiceView(activity).apply {
                setPlayerTeamColor(player.color)
                alpha = 1f
            }
        control.addView(
            dice,
            FrameLayout.LayoutParams(
                dp(activity, 54),
                dp(activity, 54),
                Gravity.CENTER,
            ),
        )
        control.layoutParams =
            LinearLayout.LayoutParams(
                dp(activity, 56),
                dp(activity, 56),
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                setMargins(dp(activity, 1), 0, dp(activity, 1), 0)
            }
        return control
    }

    private fun dispatchFeedback(
        activity: MainActivity,
        host: Host,
        previous: MatchSnapshot?,
        current: MatchSnapshot,
        characterIdsBySeat: List<String>,
    ) {
        if (
            previous == null ||
            previous.matchId != current.matchId
        ) {
            return
        }

        val pending = current.pendingRoll
        val previousPending = previous.pendingRoll
        val newRollEvent =
            pending != null &&
                previousPending?.eventIndex != pending.eventIndex

        // All clients derive the same take from the authoritative event index.
        // The local device marks its input-time sound to avoid playing it twice.
        if (
            newRollEvent &&
            host.feedbackLedger.once(
                matchId = current.matchId,
                key = "ROLL_START:${pending!!.eventIndex}",
            )
        ) {
            GameSoundFeedback.roll(activity, pending!!.eventIndex)
        }

        val newlyResolvedPending =
            pending != null &&
                pending.status == "RESOLVED" &&
                (
                    previousPending?.eventIndex != pending.eventIndex ||
                        previousPending?.status != "RESOLVED"
                )
        if (
            newlyResolvedPending &&
            host.feedbackLedger.once(
                matchId = current.matchId,
                key = "ROLL_SETTLE:${pending!!.eventIndex}",
            )
        ) {
            val eventIndex = pending!!.eventIndex
            activity.mainHandler.postDelayed(
                {
                    if (activity.canRenderUi()) GameSoundFeedback.diceSettle(activity, eventIndex)
                },
                if (newRollEvent) 320L else 0L,
            )
        } else if (
            pending == null &&
            current.history.lastOrNull()?.let { latest ->
                latest.eventIndex != previous.history.lastOrNull()?.eventIndex &&
                    latest.moveTokenIndex == null &&
                    latest.status != "TIMED_OUT" &&
                    ((latest.effectiveOutcome ?: latest.outcome)?.let { it in 1..6 } == true) &&
                    !latest.proofDigest.isNullOrBlank()
            } == true
        ) {
            // No legal destination can clear the pending roll immediately; this
            // history-only path still delivers the shared roll/settle pair.
            val latest = requireNotNull(current.history.lastOrNull())
            if (
                host.feedbackLedger.once(
                    matchId = current.matchId,
                    key = "ROLL_START:${latest.eventIndex}",
                )
            ) {
                GameSoundFeedback.roll(activity, latest.eventIndex)
            }
            if (
                host.feedbackLedger.once(
                    matchId = current.matchId,
                    key = "ROLL_SETTLE:${latest.eventIndex}",
                )
            ) {
                GameSoundFeedback.diceSettle(activity, latest.eventIndex)
            }
        }

        val reactions =
            host.feedbackLedger.filterReactions(
                LudoPawsReactionEngine.derive(
                    previous = previous,
                    current = current,
                ),
            )

        if (reactions.isNotEmpty()) {
            GameSoundFeedback.reaction(
                context = activity,
                reactions = reactions,
                characterIdsBySeat = characterIdsBySeat,
            )
            LudoPawsHaptics.reaction(
                context = activity,
                reactions = reactions,
            )
        }
    }

    private fun hasTokenMovement(
        previous: MatchSnapshot,
        current: MatchSnapshot,
    ): Boolean =
        current.players.any { player ->
            val before =
                previous.players.firstOrNull {
                    it.playerId == player.playerId
                } ?: return@any false
            before.tokens != player.tokens
        }

    private fun movementSeat(
        previous: MatchSnapshot,
        current: MatchSnapshot,
    ): Int? =
        current.players.firstOrNull { player ->
            val before =
                previous.players.firstOrNull {
                    it.playerId == player.playerId
                } ?: return@firstOrNull false
            player.tokens.indices.any { index ->
                val from = before.tokens.getOrNull(index) ?: -1
                val to = player.tokens.getOrNull(index) ?: -1
                to > from
            }
        }?.seat

    private fun dp(
        activity: MainActivity,
        value: Int,
    ): Int =
        (value * activity.resources.displayMetrics.density).roundToInt()

    private const val TAG = "LudoPawsRail"
}
