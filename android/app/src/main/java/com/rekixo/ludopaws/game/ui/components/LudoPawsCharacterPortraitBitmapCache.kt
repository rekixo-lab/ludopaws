package com.rekixo.ludopaws

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.EGLExt
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/**
 * Produces reusable transparent portraits from the same OpenGL ES geometry used
 * by live board pawns. One serialized pbuffer context renders each character /
 * team-colour pair once; UI avatars then draw the cached bitmap without running
 * their own render loops.
 */
internal object LudoPawsCharacterPortraitBitmapCache {
    private const val TAG = "LudoPawsPortrait"
    private const val PORTRAIT_SIZE = 256
    private const val MAX_CACHE_BYTES = 8 * 1024 * 1024

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "LudoPawsPortraitRenderer").apply {
                isDaemon = true
            }
        }
    private val cache =
        object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                value.byteCount
        }
    private val pendingCallbacks =
        HashMap<String, MutableList<(Bitmap?) -> Unit>>()

    // These objects are created and used only on the single portrait worker.
    private var eglSession: OffscreenEglSession? = null
    private var renderer: LudoPaws3DSceneRenderer? = null

    @Volatile
    private var rendererUnavailable = false

    fun keyFor(
        characterId: String?,
        teamColor: String?,
    ): String =
        "${resolveCharacterId(characterId)}:${resolveTeamColor(teamColor)}"

    fun request(
        characterId: String?,
        teamColor: String?,
        callback: (Bitmap?) -> Unit,
    ) {
        val resolvedCharacterId = resolveCharacterId(characterId)
        val resolvedTeamColor = resolveTeamColor(teamColor)
        val key = keyFor(resolvedCharacterId, resolvedTeamColor)

        var cached: Bitmap? = null
        var shouldRender = false
        var unavailable = false
        synchronized(lock) {
            cached = cache.get(key)
            if (cached == null) {
                if (rendererUnavailable) {
                    unavailable = true
                } else {
                    val waiters = pendingCallbacks[key]
                    if (waiters != null) {
                        waiters += callback
                    } else {
                        pendingCallbacks[key] = mutableListOf(callback)
                        shouldRender = true
                    }
                }
            }
        }

        cached?.let { bitmap ->
            // Player rails rebind on every turn. A posted cache hit leaves a
            // newly attached avatar on vector fallback for one visible frame.
            // Deliver an already-rendered portrait synchronously on the UI thread.
            if (Looper.myLooper() == Looper.getMainLooper()) {
                callback(bitmap)
            } else {
                mainHandler.post { callback(bitmap) }
            }
            return
        }
        if (unavailable) {
            mainHandler.post { callback(null) }
            return
        }
        if (!shouldRender) return

        executor.execute {
            val bitmap =
                if (rendererUnavailable) {
                    null
                } else {
                    try {
                        render(resolvedCharacterId, resolvedTeamColor)
                    } catch (error: Throwable) {
                        rendererUnavailable = true
                        Log.w(TAG, "3D portrait rendering unavailable; retaining vector fallback", error)
                        null
                    }
                }

            val callbacks =
                synchronized(lock) {
                    if (bitmap != null) {
                        cache.put(key, bitmap)
                    }
                    pendingCallbacks.remove(key).orEmpty()
                }
            mainHandler.post {
                callbacks.forEach { listener ->
                    runCatching { listener(bitmap) }
                }
            }
        }
    }

    private fun resolveCharacterId(characterId: String?): String =
        LudoPawsCharacterCatalog
            .canonicalCharacterId(characterId)
            ?.takeIf { LudoPawsCharacterCatalog.character(it) != null }
            ?: LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID

    private fun resolveTeamColor(teamColor: String?): String =
        LudoPawsTeamIdentityPolicy
            .resolve(teamColor)
            ?.wireName
            ?: LudoPawsTeamColor.BLUE.wireName

    private fun render(
        characterId: String,
        teamColor: String,
    ): Bitmap? {
        val activeRenderer =
            renderer
                ?: run {
                    // Lazily create one ES3 pbuffer on the portrait worker and
                    // reuse that context for every subsequent cache miss.
                    eglSession =
                        OffscreenEglSession.create(PORTRAIT_SIZE)
                    LudoPaws3DSceneRenderer().also {
                        it.onPortraitSurfaceCreated()
                        it.onPortraitSurfaceChanged(
                            PORTRAIT_SIZE,
                            PORTRAIT_SIZE,
                        )
                        renderer = it
                    }
                }

        activeRenderer.onPortraitSurfaceChanged(
            PORTRAIT_SIZE,
            PORTRAIT_SIZE,
        )
        activeRenderer.drawPortrait(
            characterId = characterId,
            teamColorName = teamColor,
        )
        val drawError = GLES30.glGetError()
        check(drawError == GLES30.GL_NO_ERROR) {
            "3D portrait draw failed for $characterId (0x" + drawError.toString(16) + ")"
        }

        val rgba =
            ByteBuffer
                .allocateDirect(PORTRAIT_SIZE * PORTRAIT_SIZE * 4)
                .order(ByteOrder.nativeOrder())
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
        GLES30.glReadPixels(
            0,
            0,
            PORTRAIT_SIZE,
            PORTRAIT_SIZE,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            rgba,
        )
        val glError = GLES30.glGetError()
        check(glError == GLES30.GL_NO_ERROR) {
            "glReadPixels failed for character portrait (0x${glError.toString(16)})"
        }

        val argb = IntArray(PORTRAIT_SIZE * PORTRAIT_SIZE)
        var paintedPixels = 0
        for (y in 0 until PORTRAIT_SIZE) {
            // OpenGL pixel buffers start at the bottom-left; Android bitmaps
            // start at the top-left, so flip rows while converting RGBA to ARGB.
            val sourceY = PORTRAIT_SIZE - 1 - y
            for (x in 0 until PORTRAIT_SIZE) {
                val sourceOffset = (sourceY * PORTRAIT_SIZE + x) * 4
                val red = rgba.get(sourceOffset).toInt() and 0xFF
                val green = rgba.get(sourceOffset + 1).toInt() and 0xFF
                val blue = rgba.get(sourceOffset + 2).toInt() and 0xFF
                val alpha = rgba.get(sourceOffset + 3).toInt() and 0xFF
                if (alpha > 16) paintedPixels++
                argb[y * PORTRAIT_SIZE + x] =
                    (alpha shl 24) or
                        (red shl 16) or
                        (green shl 8) or
                        blue
            }
        }

        // A successful GL call that produced a blank canvas must not replace
        // the known-good vector fallback.
        if (paintedPixels < 128) return null
        return Bitmap.createBitmap(
            argb,
            PORTRAIT_SIZE,
            PORTRAIT_SIZE,
            Bitmap.Config.ARGB_8888,
        )
    }

    private class OffscreenEglSession private constructor(
        private val display: EGLDisplay,
        private val context: EGLContext,
        private val surface: EGLSurface,
    ) {
        companion object {
            fun create(size: Int): OffscreenEglSession {
                var display = EGL14.EGL_NO_DISPLAY
                var context = EGL14.EGL_NO_CONTEXT
                var surface = EGL14.EGL_NO_SURFACE
                var initialized = false

                try {
                    display =
                        EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                    check(display != EGL14.EGL_NO_DISPLAY) {
                        "No EGL display for character portraits"
                    }
                    val versions = IntArray(2)
                    check(
                        EGL14.eglInitialize(
                            display,
                            versions,
                            0,
                            versions,
                            1,
                        ),
                    ) { "EGL initialize failed for character portraits" }
                    initialized = true

                    val configAttributes =
                        intArrayOf(
                            EGL14.EGL_SURFACE_TYPE,
                            EGL14.EGL_PBUFFER_BIT,
                            EGL14.EGL_RENDERABLE_TYPE,
                            EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                            EGL14.EGL_RED_SIZE,
                            8,
                            EGL14.EGL_GREEN_SIZE,
                            8,
                            EGL14.EGL_BLUE_SIZE,
                            8,
                            EGL14.EGL_ALPHA_SIZE,
                            8,
                            EGL14.EGL_DEPTH_SIZE,
                            16,
                            EGL14.EGL_NONE,
                        )
                    val configs = arrayOfNulls<EGLConfig>(1)
                    val count = IntArray(1)
                    check(
                        EGL14.eglChooseConfig(
                            display,
                            configAttributes,
                            0,
                            configs,
                            0,
                            configs.size,
                            count,
                            0,
                        ) && count[0] > 0,
                    ) { "No ES3 RGBA pbuffer config for character portraits" }
                    val config = checkNotNull(configs[0])

                    context =
                        EGL14.eglCreateContext(
                            display,
                            config,
                            EGL14.EGL_NO_CONTEXT,
                            intArrayOf(
                                EGL14.EGL_CONTEXT_CLIENT_VERSION,
                                3,
                                EGL14.EGL_NONE,
                            ),
                            0,
                        )
                    check(context != EGL14.EGL_NO_CONTEXT) {
                        "ES3 EGL context creation failed for character portraits"
                    }

                    surface =
                        EGL14.eglCreatePbufferSurface(
                            display,
                            config,
                            intArrayOf(
                                EGL14.EGL_WIDTH,
                                size,
                                EGL14.EGL_HEIGHT,
                                size,
                                EGL14.EGL_NONE,
                            ),
                            0,
                        )
                    check(surface != EGL14.EGL_NO_SURFACE) {
                        "EGL pbuffer creation failed for character portraits"
                    }
                    check(
                        EGL14.eglMakeCurrent(
                            display,
                            surface,
                            surface,
                            context,
                        ),
                    ) { "EGL makeCurrent failed for character portraits" }

                    return OffscreenEglSession(display, context, surface)
                } catch (error: Throwable) {
                    if (display != EGL14.EGL_NO_DISPLAY) {
                        EGL14.eglMakeCurrent(
                            display,
                            EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_CONTEXT,
                        )
                        if (surface != EGL14.EGL_NO_SURFACE) {
                            EGL14.eglDestroySurface(display, surface)
                        }
                        if (context != EGL14.EGL_NO_CONTEXT) {
                            EGL14.eglDestroyContext(display, context)
                        }
                        if (initialized) {
                            EGL14.eglTerminate(display)
                        }
                    }
                    throw error
                }
            }
        }
    }
}
