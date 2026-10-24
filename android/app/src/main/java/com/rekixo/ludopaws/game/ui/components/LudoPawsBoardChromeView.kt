package com.rekixo.ludopaws

import android.content.Context
import android.graphics.Bitmap
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

    private var staticBoard: Bitmap? = null
    private var cacheDensity = 0f

    private fun prepareArtwork() {
        renderer.prepare { post { clearArtworkCache() } }
        LudoPawsYardHabitatRenderer.prepare { post { clearArtworkCache() } }
    }

    private fun clearArtworkCache() {
        // Canvas may still reference the previous bitmap in a display list.
        staticBoard = null
        invalidate()
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reloadStyle()
        prepareArtwork()
    }

    fun reloadStyle() {
        val next = BoardRegistry.resolve(CosmeticInventoryStore(context).selectedId(CosmeticCategory.BOARD))
        if (next.rendererKey != definition.rendererKey) renderer = BoardRendererRegistry.create(next)
        if (next != definition) {
            definition = next
            clearArtworkCache()
            prepareArtwork()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cell = size / 15f
        val pixels = size.toInt()
        val density = resources.displayMetrics.density
        var artwork = staticBoard
        if (artwork == null || artwork.width != pixels || cacheDensity != density) {
            artwork = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
            val target = Canvas(artwork)
            renderer.draw(target, size, density, definition)
            yardHabitatRenderer.draw(target, cell)
            staticBoard = artwork
            cacheDensity = density
        }
        canvas.drawBitmap(artwork, 0f, 0f, null)
        // The base owns the four finish triangles and pawn coordinates. Joined
        // animated houses are rendered above them by the shared 3D scene.
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
