package com.rekixo.ludopaws.core.audio

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.TextView
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.feature.settings.data.local.GameSoundFeedback
import java.util.Collections
import java.util.WeakHashMap

/**
 * Adds the authored UI click SFX to native and custom button-like controls
 * without replacing their existing click callbacks.
 *
 * Observe added subtrees instead of traversing the entire screen on each layout.
 * Each control is hooked once; gameplay canvas views are excluded.
 */
object UiClickSoundInstaller {
    private val hookedViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val scannedChildren = WeakHashMap<ViewGroup, List<View>>()
    private val observedRoots = Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    @Volatile
    private var resolvedClickAsset: String? = null
    @Volatile
    private var clickAssetResolved = false

    fun install(activity: Activity) {
        val root = activity.window?.decorView ?: return
        val needsObserver = synchronized(observedRoots) {
            observedRoots.add(root)
        }
        if (needsObserver) root.post { scan(root) }
    }

    private fun scan(view: View) {
        hookIfButtonLike(view)
        if (view is ViewGroup && scannedChildren.put(view, emptyList()) == null) {
            view.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) { child?.let(::scan) }
                override fun onChildViewRemoved(parent: View?, child: View?) = Unit
            })
            for (index in 0 until view.childCount) scan(view.getChildAt(index))
        }
    }

    private fun hookIfButtonLike(view: View) {
        if (!view.isClickable) return

        val buttonLike =
            view is Button ||
                view is ImageButton ||
                view is CompoundButton ||
                (view is TextView && view.isClickable) ||
                view is ViewGroup

        if (!buttonLike) return

        val firstHook = synchronized(hookedViews) {
            hookedViews.add(view)
        }
        if (!firstHook) return

        view.setOnTouchListener { target, event ->
            if (
                event.actionMasked == MotionEvent.ACTION_UP &&
                event.eventTime - event.downTime <= MAX_TAP_DURATION_MS &&
                target.isEnabled &&
                target.isClickable &&
                target.isPressed
            ) {
                playClick(target)
            }
            // Preserve the view's native pressed/click handling and callback.
            false
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
