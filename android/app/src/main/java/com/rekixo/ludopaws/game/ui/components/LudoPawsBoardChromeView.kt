package com.rekixo.ludopaws

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import com.rekixo.ludopaws.feature.boards.domain.BoardRegistry
import com.rekixo.ludopaws.feature.boards.domain.BoardDefinition
import com.rekixo.ludopaws.feature.boards.rendering.BoardRendererRegistry
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import kotlin.math.min

/** Hosts the equipped board renderer; gameplay geometry stays in LudoBoardView. */
internal class LudoPawsBoardChromeView(context: Context) : View(context) {
    private var footprintSource: LudoBoardView? = null
    private var definition = BoardRegistry.resolve("board_classic")
    private var previewBoardThemeId: String? = null
    private var staticBoard: Bitmap? = null
    private var requestedKey: ArtworkKey? = null
    internal var artworkReady = false
        private set
    internal var onArtworkReadyChanged: ((Boolean) -> Unit)? = null

    private fun requestArtwork() {
        val pixels = min(width, height)
        if (pixels <= 0) return
        val key = ArtworkKey(pixels, resources.displayMetrics.density, definition)
        if (requestedKey == key) return
        requestedKey = key
        staticBoard = null
        artworkReady = false
        onArtworkReadyChanged?.invoke(false)
        cached(key)?.let { installArtwork(key, it); return }
        prepareArtwork(key) { bitmap ->
            post { if (requestedKey == key) installArtwork(key, bitmap) }
        }
    }

    private fun installArtwork(key: ArtworkKey, bitmap: Bitmap?) {
        if (requestedKey != key) return
        staticBoard = bitmap
        // A failed decoration bake must still release the playable base-board fallback.
        artworkReady = true
        invalidate()
        onArtworkReadyChanged?.invoke(true)
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reloadStyle()
        requestArtwork()
    }

    fun reloadStyle() {
        val next = BoardRegistry.resolve(previewBoardThemeId ?: CosmeticInventoryStore(context).selectedId(CosmeticCategory.BOARD))
        if (next != definition) {
            definition = next
            requestedKey = null
            requestArtwork()
        }
        invalidate()
    }

    internal fun setBoardThemeOverride(themeId: String) {
        require(BoardRegistry.find(themeId)?.offer?.contentAvailable == true)
        previewBoardThemeId = themeId
        reloadStyle()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cell = size / 15f
        staticBoard?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        // The base owns the four finish triangles and pawn coordinates. Joined
        // animated houses are rendered above them by the shared 3D scene.
        // These overlays must stay above opaque chrome, in authoritative board coordinates.
        footprintSource?.drawYardPawPrintsOverlay(canvas, cell)
        footprintSource?.drawFinishRanksOverlay(canvas, cell)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        requestArtwork()
    }

    override fun onDetachedFromWindow() {
        // Ignore an obsolete asynchronous result after this view leaves its host.
        requestedKey = null
        super.onDetachedFromWindow()
    }

    fun setFootprintSource(source: LudoBoardView) {
        if (footprintSource === source) return
        footprintSource = source
        invalidate()
    }
    private data class ArtworkKey(val pixels: Int, val density: Float, val definition: BoardDefinition)

    companion object {
        private val artworkLock = Any()
        private val completed = object : android.util.LruCache<ArtworkKey, Bitmap>(12 * 1024 * 1024) {
            override fun sizeOf(key: ArtworkKey, value: Bitmap): Int = value.byteCount
        }
        private val pending = mutableMapOf<ArtworkKey, MutableList<(Bitmap?) -> Unit>>()
        private val artworkWorker = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "LudoPawsBoardArtwork").apply { isDaemon = true }
        }

        private fun cached(key: ArtworkKey): Bitmap? = synchronized(artworkLock) { completed[key] }

        /** Precompose the equipped board while the player is still on Home. */
        fun prewarm(context: Context) {
            val metrics = context.resources.displayMetrics
            val contentWidth = if (LudoProofTheme.isExpandedWidth(context)) {
                minOf(metrics.widthPixels, LudoProofTheme.dp(context, LudoProofTheme.pageMaxContentWidthDp(context)))
            } else metrics.widthPixels
            val pixels = (contentWidth - LudoProofTheme.dp(context, 2)).coerceAtLeast(1)
            val definition = BoardRegistry.resolve(
                CosmeticInventoryStore(context).selectedId(CosmeticCategory.BOARD),
            )
            prepareArtwork(ArtworkKey(pixels, metrics.density, definition)) {}
        }

        private fun prepareArtwork(key: ArtworkKey, onReady: (Bitmap?) -> Unit) {
            synchronized(artworkLock) {
                completed[key]?.let { onReady(it); return }
                pending[key]?.let { it.add(onReady); return }
                pending[key] = mutableListOf(onReady)
            }
            val requestedAt = android.os.SystemClock.elapsedRealtime()
            val renderer = BoardRendererRegistry.create(key.definition)
            renderer.prepare {
                prepareHabitat(key.definition) {
                    artworkWorker.execute {
                        val result = runCatching {
                            val size = key.pixels.toFloat()
                            val cell = size / 15f
                            val density = key.density
                            val definition = key.definition
                            val artwork = Bitmap.createBitmap(key.pixels, key.pixels, Bitmap.Config.ARGB_8888)
                            val target = Canvas(artwork)
                            val yardHabitatRenderer = LudoPawsYardHabitatRenderer()
                            renderer.draw(target, size, density, definition)
                            if (definition.yardHabitatEnabled) yardHabitatRenderer.draw(target, cell)
                            artwork
                        }
                        val bitmap = result.getOrNull()
                        val callbacks = synchronized(artworkLock) {
                            if (bitmap != null) {
                                completed.put(key, bitmap)
                            }
                            pending.remove(key).orEmpty()
                        }
                        result.onFailure { android.util.Log.w("BoardArtwork", "Board bake failed", it) }
                        android.util.Log.d("LudoPawsLoad", "board=${key.definition.id} pixels=${key.pixels} ready_ms=${android.os.SystemClock.elapsedRealtime() - requestedAt} success=${bitmap != null}")
                        callbacks.forEach { it(bitmap) }
                    }
                }
            }
        }

        private fun prepareHabitat(definition: BoardDefinition, onReady: () -> Unit) {
            if (definition.yardHabitatEnabled) LudoPawsYardHabitatRenderer.prepare(onReady) else onReady()
        }
    }

}
