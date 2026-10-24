package com.rekixo.ludopaws.ui.quickchat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

/** Local transparent artwork; decode off the UI thread and stop when detached. */
internal class QuickChatAnimatedEmojiView(
    context: Context,
    private val emoji: String,
    private val animated: Boolean,
) : ImageView(context) {
    private var generation = 0
    private var disposed = false

    init {
        scaleType = ScaleType.FIT_CENTER
        contentDescription = emoji
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        disposed = false
        val ticket = ++generation
        val assets = context.applicationContext.assets
        decoder.execute {
            val result = runCatching {
                val source = ImageDecoder.createSource(assets, "quickchat/" + fileName(emoji))
                if (animated) {
                    ImageDecoder.decodeDrawable(source) { decoder, _, _ ->
                        decoder.setTargetSize(192, 192)
                    }
                } else {
                    val cached = previews.get(emoji)
                    val bitmap = cached ?: ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.setTargetSize(96, 96)
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }.also { previews.put(emoji, it) }
                    BitmapDrawable(resources, bitmap)
                }
            }.getOrNull()
            main.post {
                if (disposed || ticket != generation || !isAttachedToWindow) {
                    (result as? AnimatedImageDrawable)?.stop()
                    return@post
                }
                setImageDrawable(result)
                if (animated) (result as? AnimatedImageDrawable)?.apply {
                    repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    if (windowVisibility == VISIBLE) start()
                }
            }
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        (drawable as? AnimatedImageDrawable)?.let {
            if (visibility == VISIBLE && animated && !disposed) it.start() else it.stop()
        }
    }

    fun dispose() {
        disposed = true
        generation++
        animate().cancel()
        (drawable as? AnimatedImageDrawable)?.stop()
        setImageDrawable(null)
    }

    override fun onDetachedFromWindow() {
        dispose()
        super.onDetachedFromWindow()
    }

    companion object {
        private val decoder = Executors.newFixedThreadPool(2)
        private val main = Handler(Looper.getMainLooper())
        private val previews = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }

        private fun fileName(emoji: String): String =
            emoji.codePoints().toArray().filter { it != 0xFE0F }
                .joinToString("_") { it.toString(16) } + ".webp"
    }
}
