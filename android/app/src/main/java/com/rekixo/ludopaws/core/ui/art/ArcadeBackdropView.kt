package com.rekixo.ludopaws

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.rekixo.ludopaws.core.ui.art.RecessedPlywoodDrawable
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground

/**
 * Shared full-screen plywood backdrop across all app screens, including gameplay.
 *
 * Both legacy background entry points intentionally use the same code-drawn
 * recessed plywood skin; neither loads a WebP or another image resource.
 */
class ArcadeBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    init {
        isClickable = false
        isFocusable = false
        contentDescription = null
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        applySharedBackdrop()
    }

    /** Compatibility entry point used by gameplay callers. */
    fun useGameplayBackground() {
        applySharedBackdrop()
    }

    /** Compatibility entry point used by setup and the rest of the UI. */
    fun useUiBackground() {
        applySharedBackdrop()
    }

    private fun applySharedBackdrop() {
        // Full-bleed square outside corners; the existing inset carved pocket
        // and plywood rim remain intact. Reuse the drawable on screen changes.
        if (background !is RecessedPlywoodDrawable) {
            background = recessedPlywoodBackground(context, outerCornerRadiusDp = 0f)
        }
    }
}
