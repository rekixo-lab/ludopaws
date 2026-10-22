package com.rekixo.ludopaws

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.rekixo.ludopaws.core.ui.art.PlywoodTextureDrawable
import com.rekixo.ludopaws.core.ui.art.plywoodBackground

/**
 * Shared full-screen plywood backdrop across all app screens, including gameplay.
 *
 * Both legacy background entry points intentionally use the same code-drawn
 * plain plywood skin; neither loads a WebP or another image resource.
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
        // Full-screen plain plywood with a 27dp corner radius (20% above the previous 22.5dp).
        // Reuse the same drawable when switching screens.
        if (background !is PlywoodTextureDrawable) {
            background = plywoodBackground(context, cornerRadiusDp = 27f)
        }
    }
}
