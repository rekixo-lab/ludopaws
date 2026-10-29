package com.rekixo.ludopaws

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.View
import com.rekixo.ludopaws.core.ui.art.PlywoodTextureDrawable
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.feature.boards.rendering.GameplayBackdropDrawable
import com.rekixo.ludopaws.feature.boards.rendering.GameplayBoardEnvironment
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.data.local.StorePreferences
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory

/**
 * Shared plywood UI backdrop with explicitly scoped board-themed gameplay habitats.
 */
class ArcadeBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var gameplayFinish = false
    private var boardEnvironmentEnabled = false
    private var environmentBoardId: String? = null
    private val preferences = context.applicationContext.getSharedPreferences(StorePreferences.PREFS_NAME, Context.MODE_PRIVATE)
    private val refreshBackdrop = Runnable {
        if (gameplayFinish && boardEnvironmentEnabled) applyGameplayBackdrop()
    }
    private val inventoryListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == StorePreferences.KEY_SELECTED_BOARD || key == "offline_selection_BOARD" || (key.startsWith("local_equipment_") && key.endsWith("_BOARD")) ||
            key == StorePreferences.KEY_OWNED_COSMETICS || key == StorePreferences.KEY_WALLET_ACCOUNT_ID) {
            removeCallbacks(refreshBackdrop)
            post(refreshBackdrop)
        }
    }

    init {
        isClickable = false
        isFocusable = false
        contentDescription = null
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        applySharedBackdrop()
    }

    /** Compatibility entry point used by gameplay callers. */
    fun useGameplayBackground(boardEnvironment: Boolean = false) {
        if (gameplayFinish && boardEnvironmentEnabled == boardEnvironment) return
        gameplayFinish = true
        boardEnvironmentEnabled = boardEnvironment
        applyGameplayBackdrop()
    }

    private fun applyGameplayBackdrop() {
        val environment = if (boardEnvironmentEnabled) GameplayBoardEnvironment.resolve(
            CosmeticInventoryStore(context).selectedId(CosmeticCategory.BOARD),
        ) else null
        if (environment != null && environmentBoardId == environment.boardId && background is GameplayBackdropDrawable) return
        environmentBoardId = environment?.boardId
        background = if (environment != null) GameplayBackdropDrawable(environment, 27f * resources.displayMetrics.density)
            else plywoodBackground(context, cornerRadiusDp = 27f, gameplayFinish = true)
    }

    /** Compatibility entry point used by setup and the rest of the UI. */
    fun useUiBackground() {
        if (gameplayFinish) {
            gameplayFinish = false
            boardEnvironmentEnabled = false
            environmentBoardId = null
            background = plywoodBackground(context, cornerRadiusDp = 27f)
        }
        applySharedBackdrop()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        preferences.registerOnSharedPreferenceChangeListener(inventoryListener)
        if (gameplayFinish && boardEnvironmentEnabled) applyGameplayBackdrop()
    }

    override fun onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(inventoryListener)
        removeCallbacks(refreshBackdrop)
        super.onDetachedFromWindow()
    }

    private fun applySharedBackdrop() {
        // Full-screen plain plywood with a 27dp corner radius (20% above the previous 22.5dp).
        // Reuse the same drawable when switching screens.
        if (background !is PlywoodTextureDrawable) {
            background = plywoodBackground(context, cornerRadiusDp = 27f)
        }
    }
}
