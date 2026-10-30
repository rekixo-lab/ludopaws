package com.rekixo.ludopaws

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.ui.online.dp

internal fun MainActivity.renderFirstMatchCoach(state: MatchSnapshot) {
    val tag = "paws_first_match_coach"
    val existing = actionPanel.findViewWithTag<TextView>(tag)
    val settings = GameSettingsStore(this)
    val profile = runCatching { ProfileStore(this).snapshot() }.getOrNull()
    val local = state.players.firstOrNull { it.playerId == playerId }
    val hint = FirstMatchCoachPolicy.hint(
        firstMatch = profile != null && profile.totalGames == 0 && profile.characterMatches.isEmpty(),
        enabled = settings.snapshot().playHintsEnabled,
        active = state.status == "ACTIVE" && canSubmitOnlineGameplayAction(),
        localTurn = local?.seat == (state.actingSeat ?: state.turnSeat),
        choices = state.pendingRoll?.legalTokenIndexes?.size,
    )
    if (hint == null) { existing?.let(actionPanel::removeView); return }
    val view = existing ?: TextView(this).also { tip ->
        tip.tag = tag
        tip.gravity = Gravity.CENTER
        tip.textSize = 12f
        tip.setTextColor(0xFFFFF0CA.toInt())
        tip.setPadding(dp(8), dp(6), dp(8), dp(6))
        tip.setOnClickListener { settings.setPlayHintsEnabled(false); actionPanel.removeView(tip) }
        actionPanel.addView(tip, LinearLayout.LayoutParams(-1, -2))
    }
    val copy = "$hint · Tap to hide"
    if (view.text.toString() != copy) view.text = copy
}
