package com.rekixo.ludopaws.ui.offline.common

import android.view.Gravity
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

    panel.addView(
        TextView(this).apply {
            text =
                "Your current game is still in progress. If you exit, this match will be discarded."
            LudoProofTheme.body(
                this,
                if (isCompactSetup()) 12f else 13f,
                centered = true,
                bright = true,
            )
            setPadding(
                dp(8),
                dp(12),
                dp(8),
                dp(4),
            )
        },
        fullWidthParams(
            this,
            8,
        ),
    )

    panel.addView(
        TextView(this).apply {
            text =
                "Cancel to keep playing, or Exit Game to leave and return Home."
            LudoProofTheme.body(
                this,
                if (isCompactSetup()) 10.5f else 11.5f,
                centered = true,
            )
        },
        fullWidthParams(
            this,
            4,
        ),
    )

    val actions =
        LinearLayout(this).apply {
            orientation =
                LinearLayout.HORIZONTAL
            gravity =
                Gravity.CENTER
        }

    actions.addView(
        Button(this).apply {
            text = "CANCEL"
            contentDescription =
                "Cancel exit and continue game"
            LudoProofTheme.secondary(this)
            setOnClickListener {
                dialog.dismiss()
            }
        },
        LinearLayout.LayoutParams(
            0,
            dp(if (isCompactSetup()) 48 else 52),
            1f,
        ),
    )

    actions.addView(
        Button(this).apply {
            text = "EXIT GAME"
            contentDescription =
                "Confirm exit and discard current game"
            LudoProofTheme.danger(this)
            setOnClickListener {
                session.clear()
                prepareOfflineUiTransition()
                dialog.dismiss()
                finish()
            }
        },
        LinearLayout.LayoutParams(
            0,
            dp(if (isCompactSetup()) 48 else 52),
            1f,
        ).apply {
            marginStart =
                dp(10)
        },
    )

    panel.addView(
        actions,
        fullWidthParams(
            this,
            14,
        ),
    )

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
