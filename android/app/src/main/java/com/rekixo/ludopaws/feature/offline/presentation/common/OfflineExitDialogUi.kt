package com.rekixo.ludopaws.ui.offline.common

import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.OfflineGameActivity
import com.rekixo.ludopaws.feature.store.domain.PostWinInterstitialPolicy
import com.rekixo.ludopaws.ui.dialogs.baseDialog
import com.rekixo.ludopaws.ui.dialogs.dialogPanel
import com.rekixo.ludopaws.ui.dialogs.fullWidthParams
import com.rekixo.ludopaws.ui.dialogs.sizeDialog

internal fun OfflineGameActivity.requestOfflineExit() {
    val snapshot =
        session.snapshot()
    val activeGameplay =
        boardView != null &&
            snapshot?.status ==
            "ACTIVE"

    if (!activeGameplay) {
        exitConfirmationDialog
            ?.dismiss()
        exitConfirmationDialog =
            null

        val shouldShowPostWinAd = PostWinInterstitialPolicy.shouldShowOffline(
            matchStatus = snapshot?.status,
            winnerPlayerId = snapshot?.winnerPlayerId,
            humanPlayerId = session.localPlayerId(),
            isComputerMode = isComputerMode,
        )

        val finishedMatchId = snapshot?.matchId
        val finishExit: () -> Unit = {
            if (snapshot?.status == "FINISHED") {
                session.clear()
            }
            prepareOfflineUiTransition()
            finish()
        }
        if (
            shouldShowPostWinAd &&
            finishedMatchId != null &&
            showPostWinInterstitialIfReady(finishedMatchId, finishExit)
        ) {
            return
        }
        finishExit()
        return
    }

    if (
        isFinishing ||
        isDestroyed ||
        exitConfirmationDialog
            ?.isShowing ==
        true
    ) {
        return
    }

    offlineModalPaused = true
    pauseOfflineActions()
    val dialog =
        baseDialog(this)
    dialog.setCanceledOnTouchOutside(
        false,
    )

    val panel =
        dialogPanel(
            context = this,
            title = "EXIT GAME?",
            subtitle =
                if (isComputerMode) {
                    "COMPUTER GAME IN PROGRESS"
                } else {
                    "PASS & PLAY IN PROGRESS"
                },
            dialog = dialog,
            usePlywoodSurface = true,
            usePlywoodCloseButton = true,
            showBrandBadge = false,
        )

    val message = TextView(this).apply {
        text = "Save this match to continue later, or discard it and return Home."
        LudoProofTheme.body(this, 13f, centered = true, bright = true)
        setPadding(dp(8), dp(12), dp(8), dp(4))
    }
    panel.addView(message, fullWidthParams(this, 8))

    val actions = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }
    val keepPlaying = Button(this).apply {
        text = "KEEP PLAYING"
        LudoProofTheme.secondary(this)
        setOnClickListener { dialog.dismiss() }
    }
    val discard = Button(this).apply {
        text = "DISCARD MATCH"
        LudoProofTheme.danger(this)
        setOnClickListener {
            session.clear()
            prepareOfflineUiTransition()
            dialog.dismiss()
            finish()
        }
    }
    val save = Button(this).apply {
        text = "SAVE & EXIT"
        LudoProofTheme.positive(this)
        setOnClickListener {
            isEnabled = false
            discard.isEnabled = false
            keepPlaying.isEnabled = false
            dialog.setCancelable(false)
            message.text = "Saving your match…"
            engine.saveActiveGame { saved ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (saved) {
                        prepareOfflineUiTransition()
                        finish()
                        dialog.dismiss()
                    } else if (dialog.isShowing) {
                        message.text = "Match could not be saved. Try again or keep playing."
                        isEnabled = true
                        discard.isEnabled = true
                        keepPlaying.isEnabled = true
                        dialog.setCancelable(true)
                    }
                }
            }
        }
    }
    for (action in listOf(save, keepPlaying, discard)) {
        actions.addView(action, fullWidthParams(this, 6))
    }
    panel.addView(actions, fullWidthParams(this, 12))

    dialog.setContentView(
        panel,
    )
    sizeDialog(
        dialog,
        if (isCompactSetup()) {
            0.92f
        } else {
            0.78f
        },
    )
    dialog.setOnDismissListener {
        if (
            exitConfirmationDialog ===
            dialog
        ) {
            exitConfirmationDialog =
                null
        }
        offlineModalPaused = false
        resumeOfflineActions()
    }

    exitConfirmationDialog =
        dialog
    dialog.show()
}
