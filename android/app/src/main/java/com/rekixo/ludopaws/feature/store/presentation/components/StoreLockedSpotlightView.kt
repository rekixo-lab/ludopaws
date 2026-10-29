package com.rekixo.ludopaws.feature.store.presentation.components

import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.rekixo.ludopaws.feature.boards.domain.BoardRegistry
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.dice.domain.DiceRegistry
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic
import com.rekixo.ludopaws.feature.store.presentation.StoreActivity

/** Rotates only shipped, unowned items in the current Shop category. */
internal class StoreLockedSpotlightView(
    private val store: StoreActivity,
    private val category: CosmeticCategory,
    initialId: String?,
    private val onFocused: (String) -> Unit,
) : LinearLayout(store) {
    private val body = FrameLayout(store)
    private var currentId = initialId
    private var active = false
    private val bounds = Rect()
    private val cycle = object : Runnable {
        override fun run() {
            if (!active || !isAttachedToWindow) return
            if (hasWindowFocus() && isShown && getGlobalVisibleRect(bounds) && bounds.height() >= height / 2 &&
                ValueAnimator.areAnimatorsEnabled() && offers().size > 1) show(1)
            schedule()
        }
    }

    init {
        orientation = VERTICAL
        addView(body, LayoutParams(-1, -2))
        show()
    }

    private fun offers(): List<StoreCosmetic> = StoreCosmeticCatalog.all.filter { item ->
        item.category == category && item.contentAvailable && !store.cosmetics.isOwned(item.id) && when (category) {
            CosmeticCategory.BOARD -> BoardRegistry.find(item.id) != null
            CosmeticCategory.DICE -> DiceRegistry.find(item.id) != null
            CosmeticCategory.CHARACTER_PACK -> CharacterDefinitions.all.any {
                StoreCosmeticCatalog.characterUnlock(it.character.id)?.id == item.id && CharacterRegistry.find(it.character.id) != null
            }
            else -> false
        }
    }

    private fun show(step: Int = 0) {
        val items = offers()
        visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        if (items.isEmpty()) { body.removeAllViews(); return }
        val index = items.indexOfFirst { it.id == currentId }
        val selected = if (index < 0) 0 else (index + step + items.size) % items.size
        val item = items[selected]
        currentId = item.id
        onFocused(item.id)
        body.removeAllViews()
        body.addView(store.promotedItemCard(item, compact = true), FrameLayout.LayoutParams(-1, -2))
    }

    private fun schedule() {
        removeCallbacks(cycle)
        if (active && isAttachedToWindow && offers().size > 1) postDelayed(cycle, 7000L)
    }
    fun setActive(value: Boolean) { active = value; schedule() }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) removeCallbacks(cycle)
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) schedule()
        return handled
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); schedule() }
    override fun onDetachedFromWindow() { removeCallbacks(cycle); super.onDetachedFromWindow() }
}
