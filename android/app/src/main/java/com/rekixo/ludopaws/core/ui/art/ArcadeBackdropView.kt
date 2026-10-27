package com.rekixo.ludopaws

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.rekixo.ludopaws.core.ui.art.PlywoodTextureDrawable
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.feature.boards.content.classic.ClassicGameplayBackdropDrawable
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory

/**
 * Shared plywood UI backdrop with an explicitly scoped Classic gameplay habitat.
 */
class ArcadeBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var gameplayFinish = false
    private var jungleEnvironment = false

    init {
        isClickable = false
        isFocusable = false
        contentDescription = null
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        applySharedBackdrop()
    }

    /** Compatibility entry point used by gameplay callers. */
    fun useGameplayBackground(boardEnvironment: Boolean = false) {
        val jungle = boardEnvironment && CosmeticInventoryStore(context).selectedId(CosmeticCategory.BOARD) == "board_classic"
        if (!gameplayFinish || jungleEnvironment != jungle) {
            gameplayFinish = true
            jungleEnvironment = jungle
            background = if (jungle) {
                ClassicGameplayBackdropDrawable(27f * resources.displayMetrics.density)
            } else plywoodBackground(context, cornerRadiusDp = 27f, gameplayFinish = true)
        }
    }

    /** Compatibility entry point used by setup and the rest of the UI. */
    fun useUiBackground() {
        if (gameplayFinish) {
            gameplayFinish = false
            jungleEnvironment = false
            background = plywoodBackground(context, cornerRadiusDp = 27f)
        }
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
