package com.rekixo.ludopaws.ui.game

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.components.appIconActionButton
import com.rekixo.ludopaws.core.ui.icons.AppIconName

/**
 * Fixed top-level game controls. Both online and offline play use the same
 * shared semantic action controls so icon geometry and button surfaces do not
 * drift by game mode.
 */
internal fun addGameTopActions(
    host: FrameLayout,
    context: android.content.Context,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    centerContent: View? = null,
): FrameLayout {
    val buttonSize = LudoProofTheme.dp(context, 52)
    val sideMargin = LudoProofTheme.dp(context, 16)
    val actionTopMargin = LudoProofTheme.dp(context, 14)
    val bar =
        FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

    bar.addView(
        appIconActionButton(
            context = context,
            contentDescription = "Back",
            iconName = AppIconName.CHEVRON_LEFT,
            sizeDp = 52,
            onClick = onBack,
        ).apply {
            background = plywoodBackground(
                context = context,
                cornerRadiusDp = 999f,
                gameplayFinish = true,
            )
        },
        FrameLayout.LayoutParams(
            buttonSize,
            buttonSize,
            Gravity.TOP or Gravity.START,
        ).apply {
            leftMargin = sideMargin
            topMargin = actionTopMargin
        },
    )

    bar.addView(
        appIconActionButton(
            context = context,
            contentDescription = "Game settings",
            iconName = AppIconName.SETTINGS,
            sizeDp = 52,
            onClick = onSettings,
        ).apply {
            background = plywoodBackground(
                context = context,
                cornerRadiusDp = 999f,
                gameplayFinish = true,
            )
        },
        FrameLayout.LayoutParams(
            buttonSize,
            buttonSize,
            Gravity.TOP or Gravity.END,
        ).apply {
            rightMargin = sideMargin
            topMargin = actionTopMargin
        },
    )

    centerContent?.let { center ->
        bar.addView(center, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            buttonSize,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ).apply {
            leftMargin = sideMargin + buttonSize + LudoProofTheme.dp(context, 8)
            rightMargin = leftMargin
            topMargin = actionTopMargin
        })
    }

    host.addView(
        bar,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ),
    )
    return bar
}
