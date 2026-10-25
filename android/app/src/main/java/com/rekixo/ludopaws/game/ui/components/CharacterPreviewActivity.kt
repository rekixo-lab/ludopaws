package com.rekixo.ludopaws

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.LinearLayout
import android.widget.TextView
import com.rekixo.ludopaws.feature.characters.rendering.CharacterHopPlayback
import com.rekixo.ludopaws.feature.characters.rendering.CharacterMotion
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.characters.shared.LudoPaws3DLifeMotion
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

/** Isolated live viewer: no match, roll, token or network state is changed here. */
class CharacterPreviewActivity : Activity() {
    private lateinit var preview: CharacterPreviewSurface
    private lateinit var title: TextView
    private lateinit var pauseButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LudoProofTheme.configureWindow(this)
        preview = CharacterPreviewSurface(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF131C24.toInt())
            setPadding(dp(12), dp(16), dp(12), dp(16))
            setOnApplyWindowInsetsListener { view, insets ->
                val cutout = insets.displayCutout
                view.setPadding(dp(12) + (cutout?.safeInsetLeft ?: 0),
                    maxOf(dp(16), cutout?.safeInsetTop ?: 0),
                    dp(12) + (cutout?.safeInsetRight ?: 0),
                    maxOf(dp(16), cutout?.safeInsetBottom ?: 0))
                insets
            }
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(button("Close") { finish() })
        title = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(56), 1f))
        root.addView(header)
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "Drag to rotate · Pinch to zoom"
            setTextColor(0xFFB9CADA.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        })
        val characters = LinearLayout(this)
        characters.addView(button("‹ Previous") { selectRelative(-1) }, weighted())
        characters.addView(button("Next ›") { selectRelative(1) }, weighted())
        root.addView(characters)
        val actions = LinearLayout(this)
        pauseButton = button("Pause") {
            preview.state = preview.state.copy(paused = !preview.state.paused)
            updateLabels()
        }
        actions.addView(pauseButton, weighted())
        actions.addView(button("Idle") { selectMotion(CharacterMotion.IDLE) }, weighted())
        actions.addView(button("Hop") { selectMotion(CharacterMotion.HOP) }, weighted())
        actions.addView(button("Celebrate") { selectMotion(CharacterMotion.HOME) }, weighted())
        root.addView(actions)
        setContentView(root)
        if (savedInstanceState == null) {
            readCommand(intent)
        } else {
            preview.state = PreviewState(
                characterId = CharacterRegistry.resolve(savedInstanceState.getString("character_id")).id,
                motion = motionFrom(savedInstanceState.getString("animation")),
                yaw = savedInstanceState.getFloat("yaw"),
                zoom = savedInstanceState.getFloat("zoom", 1f).coerceIn(.6f, 1.5f),
                paused = savedInstanceState.getBoolean("paused"),
            )
            updateLabels()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readCommand(intent)
    }

    private fun readCommand(command: Intent) {
        preview.state = PreviewState(
            characterId = CharacterRegistry.find(command.getStringExtra("character_id"))?.id ?: "goat_lumi",
            motion = motionFrom(command.getStringExtra("animation")),
        )
        updateLabels()
    }

    private fun motionFrom(value: String?): CharacterMotion = when (value?.lowercase()) {
        "hop" -> CharacterMotion.HOP
        "home", "celebrate" -> CharacterMotion.HOME
        else -> CharacterMotion.IDLE
    }

    private fun selectRelative(step: Int) {
        val all = CharacterRegistry.all
        val index = all.indexOfFirst { it.id == preview.state.characterId }
        preview.state = preview.state.copy(characterId = all[(index + step).mod(all.size)].id)
        updateLabels()
    }

    private fun selectMotion(motion: CharacterMotion) {
        preview.state = preview.state.copy(motion = motion, paused = false)
        updateLabels()
    }

    private fun updateLabels() {
        title.text = CharacterRegistry.resolve(preview.state.characterId).definition.character.displayName
        pauseButton.text = if (preview.state.paused) "Play" else "Pause"
        preview.renderMode = if (preview.state.paused) GLSurfaceView.RENDERMODE_WHEN_DIRTY
            else GLSurfaceView.RENDERMODE_CONTINUOUSLY
        preview.requestRender()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val state = preview.state
        outState.putString("character_id", state.characterId)
        outState.putString("animation", state.motion.name.lowercase())
        outState.putFloat("yaw", state.yaw)
        outState.putFloat("zoom", state.zoom)
        outState.putBoolean("paused", state.paused)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        preview.onResume()
    }

    override fun onPause() {
        preview.onPause()
        super.onPause()
    }

    private fun button(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(0xFFFFDF92.toInt())
        minHeight = dp(48)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun weighted() = LinearLayout.LayoutParams(0, -2, 1f)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}

private data class PreviewState(
    val characterId: String = "goat_lumi",
    val motion: CharacterMotion = CharacterMotion.IDLE,
    val yaw: Float = 0f,
    val zoom: Float = 1f,
    val paused: Boolean = false,
)

private class CharacterPreviewSurface(context: Context) : GLSurfaceView(context) {
    @Volatile var state = PreviewState()
    private var lastX = 0f
    private val pinch = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                state = state.copy(zoom = (state.zoom * detector.scaleFactor).coerceIn(.6f, 1.5f))
                return true
            }
        })

    init {
        contentDescription = "Animated 3D character. Drag horizontally to rotate; pinch to zoom."
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        setRenderer(object : GLSurfaceView.Renderer {
            private lateinit var scene: LudoPaws3DSceneRenderer
            private var life = LudoPaws3DLifeMotion()
            private var previous = PreviewState()
            private var elapsed = 0L
            private var lastFrame = 0L

            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                // A recreated EGL context cannot reuse the old context's mesh/program handles.
                scene = LudoPaws3DSceneRenderer()
                scene.onPortraitSurfaceCreated()
                GLES30.glClearColor(.075f, .11f, .14f, 1f)
                lastFrame = 0L
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                scene.onPortraitSurfaceChanged(width, height)
            }

            override fun onDrawFrame(gl: GL10?) {
                val frame = state
                val now = SystemClock.uptimeMillis()
                if (frame.characterId != previous.characterId || frame.motion != previous.motion) {
                    elapsed = 0L
                    life = LudoPaws3DLifeMotion()
                    lastFrame = now
                }
                if (frame.paused != previous.paused) lastFrame = now
                if (!frame.paused && lastFrame != 0L) elapsed += (now - lastFrame).coerceIn(0L, 64L)
                lastFrame = now
                previous = frame
                val character = CharacterRegistry.resolve(frame.characterId)
                val duration = when (frame.motion) {
                    CharacterMotion.IDLE -> character.definition.idleDurationMillis
                    CharacterMotion.HOP -> 1100L
                    CharacterMotion.HOME -> character.definition.homeDurationMillis
                }.coerceAtLeast(1L)
                life.update(10_000L + elapsed, 37, frame.motion == CharacterMotion.IDLE,
                    0f, 0f, LudoPaws3DReactionPose())
                val sampled = character.renderer.sample(frame.motion, (elapsed % duration).toFloat() / duration,
                    CharacterHopPlayback.ISOLATED)
                val pose = if (frame.motion == CharacterMotion.IDLE) life.idlePose(sampled) else sampled
                scene.drawPortrait(frame.characterId, "BLUE",
                    pose.copy(bodyYawDegrees = pose.bodyYawDegrees + frame.yaw), life,
                    previewZoom = frame.zoom, synchronizeGpu = false)
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        pinch.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 1 && !pinch.isInProgress) {
            state = state.copy(yaw = (state.yaw + (event.x - lastX) * .35f) % 360f)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        lastX = event.x
        requestRender()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
