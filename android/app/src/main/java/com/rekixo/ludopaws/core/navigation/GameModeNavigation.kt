package com.rekixo.ludopaws

import com.rekixo.ludopaws.ui.dialogs.PlywoodAlertDialogBuilder
import android.app.Activity
import android.content.Intent
import com.rekixo.ludopaws.feature.friends.presentation.FriendsActivity
import com.rekixo.ludopaws.feature.team.presentation.TeamUpActivity
import com.rekixo.ludopaws.feature.safety.PlayAgeSignalsObserver

fun Activity.openGameMode(
    mode: GameMode,
    resumeSavedMatch: Boolean = false,
    beforeLaunch: (() -> Boolean)? = null,
    onLaunched: (() -> Unit)? = null,
) {
    if (mode.isRemote && !BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED) {
        PlywoodAlertDialogBuilder(this)
            .setTitle("Online play is temporarily unavailable")
            .setMessage(PlayAgeSignalsObserver.offlineExplanation())
            .setNegativeButton("CLOSE", null)
            .setPositiveButton("PLAY OFFLINE") { _, _ ->
                openGameMode(GameMode.COMPUTER, beforeLaunch = beforeLaunch, onLaunched = onLaunched)
            }
            .show()
        return
    }
    if (mode.isRemote && !ConnectivityMonitor(this) { }.isOnline()) {
        android.widget.Toast.makeText(this, "You are offline. Connect for online play, or choose Robot / Pass & Play.", android.widget.Toast.LENGTH_LONG).show()
    }
    if (mode.isRemote && com.rekixo.ludopaws.feature.safety.FamilyOnlineSafety.showNoticeIfNeeded(this) {
            openGameMode(mode, resumeSavedMatch, beforeLaunch, onLaunched)
        }) return
    val target =
        when (
            GameModeRouting
                .destination(
                    mode,
                )
        ) {
            GameModeDestination.ONLINE_ACTIVITY ->
                MainActivity::class.java

            GameModeDestination.REMOTE_MODE_ENTRY ->
                TeamUpActivity::class.java

            GameModeDestination.FRIENDS_ACTIVITY ->
                FriendsActivity::class.java

            GameModeDestination.OFFLINE_ACTIVITY ->
                OfflineGameActivity::class.java
        }

    if (beforeLaunch?.invoke() == false) return
    val launched = launchPlayerScreen(
        Intent(
            this,
            target,
        )
            .putExtra(
                GameModeIntent.EXTRA_GAME_MODE,
                mode.wireValue,
            )
            .putExtra(
                GameModeIntent.EXTRA_RESUME_SAVED_MATCH,
                resumeSavedMatch,
            ),
    )
    if (launched) onLaunched?.invoke()
}
