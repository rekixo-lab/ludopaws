package com.rekixo.ludopaws.feature.sharing

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.*
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.feature.friends.presentation.FriendsActivity
import com.rekixo.ludopaws.feature.safety.FamilyAgeScreen
import com.rekixo.ludopaws.feature.safety.FamilyOnlineSafety

/** Separate entry point so an invitation never opens Home's abandon-match cleanup. */
class RoomLinkActivity : Activity() {
    private val link by lazy { runCatching { RoomLink.parse(intent.data) }.getOrNull() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LudoProofTheme.configureWindow(this)
        val page = com.rekixo.ludopaws.feature.growth.GrowthPage(this, "Play together", onBack = { finish() })
        val ui = com.rekixo.ludopaws.feature.growth.GrowthUi
        val card = ui.card(this)
        val friends = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER }
        listOf("dog", "duck").forEach { id ->
            friends.addView(ui.portrait(this, id), LinearLayout.LayoutParams(ui.dp(this, 108), ui.dp(this, 120)))
        }
        ui.add(card, friends, 0)
        ui.heading(card, if (link == null) "This invitation needs a refresh" else "Your next game starts with a friend",
            if (link == null) "Ask your friend for a fresh room link, then open it here." else "A few friendly paws. One happy game together.")
        link?.let { invitation ->
            ui.add(card, ui.pill(this, "FRIENDS · PRIVATE ROOM"), 18)
            ui.add(card, ui.copy(this, invitation.room, 28f, true).apply {
                gravity = android.view.Gravity.CENTER; typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, ui.dp(this@RoomLinkActivity, 12), 0, ui.dp(this@RoomLinkActivity, 12))
                contentDescription = "Room code: " + invitation.room.toCharArray().joinToString(" ")
                setTextIsSelectable(true)
            })
            ui.add(card, ui.button(this, "Open my friend's room") {
                FamilyAgeScreen.ensureEntryChoice(this@RoomLinkActivity) { continueToRoom() }
            })
            ui.add(card, ui.copy(this, "Your invitation will appear in Friends. New friends need the host's approval first.", 13f), 14)
            ui.add(card, ui.copy(this, "Rooms can fill up, start or expire. Ask for a fresh invitation if this one is no longer available.", 12f), 10)
        }
        ui.add(card, ui.button(this, "Maybe later", primary = false) { finish() }, 14)
        ui.add(page.body, card, 10)
    }
    private fun continueToRoom() {
        val target = link ?: return
        if (isFinishing || isDestroyed) return
        if (!BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED) {
            Toast.makeText(this, "Online play is temporarily unavailable. You can still play from the game menu.", Toast.LENGTH_LONG).show()
            return
        }
        if (FamilyOnlineSafety.showNoticeIfNeeded(this) { continueToRoom() }) return
        val saved = SecureSessionStore(this).load()
        if (saved != null && saved.matchId != target.room) {
            Toast.makeText(this, "Finish or exit your saved match before joining another room.", Toast.LENGTH_LONG).show()
            return
        }
        val destination = if (saved != null) MainActivity::class.java else FriendsActivity::class.java
        if (launchPlayerScreen(Intent(this, destination)
                .putExtra(GameModeIntent.EXTRA_GAME_MODE, saved?.modeWire ?: "FRIENDS")
                .putExtra(GameModeIntent.EXTRA_RESUME_SAVED_MATCH, saved != null)
                .putExtra(RoomLink.EXTRA_LINK, target.url()))) {
            com.rekixo.ludopaws.feature.growth.GrowthMetrics.increment(this, "room_link_opened")
            finish()
        }
    }
    override fun onDestroy() { FamilyAgeScreen.detach(this); super.onDestroy() }
}
