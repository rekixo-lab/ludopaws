package com.rekixo.ludopaws

import com.rekixo.ludopaws.ui.dialogs.PlywoodAlertDialogBuilder
import android.app.Activity
import android.content.Intent
import com.rekixo.ludopaws.feature.mode.presentation.RemoteModeEntryActivity
import com.rekixo.ludopaws.feature.friends.presentation.FriendsActivity
import com.rekixo.ludopaws.feature.team.presentation.TeamUpActivity
import com.rekixo.ludopaws.feature.safety.PlayAgeSignalsObserver

fun Activity.openGameMode(
    mode: GameMode,
    resumeSavedMatch: Boolean = false,
) {
    if (mode.isRemote && !BuildConfig.LUDOPAWS_CONNECTED_FEATURES_ENABLED) {
        PlywoodAlertDialogBuilder(this)
            .setTitle("Online play is temporarily unavailable")
            .setMessage(PlayAgeSignalsObserver.offlineExplanation())
            .setNegativeButton("CLOSE", null)
            .setPositiveButton("PLAY OFFLINE") { _, _ ->
                openGameMode(GameMode.COMPUTER)
            }
            .show()
        return
    }
    if (mode.isRemote && com.rekixo.ludopaws.feature.safety.FamilyOnlineSafety.showNoticeIfNeeded(this) {
            openGameMode(mode, resumeSavedMatch)
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

    startActivity(
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
}
