package com.rekixo.ludopaws.feature.store.presentation.components

import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLExt
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.TextureView
import android.view.View
import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.LudoPaws3DSceneRenderer
import com.rekixo.ludopaws.feature.characters.rendering.CharacterHopPlayback
import com.rekixo.ludopaws.feature.characters.rendering.CharacterMotion
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.characters.shared.LudoPaws3DLifeMotion
import java.util.concurrent.atomic.AtomicBoolean

internal class FeaturedPawsPreview(
    context: Context,
    private val frameIntervalMillis: Long = 33L,
    private val playful: Boolean = false,
    private val prominent: Boolean = false,
) : TextureView(context), TextureView.SurfaceTextureListener {
    var characterId = "dog"
        set(value) {
            if (field != value) {
                field = value
                needsFrame = true
            }
        }
    private var animated = true
    private var active = false
    private var failed = false
    private var needsFrame = true
    private var session: Session? = null
    private val visibleRect = Rect()
    var onReady: (() -> Unit)? = null
    var onUnavailable: (() -> Unit)? = null
    private val ticker = object : Runnable {
        override fun run() {
            if (!active || !isAttachedToWindow || failed) return
            val visible = isShown && hasWindowFocus() && windowVisibility == View.VISIBLE &&
                getGlobalVisibleRect(visibleRect) && visibleRect.height() >= height / 2
            if (visible && (animated || needsFrame)) {
                if (session?.draw(characterId, animated, needsFrame) == true) needsFrame = false
            }
            postDelayed(this, if (visible && animated) frameIntervalMillis.coerceAtLeast(33L) else 250L)
        }
    }

    init {
        isOpaque = false
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        surfaceTextureListener = this
    }

    fun setActive(value: Boolean) {
        active = value
        needsFrame = true
        animated = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
            android.animation.ValueAnimator.areAnimatorsEnabled()
        removeCallbacks(ticker)
        if (value && !failed && isAttachedToWindow) post(ticker)
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        session = Session(texture, width, height)
        needsFrame = true
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        session?.resize(width, height)
        needsFrame = true
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        val previous = session
        session = null
        if (previous != null) previous.close() else texture.release()
        needsFrame = true
        return false
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (active && !failed) {
            removeCallbacks(ticker)
            post(ticker)
        }
    }

    override fun onDetachedFromWindow() {
        setActive(false)
        super.onDetachedFromWindow()
    }

    private inner class Session(private val texture: SurfaceTexture, width: Int, height: Int) {
        private val thread = HandlerThread("LudoPawsPromotion").apply { start() }
        private val handler = Handler(thread.looper)
        private val closed = AtomicBoolean(false)
        private val pending = AtomicBoolean(false)
        private var window: EglWindow? = null
        private var renderer: LudoPaws3DSceneRenderer? = null
        private var previousId = ""
        private var elapsed = 0L
        private var lastFrame = 0L
        private var life = LudoPaws3DLifeMotion()

        init {
            handler.post {
                if (closed.get()) return@post
                try {
                    val egl = EglWindow()
                    window = egl
                    egl.open(texture)
                    renderer = LudoPaws3DSceneRenderer().also {
                        it.onPortraitSurfaceCreated()
                        it.onPortraitSurfaceChanged(width.coerceAtLeast(1), height.coerceAtLeast(1))
                    }
                } catch (error: RuntimeException) {
                    fail(error)
                }
            }
        }

        fun draw(id: String, animate: Boolean, notifyReady: Boolean): Boolean {
            if (closed.get() || !pending.compareAndSet(false, true)) return false
            handler.post {
                try {
                    if (closed.get()) return@post
                    val scene = renderer ?: return@post
                    val now = SystemClock.uptimeMillis()
                    if (previousId != id) {
                        if (previousId.isNotEmpty()) scene.clearPortraitMeshes()
                        previousId = id
                        elapsed = 0L
                        life = LudoPaws3DLifeMotion()
                        lastFrame = 0L
                    }
                    if (animate && lastFrame > 0L && now - lastFrame < 250L) elapsed += now - lastFrame
                    lastFrame = now
                    val clock = if (animate) elapsed else 0L
                    val character = CharacterRegistry.resolve(id)
                    val idleEnd = if (playful) 3000L else 8000L
                    val hopEnd = idleEnd + 1500L
                    val cycle = clock % if (playful) 7000L else 12000L
                    val motion = when {
                        cycle < idleEnd -> CharacterMotion.IDLE
                        cycle < hopEnd -> CharacterMotion.HOP
                        else -> CharacterMotion.HOME
                    }
                    val duration = when (motion) {
                        CharacterMotion.IDLE -> character.definition.idleDurationMillis
                        CharacterMotion.HOP -> 1500L
                        CharacterMotion.HOME -> character.definition.homeDurationMillis
                    }.coerceAtLeast(1L)
                    val phase = when (motion) {
                        CharacterMotion.IDLE -> cycle
                        CharacterMotion.HOP -> cycle - idleEnd
                        CharacterMotion.HOME -> cycle - hopEnd
                    }
                    life.update(10000L + clock, 37, motion == CharacterMotion.IDLE,
                        0f, 0f, LudoPaws3DReactionPose())
                    val sampled = character.renderer.sample(motion, (phase % duration) / duration.toFloat(),
                        CharacterHopPlayback.ISOLATED)
                    val pose = if (motion == CharacterMotion.IDLE) life.idlePose(sampled) else sampled
                    // Reserve headroom in the GL viewport itself: allowing
                    // parent overflow cannot recover pixels clipped by GL.
                    val tall = com.rekixo.ludopaws.LudoPawsPawnLayout.silhouetteHeight(id) >= 3.2f
                    val zoom = if (prominent) { if (tall) .68f else .78f }
                        else if (com.rekixo.ludopaws.LudoPawsPawnLayout.silhouetteHeight(id) >= 2.8f) .6f else .72f
                    val framedPose = if (prominent) pose.copy(liftY = pose.liftY * .4f) else pose
                    scene.drawPortrait(id, "BLUE", framedPose, life, previewZoom = zoom,
                        synchronizeGpu = false, verticalAnchor = if (prominent) .70f else .69f)
                    check(window?.swap() == true) { "Showcase buffer swap failed" }
                    if (notifyReady) post {
                        if (session === this && active && characterId == id && !failed) onReady?.invoke()
                    }
                } catch (error: RuntimeException) {
                    fail(error)
                } finally {
                    pending.set(false)
                }
            }
            return true
        }

        fun resize(width: Int, height: Int) {
            handler.post {
                if (closed.get()) return@post
                try {
                    renderer?.onPortraitSurfaceChanged(width.coerceAtLeast(1), height.coerceAtLeast(1))
                } catch (error: RuntimeException) {
                    fail(error)
                }
            }
        }

        private fun fail(error: RuntimeException) {
            android.util.Log.w("FeaturedPaws", "Live preview unavailable", error)
            post {
                if (session === this) {
                    failed = true
                    setActive(false)
                    visibility = View.INVISIBLE
                    onUnavailable?.invoke()
                }
            }
            release()
        }

        private fun release() {
            try {
                runCatching { renderer?.release() }
                    .onFailure { android.util.Log.w("FeaturedPaws", "Preview cleanup failed", it) }
            } finally {
                renderer = null
                window?.release()
                window = null
            }
        }

        fun close() {
            if (!closed.compareAndSet(false, true)) return
            handler.post {
                try {
                    release()
                } finally {
                    texture.release()
                    thread.quitSafely()
                }
            }
        }
    }

    private class EglWindow {
        private var display = EGL14.EGL_NO_DISPLAY
        private var context = EGL14.EGL_NO_CONTEXT
        private var surface = EGL14.EGL_NO_SURFACE
        private var initialized = false

        fun open(texture: SurfaceTexture) {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "EGL initialization failed" }
            initialized = true
            val attributes = intArrayOf(EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_NONE)
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
                "No ES3 showcase configuration"
            }
            val config = checkNotNull(configs[0])
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT) { "Showcase context creation failed" }
            surface = EGL14.eglCreateWindowSurface(display, config, texture, intArrayOf(EGL14.EGL_NONE), 0)
            check(surface != EGL14.EGL_NO_SURFACE) { "Showcase surface creation failed" }
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "Showcase makeCurrent failed" }
            EGL14.eglSwapInterval(display, 1)
        }

        fun swap(): Boolean = EGL14.eglSwapBuffers(display, surface)

        fun release() {
            if (!initialized) return
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            EGL14.eglReleaseThread()
            initialized = false
        }
    }
}
