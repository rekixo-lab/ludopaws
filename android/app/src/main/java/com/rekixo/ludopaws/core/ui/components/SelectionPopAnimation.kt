package com.rekixo.ludopaws.core.ui.components

import android.view.View
import android.animation.ValueAnimator
import android.os.Build
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore

/**
 * Reuses Online Match's short press-and-return feedback for selection controls
 * across the game setup and matchmaking screens.
 */
internal fun View.playSelectionPopAnimation(onComplete: (() -> Unit)? = null) {
    animate().cancel()
    scaleX = 1f
    scaleY = 1f
    // Decorative button motion is optional. Never delay selection or its callback
    // when Android animation scale is disabled or the player chooses reduced motion.
    if (
        GameSettingsStore(context).snapshot().reduceMotionEnabled ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled())
    ) {
        onComplete?.invoke()
        return
    }
    animate()
        .scaleX(0.965f)
        .scaleY(0.965f)
        .setDuration(75L)
        .withEndAction {
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(135L)
                .withEndAction { onComplete?.invoke() }
                .start()
        }
        .start()
}
