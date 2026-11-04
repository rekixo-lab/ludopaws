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
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(36), dp(24), dp(24))
            setBackgroundColor(0xFF143F43.toInt())
        }
        setContentView(ScrollView(this).apply { addView(body) })
        fun label(value: String) { body.addView(TextView(this).apply {
            text = value; textSize = 20f; setTextColor(-1); setPadding(0, dp(12), 0, dp(12))
        }) }
        label(if (link == null) "This room link is invalid." else "Play together · ${link?.room}")
        if (link != null) {
            label("Open your friend's invitation. Full, started or expired rooms may no longer accept players.")
            body.addView(Button(this).apply {
                text = "Continue to room"
                setOnClickListener { FamilyAgeScreen.ensureEntryChoice(this@RoomLinkActivity) { continueToRoom() } }
            })
        }
        body.addView(Button(this).apply { text = "Close"; setOnClickListener { finish() } })
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
