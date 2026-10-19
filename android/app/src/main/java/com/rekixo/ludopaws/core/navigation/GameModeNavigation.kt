package com.rekixo.ludopaws

import android.app.Activity
import android.content.Intent
import com.rekixo.ludopaws.feature.mode.presentation.RemoteModeEntryActivity
import com.rekixo.ludopaws.feature.friends.presentation.FriendsActivity
import com.rekixo.ludopaws.feature.team.presentation.TeamUpActivity
import com.rekixo.ludopaws.feature.safety.PlayAgeSignalsGate

fun Activity.openGameMode(
    mode: GameMode,
    resumeSavedMatch: Boolean = false,
) {
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

    val launch = {
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
    if (mode.isRemote) PlayAgeSignalsGate.requireConnectedAccess(this, launch)
    else launch()
}
