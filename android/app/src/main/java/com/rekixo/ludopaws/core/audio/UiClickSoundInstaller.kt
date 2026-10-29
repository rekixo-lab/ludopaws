package com.rekixo.ludopaws.core.audio

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.TextView
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.feature.settings.data.local.GameSoundFeedback
import java.lang.ref.WeakReference

/**
 * Adds the authored UI click SFX to native and custom button-like controls
 * without replacing their existing click callbacks.
 *
 * The window callback observes completed taps without replacing any View touch
 * or click listener. Dynamic layouts need no global-layout scans or per-view hooks.
 * Gameplay canvas views are intentionally excluded.
 */
object UiClickSoundInstaller {
    @Volatile
    private var resolvedClickAsset: String? = null
    @Volatile
    private var clickAssetResolved = false

    fun install(activity: Activity) {
        val window = activity.window ?: return
        val callback = window.callback ?: return
        if (callback is ClickSoundCallback) return
        window.callback = ClickSoundCallback(callback, window.decorView)
    }

    private fun targetAt(view: View, x: Float, y: Float): View? {
        if (!view.isShown || !view.isEnabled) return null
        val bounds = android.graphics.Rect()
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains(x.toInt(), y.toInt())) return null
        if (view is ViewGroup) {
            for (index in view.childCount - 1 downTo 0) {
                targetAt(view.getChildAt(index), x, y)?.let { return it }
            }
        }
        return view.takeIf { buttonLike(it) }
    }

    private fun buttonLike(view: View): Boolean = view.isClickable && (
            view is Button ||
                view is ImageButton ||
                view is CompoundButton ||
                (view is TextView && view.isClickable) ||
                view is ViewGroup)

    private class ClickSoundCallback(
        private val delegate: Window.Callback,
        private val root: View,
    ) : Window.Callback by delegate {
        private var downTarget: WeakReference<View>? = null
        private var downX = 0f
        private var downY = 0f
        private val slop = ViewConfiguration.get(root.context).scaledTouchSlop

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                downTarget = targetAt(root, event.rawX, event.rawY)?.let { WeakReference(it) }
                downX = event.rawX
                downY = event.rawY
            }
            if (event.pointerCount > 1 || event.actionMasked == MotionEvent.ACTION_CANCEL ||
                kotlin.math.abs(event.rawX - downX) > slop || kotlin.math.abs(event.rawY - downY) > slop) downTarget = null
            val candidate = if (event.actionMasked == MotionEvent.ACTION_UP &&
                event.eventTime - event.downTime <= MAX_TAP_DURATION_MS) downTarget?.get() else null
            val handled = delegate.dispatchTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                downTarget = null
                if (handled && candidate != null && candidate.isEnabled && candidate.isShown &&
                    targetAt(root, event.rawX, event.rawY) === candidate) playClick(candidate)
            }
            return handled
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            val focused = root.findFocus()
            val handled = delegate.dispatchKeyEvent(event)
            if (handled && event.action == KeyEvent.ACTION_UP && focused != null && focused.isEnabled &&
                buttonLike(focused) && event.keyCode in intArrayOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE)) playClick(focused)
            return handled
        }
    }

    private fun playClick(view: View) {
        val context = view.context.applicationContext
        if (!GameSettingsStore(context).snapshot().soundEnabled) return

        val assetPath = resolveClickAsset(context)
        if (assetPath == null) {
            GameSoundFeedback.click(context)
            return
        }
        if (
            !LudoPawsSoundPool.playAsset(
                context = context,
                assetPath = assetPath,
                volume = CLICK_VOLUME,
            )
        ) {
            // If SoundPool refuses a stream, try the common authored/procedural fallback.
            GameSoundFeedback.click(context)
        }
    }

    private fun resolveClickAsset(context: android.content.Context): String? {
        if (clickAssetResolved) return resolvedClickAsset
        synchronized(this) {
            if (clickAssetResolved) return resolvedClickAsset

            val assets = context.applicationContext.assets
            resolvedClickAsset = LudoPawsAudioCatalog.Sfx.UI_CLICK.firstNotNullOfOrNull { path ->
                val exactExists = runCatching {
                    assets.openFd(path).use { }
                }.isSuccess
                if (exactExists) {
                    path
                } else {
                    val directory = path.substringBeforeLast('/')
                    val fileName = path.substringAfterLast('/')
                    val extension = fileName.substringAfterLast('.', "")
                    val stem = fileName.substringBeforeLast('.')
                    assets.list(directory)
                        .orEmpty()
                        .firstOrNull { candidate ->
                            candidate.startsWith("$stem (") &&
                                candidate.endsWith(").$extension", ignoreCase = true) &&
                                candidate
                                    .removePrefix("$stem (")
                                    .removeSuffix(").$extension")
                                    .toIntOrNull() != null
                        }
                        ?.let { "$directory/$it" }
                }
            }
            clickAssetResolved = true
            resolvedClickAsset?.let { LudoPawsSoundPool.preloadAsset(context, it) }
            return resolvedClickAsset
        }
    }

    private const val CLICK_VOLUME = 0.55f
    private const val MAX_TAP_DURATION_MS = 600L
}
