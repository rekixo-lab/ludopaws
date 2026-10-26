package com.rekixo.ludopaws.feature.store.presentation.components

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.View
import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.LudoPaws3DSceneRenderer
import com.rekixo.ludopaws.feature.characters.rendering.CharacterHopPlayback
import com.rekixo.ludopaws.feature.characters.rendering.CharacterMotion
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.characters.shared.LudoPaws3DLifeMotion
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal class FeaturedPawsPreview(context: Context) : GLSurfaceView(context) {
    @Volatile var characterId = "dog"
    @Volatile private var animated = true
    private var active = false
    private var running = true
    private var failed = false
    private val visibleRect = Rect()
    var onReady: (() -> Unit)? = null
    var onUnavailable: (() -> Unit)? = null
    private val ticker = object : Runnable {
        override fun run() {
            if (!active || !isAttachedToWindow || failed) return
            val visible = isShown && hasWindowFocus() && windowVisibility == View.VISIBLE &&
                getGlobalVisibleRect(visibleRect) && visibleRect.height() >= height / 2
            setRunning(visible)
            if (visible) requestRender()
            postDelayed(this, if (visible && animated) 33L else 250L)
        }
    }

    init {
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(true)
        setRenderer(object : GLSurfaceView.Renderer {
            private var scene: LudoPaws3DSceneRenderer? = null
            private var previousId = ""
            private var started = 0L
            private var life = LudoPaws3DLifeMotion()
            private var notified = false

            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                try {
                    scene = LudoPaws3DSceneRenderer().also { it.onPortraitSurfaceCreated() }
                    GLES30.glClearColor(0f, 0f, 0f, 0f)
                    previousId = ""
                    notified = false
                } catch (error: RuntimeException) {
                    unavailable(error)
                }
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                try {
                    scene?.onPortraitSurfaceChanged(width, height)
                } catch (error: RuntimeException) {
                    scene = null
                    unavailable(error)
                }
            }

            override fun onDrawFrame(gl: GL10?) {
                val renderer = scene ?: return
                try {
                    val id = characterId
                    val now = SystemClock.uptimeMillis()
                    if (id != previousId) {
                        previousId = id
                        started = now
                        life = LudoPaws3DLifeMotion()
                    }
                    val elapsed = if (animated) now - started else 0L
                    val character = CharacterRegistry.resolve(id)
                    val cycle = elapsed % 12000L
                    val motion = when {
                        cycle < 8000L -> CharacterMotion.IDLE
                        cycle < 9500L -> CharacterMotion.HOP
                        else -> CharacterMotion.HOME
                    }
                    val duration = when (motion) {
                        CharacterMotion.IDLE -> character.definition.idleDurationMillis
                        CharacterMotion.HOP -> 1500L
                        CharacterMotion.HOME -> character.definition.homeDurationMillis
                    }.coerceAtLeast(1L)
                    val motionElapsed = when (motion) {
                        CharacterMotion.IDLE -> cycle
                        CharacterMotion.HOP -> cycle - 8000L
                        CharacterMotion.HOME -> cycle - 9500L
                    }
                    life.update(10000L + elapsed, 37, motion == CharacterMotion.IDLE,
                        0f, 0f, LudoPaws3DReactionPose())
                    val sampled = character.renderer.sample(motion, (motionElapsed % duration) / duration.toFloat(),
                        CharacterHopPlayback.ISOLATED)
                    val pose = if (motion == CharacterMotion.IDLE) life.idlePose(sampled) else sampled
                    renderer.drawPortrait(id, "BLUE", pose, life, previewZoom = .9f, synchronizeGpu = false)
                    if (!notified) {
                        notified = true
                        post { if (!failed) onReady?.invoke() }
                    }
                } catch (error: RuntimeException) {
                    scene = null
                    unavailable(error)
                }
            }
        })
        renderMode = RENDERMODE_WHEN_DIRTY
        setRunning(false)
    }

    private fun unavailable(error: RuntimeException) {
        android.util.Log.w("FeaturedPaws", "Live preview unavailable", error)
        post {
            failed = true
            setActive(false)
            visibility = View.INVISIBLE
            onUnavailable?.invoke()
        }
    }

    fun setActive(value: Boolean) {
        active = value
        animated = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
            android.animation.ValueAnimator.areAnimatorsEnabled()
        removeCallbacks(ticker)
        if (value && !failed) post(ticker) else setRunning(false)
    }

    private fun setRunning(value: Boolean) {
        if (running == value) return
        running = value
        if (value) onResume() else onPause()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (active && !failed) post(ticker)
    }

    override fun onDetachedFromWindow() {
        setActive(false)
        super.onDetachedFromWindow()
    }
}
