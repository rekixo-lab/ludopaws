package com.rekixo.ludopaws.ui.offline.gameplay

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.MatchSnapshot
import com.rekixo.ludopaws.OfflineGameActivity
import com.rekixo.ludopaws.feature.settings.data.local.GameSoundFeedback
import com.rekixo.ludopaws.ui.offline.common.*

internal fun OfflineGameActivity.presentPassAndPlayHandoff(
    previous: MatchSnapshot?,
    current: MatchSnapshot,
) {
    val handoff =
        OfflinePassAndPlayHandoffPolicy.resolve(
            mode = gameMode,
            previous = previous,
            current = current,
        ) ?: return

    turnHandoffDialog?.dismiss()

    val dialog = Dialog(this)
    val card =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(22), dp(24), dp(20))
            background =
                LudoProofTheme.hudPanelDrawable(
                    this@presentPassAndPlayHandoff,
                    goldBorder = true,
                )

            addView(
                TextView(this@presentPassAndPlayHandoff).apply {
                    text = "PASS THE PHONE"
                    LudoProofTheme.body(
                        this,
                        11f,
                        centered = true,
                        bright = true,
                    )
                    setTextColor(0xFF6DE7FF.toInt())
                },
            )

            addView(
                TextView(this@presentPassAndPlayHandoff).apply {
                    text = handoff.displayName
                    LudoProofTheme.title(
                        this,
                        if (isCompactSetup()) 24f else 28f,
                        gold = true,
                    )
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, dp(4))
                },
            )

            addView(
                TextView(this@presentPassAndPlayHandoff).apply {
                    text = "${handoff.color.lowercase().replaceFirstChar { it.uppercase() }} paws • your turn"
                    LudoProofTheme.body(
                        this,
                        if (isCompactSetup()) 11f else 12f,
                        centered = true,
                        bright = true,
                    )
                    setPadding(0, 0, 0, dp(16))
                },
            )

            addView(
                Button(this@presentPassAndPlayHandoff).apply {
                    text = "READY"
                    textSize = if (isCompactSetup()) 12f else 13f
                    minHeight = 0
                    LudoProofTheme.positive(this)
                    contentDescription = "Ready for ${handoff.displayName} turn"
                    setOnClickListener {
                        GameSoundFeedback.click(this@presentPassAndPlayHandoff)
                        dialog.dismiss()
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(if (isCompactSetup()) 48 else 52),
                ),
            )
        }

    dialog.setContentView(
        card,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    dialog.setCancelable(false)
    dialog.setCanceledOnTouchOutside(false)
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        attributes =
            attributes.apply {
                dimAmount = 0.82f
            }
    }
    dialog.setOnDismissListener {
        if (turnHandoffDialog === dialog) {
            turnHandoffDialog = null
        }
    }

    turnHandoffDialog = dialog
    dialog.show()
    dialog.window?.setLayout(
        if (isCompactSetup()) dp(300) else dp(340),
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}
