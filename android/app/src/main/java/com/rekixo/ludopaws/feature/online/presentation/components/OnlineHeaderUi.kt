package com.rekixo.ludopaws.ui.online

import android.app.Dialog
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.feature.online.*
import com.rekixo.ludopaws.ui.dialogs.baseDialog
import com.rekixo.ludopaws.ui.dialogs.dialogPanel
import com.rekixo.ludopaws.ui.dialogs.fullWidthParams
import com.rekixo.ludopaws.ui.dialogs.sizeDialog

internal fun MainActivity.registerOnlineBackCallback() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
        ) {
            requestRemoteExit()
        }
    }
}

internal fun MainActivity.requestRemoteExit() {
    if (matchId == null && publicMatchmakingStore.load() != null) {
        cancelPublicMatchmaking(exitAfterCancellation = true)
        return
    }
    val hasUnfinishedMatch =
        !matchId.isNullOrBlank() &&
            currentState?.status != "FINISHED"
    val activeMatch = currentState?.status == "ACTIVE"

    if (!hasUnfinishedMatch) {
        remoteExitConfirmationDialog?.dismiss()
        remoteExitConfirmationDialog = null
        if (abandonRemoteSessionState()) finish()
        return
    }
    if (
        isFinishing ||
        isDestroyed ||
        remoteExitConfirmationDialog?.isShowing == true
    ) {
        return
    }

    val dialog = baseDialog(this)
    dialog.setCanceledOnTouchOutside(false)
    val teamUp = gameMode == GameMode.TEAM_UP
    val panel = dialogPanel(
        context = this,
        title = if (teamUp) "LEAVE TEAM UP?" else "LEAVE MATCH?",
        subtitle = if (activeMatch) "MATCH IN PROGRESS" else "ROOM NOT FINISHED",
        dialog = dialog,
        usePlywoodSurface = true,
        usePlywoodCloseButton = true,
        showBrandBadge = false,
    )

    panel.addView(
        TextView(this).apply {
            text = when {
                activeMatch && teamUp ->
                    "Leaving this active 2 vs 2 match forfeits your whole team. Cancel to keep playing."
                activeMatch ->
                    "Leaving attempts a server-side forfeit. Ranked results are saved only after the server confirms the match is finished."
                teamUp ->
                    "This Team Up room is not finished. If it becomes active before you leave, forfeiting may affect your whole team."
                else ->
                    "This match is not confirmed as finished. Leaving attempts a server-side forfeit if it is active."
            }
            LudoProofTheme.body(
                this,
                if (isCompactOnline()) 12f else 13f,
                centered = true,
                bright = true,
            )
            setPadding(dp(8), dp(12), dp(8), dp(4))
        },
        fullWidthParams(this, 8),
    )

    panel.addView(
        TextView(this).apply {
            text = "Cancel to stay in the match, or confirm to leave."
            LudoProofTheme.body(
                this,
                if (isCompactOnline()) 10.5f else 11.5f,
                centered = true,
            )
        },
        fullWidthParams(this, 4),
    )

    val actions = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }
    actions.addView(
        Button(this).apply {
            text = "CANCEL"
            contentDescription = "Cancel exit and continue match"
            LudoProofTheme.secondary(this)
            setOnClickListener { dialog.dismiss() }
        },
        LinearLayout.LayoutParams(
            0,
            dp(if (isCompactOnline()) 48 else 52),
            1f,
        ),
    )
    actions.addView(
        Button(this).apply {
            text = if (teamUp) "EXIT TEAM" else "LEAVE MATCH"
            contentDescription = if (teamUp) "Confirm exit and forfeit Team Up match" else "Confirm leave match"
            LudoProofTheme.danger(this)
            // The themed default is 17sp with 16dp horizontal padding. On compact
            // phones the equal-width action buttons cannot fit "LEAVE MATCH", so
            // Android wraps the second word and clips it at the button's fixed height.
            // Keep the complete label on one centered line with a compact hit target.
            textSize = if (isCompactOnline()) 13.5f else 15f
            isSingleLine = true
            maxLines = 1
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0)
            minWidth = 0
            minHeight = dp(if (isCompactOnline()) 48 else 52)
            setOnClickListener {
                // Keep the dialog open if the secure forfeit could not be persisted.
                if (abandonRemoteSessionState()) {
                    dialog.dismiss()
                    finish()
                }
            }
        },
        LinearLayout.LayoutParams(
            0,
            dp(if (isCompactOnline()) 48 else 52),
            1f,
        ).apply { marginStart = dp(10) },
    )
    panel.addView(actions, fullWidthParams(this, 14))

    dialog.setContentView(panel)
    sizeDialog(dialog, if (isCompactOnline()) 0.92f else 0.78f)
    dialog.setOnDismissListener {
        if (remoteExitConfirmationDialog === dialog) {
            remoteExitConfirmationDialog = null
        }
    }
    remoteExitConfirmationDialog = dialog
    dialog.show()
}

internal fun MainActivity.onlineHero(): FrameLayout =
    FrameLayout(this).apply {
        val heroHeight = dp(if (isCompactOnline()) 160 else 182)

        addView(
            OnlineLobbyArtView(this@onlineHero),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                heroHeight,
            ),
        )

        val copy =
            LinearLayout(this@onlineHero).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dp(if (isCompactOnline()) 104 else 146),
                    dp(14),
                    dp(if (isCompactOnline()) 12 else 24),
                    dp(14),
                )

                addView(
                    TextView(this@onlineHero).apply {
                        text =
                            if (gameMode == GameMode.TEAM_UP) {
                                "VERIFIED TEAM UP"
                            } else {
                                "VERIFIED ONLINE"
                            }
                        LudoProofTheme.title(
                            this,
                            if (isCompactOnline()) 21f else 27f,
                            gold = true,
                        )
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    },
                )

                addView(
                    TextView(this@onlineHero).apply {
                        text =
                            if (gameMode == GameMode.TEAM_UP) {
                                "Red + Yellow vs Green + Blue"
                            } else {
                                "Create a room or join with a match code"
                            }
                        LudoProofTheme.body(
                            this,
                            if (isCompactOnline()) 11f else 12.5f,
                            bright = true,
                        )
                        gravity = Gravity.START
                        setPadding(0, dp(4), 0, 0)
                    },
                )

                addView(
                    TextView(this@onlineHero).apply {
                        text =
                            if (gameMode == GameMode.TEAM_UP) {
                                "Remote server dice authority • exit forfeits your team"
                            } else {
                                "Remote server dice authority • exit abandons this match"
                            }
                        LudoProofTheme.body(
                            this,
                            if (isCompactOnline()) 9.5f else 10.5f,
                        )
                        gravity = Gravity.START
                        setPadding(0, dp(3), 0, 0)
                    },
                )
            }

        addView(
            copy,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                heroHeight,
            ),
        )
    }
