package com.rekixo.ludopaws

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.rekixo.ludopaws.feature.boards.domain.BoardRegistry
import com.rekixo.ludopaws.feature.boards.rendering.BoardRendererRegistry
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import kotlin.math.min

/** Hosts the equipped board renderer; gameplay geometry stays in LudoBoardView. */
internal class LudoPawsBoardChromeView(context: Context) : View(context) {
    private var footprintSource: LudoBoardView? = null
    private var definition = BoardRegistry.resolve("board_classic")
    private var renderer = BoardRendererRegistry.create(definition)
    // Identical code-drawn homes with team-color palettes; road and center untouched.
    private val yardHabitatRenderer = LudoPawsYardHabitatRenderer()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reloadStyle()
    }

    fun reloadStyle() {
        val next = BoardRegistry.resolve(CosmeticInventoryStore(context).selectedId(CosmeticCategory.BOARD))
        if (next.rendererKey != definition.rendererKey) renderer = BoardRendererRegistry.create(next)
        definition = next
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cell = size / 15f
        renderer.draw(canvas, size, resources.displayMetrics.density, definition)
        // Four full 5x5 color homes: same illustrated layout, four team tints.
        // Overdraw old yard backgrounds, below prints and actual 3D pawns.
        yardHabitatRenderer.draw(canvas, cell)
        // These overlays must stay above opaque chrome, in authoritative board coordinates.
        footprintSource?.drawYardPawPrintsOverlay(canvas, cell)
        footprintSource?.drawFinishRanksOverlay(canvas, cell)
    }

    fun setFootprintSource(source: LudoBoardView) {
        if (footprintSource === source) return
        footprintSource = source
        invalidate()
    }
}
