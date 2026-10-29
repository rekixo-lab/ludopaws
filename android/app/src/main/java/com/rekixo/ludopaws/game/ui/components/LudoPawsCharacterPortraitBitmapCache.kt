package com.rekixo.ludopaws

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Context
import android.util.AtomicFile
import java.io.File
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
 * natural-material portrait once; UI avatars draw the cached bitmap without running
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

    // Revision changes when portrait geometry/material framing changes.
    private const val DISK_REVISION = "portraits-v1"
    @Volatile private var appContext: Context? = null
    private val failedUntil = HashMap<String, Long>()
    private var diskDirectoryName = DISK_REVISION
    private var diskPrepared = false // portrait-worker only

    @Synchronized fun initialize(context: Context) {
        if (appContext != null) return
        val application = context.applicationContext
        val source = runCatching {
            @Suppress("DEPRECATION")
            application.packageManager.getPackageInfo(application.packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
        diskDirectoryName = "$DISK_REVISION-$source"
        appContext = application
    }

    fun peek(characterId: String?, teamColor: String?): Bitmap? = synchronized(lock) {
        cache.get(keyFor(characterId, teamColor))
    }

    fun prewarm(context: Context, ids: List<String>) {
        initialize(context)
        ids.distinct().forEach { id -> request(id, null) {} }
    }

    private fun portraitFile(id: String): File? = appContext?.let { app ->
        if (!diskPrepared) {
            // Only derived portrait-cache folders are touched. An app update
            // invalidates old model images even if its versionCode is reused.
            app.cacheDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("$DISK_REVISION-") && it.name != diskDirectoryName }
                ?.forEach { runCatching { it.deleteRecursively() } }
            diskPrepared = true
        }
        File(File(app.cacheDir, diskDirectoryName), "$id.png")
    }

    private fun readPortrait(id: String): Bitmap? {
        val file = portraitFile(id) ?: return null
        if (!file.isFile) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() ?: return null
        if (bitmap.width == PORTRAIT_SIZE && bitmap.height == PORTRAIT_SIZE) return bitmap
        bitmap.recycle()
        return null
    }

    private fun savePortrait(id: String, bitmap: Bitmap) {
        val file = portraitFile(id) ?: return
        if (file.parentFile?.isDirectory != true && file.parentFile?.mkdirs() != true) return
        val atomic = AtomicFile(file)
        var output: java.io.FileOutputStream? = null
        try {
            output = atomic.startWrite()
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            Log.w(TAG, "Could not persist the rendered portrait for $id", error)
        }
    }

    fun keyFor(
        characterId: String?,
        teamColor: String?,
    ): String =
        "${resolveCharacterId(characterId)}:natural"

    fun request(
        characterId: String?,
        teamColor: String?,
        callback: (Bitmap?) -> Unit,
    ) {
        val resolvedCharacterId = resolveCharacterId(characterId)
        val resolvedTeamColor = LudoPawsTeamColor.BLUE.wireName
        val key = keyFor(resolvedCharacterId, resolvedTeamColor)

        var cached: Bitmap? = null
        var shouldRender = false
        var unavailable = false
        synchronized(lock) {
            cached = cache.get(key)
            if (cached == null) {
                if ((failedUntil[key] ?: 0L) > android.os.SystemClock.elapsedRealtime()) {
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
            // newly attached avatar waiting for one visible frame.
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

        val requestedAt = android.os.SystemClock.elapsedRealtime()
        executor.execute {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            val bitmap = try {
                runCatching { readPortrait(resolvedCharacterId) }.getOrNull()
                    ?: render(resolvedCharacterId, resolvedTeamColor)?.also { portrait ->
                        runCatching { savePortrait(resolvedCharacterId, portrait) }
                            .onFailure { Log.w(TAG, "Portrait cache write skipped for $resolvedCharacterId", it) }
                    }
            } catch (error: Exception) {
                runCatching { renderer?.clearPortraitMeshes() }
                Log.w(TAG, "Actual 3D portrait unavailable for $resolvedCharacterId; no substitute art will be shown", error)
                null
            }

            val callbacks =
                synchronized(lock) {
                    if (bitmap != null) {
                        cache.put(key, bitmap)
                        failedUntil.remove(key)
                    } else {
                        // One bad model/context attempt must not disable every
                        // other character for the rest of the app process.
                        failedUntil[key] = android.os.SystemClock.elapsedRealtime() + 15_000L
                    }
                    pendingCallbacks.remove(key).orEmpty()
                }
            Log.d("LudoPawsLoad", "portrait=$resolvedCharacterId queue_ms=${startedAt - requestedAt} render_ms=${android.os.SystemClock.elapsedRealtime() - startedAt} success=${bitmap != null}")
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

        // Readback is finished; no need to retain every animal's GPU meshes
        // when warming the whole catalog. The bitmap is the reusable result.
        activeRenderer.clearPortraitMeshes()
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
        // an actual character portrait.
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
