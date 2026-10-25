package com.rekixo.ludopaws.ui.offline.gameplay

import android.os.SystemClock
import com.rekixo.ludopaws.OfflineGameActivity
import com.rekixo.ludopaws.feature.settings.data.local.GameSoundFeedback
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.ui.quickchat.QuickChatEmojiCatalog

/**
 * Presents a local animated reaction beside its sender, without gameplay text.
 * Gameplay status copy is intentionally left untouched.
 */
internal fun OfflineGameActivity.presentQuickReaction(
    emoji: String,
    senderPlayerId: String? = null,
): Boolean {
    if (!GameSettingsStore(this).snapshot().quickChatEnabled) return false
    if (emoji !in QuickChatEmojiCatalog.EMOJIS) return false
    val now = SystemClock.elapsedRealtime()
    val presentation =
        OfflineQuickReactionPolicy.resolve(
            state = session.snapshot(),
            emoji = emoji,
            nowMs = now,
            lastShownAtMs = quickReactionLastShownAtMs,
            senderPlayerId = senderPlayerId,
        ) ?: return false
    val board = boardView ?: return false

    val anchor = playerCardViewsBySeat[presentation.seat] ?: return false
    quickReactionView = com.rekixo.ludopaws.ui.quickchat.animateQuickChatReaction(
        parent = board,
        emoji = presentation.emoji,
        senderPlayerId = presentation.playerId,
        anchor = anchor,
    )
    quickReactionLastShownAtMs = now
    GameSoundFeedback.click(this)
    return true
}
