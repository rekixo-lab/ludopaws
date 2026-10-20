package com.rekixo.ludopaws.ui.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import com.rekixo.ludopaws.GameMode
import com.rekixo.ludopaws.HomeActivity
import com.rekixo.ludopaws.openGameMode
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore

/**
 * First-run guide is optional, offline-safe, and asks for no personal data.
 * It never triggers ads, social sign-in, age prompts or backend calls.
 */
internal class HomeGuideStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "ludopaws_home_guide_v1", Context.MODE_PRIVATE,
    )
    fun wasSeen(): Boolean = prefs.getBoolean("intro_seen", false)
    fun markSeen() { prefs.edit().putBoolean("intro_seen", true).apply() }
}

internal fun HomeActivity.showFirstRunGuideIfNeeded() {
    if (homeGuideVisible || isFinishing || isDestroyed) return
    val store = HomeGuideStore(this)
    if (store.wasSeen() || ProfileStore(this).snapshot().totalGames > 0) return
    homeGuideVisible = true
    window.decorView.post {
        if (isFinishing || isDestroyed) {
            homeGuideVisible = false
            return@post
        }
        showLudoPawsGuide(
            activity = this,
            onTryRobot = { openGameMode(GameMode.COMPUTER) },
            onDismissed = {
                store.markSeen()
                homeGuideVisible = false
            },
        )
    }
}

/** Reusable from Settings; help remains available after first-run dismissal. */
internal fun showLudoPawsGuide(
    activity: Activity,
    onTryRobot: (() -> Unit)? = null,
    onDismissed: (() -> Unit)? = null,
) {
    if (activity.isFinishing || activity.isDestroyed) return
    val builder = AlertDialog.Builder(activity)
        .setTitle("Welcome to Ludo Paws!")
        .setMessage(
            "1. Choose Robot to practice alone, or Pass & Play with friends on one phone.\n\n" +
                "2. Roll the dice, choose a highlighted pawn and race all four paws home.\n\n" +
                "3. Earn XP and badges by completing games. You can see your progress in Profile.\n\n" +
                "4. Change sound, game speed and Reduce motion in Settings.\n\n" +
                "Online modes are available only when account safety requirements are met.",
        )
        .setPositiveButton("GOT IT", null)
        .setNegativeButton("NOT NOW", null)
    if (onTryRobot != null) {
        builder.setNeutralButton("PRACTICE WITH ROBOT") { _, _ -> onTryRobot() }
    }
    builder.create().apply {
        setOnDismissListener { onDismissed?.invoke() }
        show()
    }
}
