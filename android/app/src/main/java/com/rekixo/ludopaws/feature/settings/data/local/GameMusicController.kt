package com.rekixo.ludopaws.feature.settings.data.local

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.rekixo.ludopaws.R
import com.rekixo.ludopaws.core.audio.LudoPawsSoundPool
import kotlin.math.max

object GameMusicController {
    private var appForeground = false
    private var player: MediaPlayer? = null
    private var preparingPlayer = false
    private var playerGeneration = 0L
    private val playerWorker = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "LudoPawsMusicPrepare").apply { isDaemon = true }
    }
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var voiceDuckUntilMillis = 0L
    private val mainHandler =
        Handler(
            Looper.getMainLooper(),
        )

    private val restoreVolumeRunnable =
        Runnable {
            synchronized(this) {
                if (
                    SystemClock.elapsedRealtime() >=
                    voiceDuckUntilMillis
                ) {
                    voiceDuckUntilMillis = 0L
                    applyCurrentVolume()
                } else {
                    scheduleDuckRestore()
                }
            }
        }

    private val focusListener =
        AudioManager.OnAudioFocusChangeListener { change ->
            synchronized(this) {
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        hasAudioFocus = true
                        applyCurrentVolume()
                        if (
                            appForeground &&
                            player?.isPlaying == false
                        ) {
                            runCatching {
                                player?.start()
                            }
                        }
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                        player?.setVolume(
                            FOCUS_DUCK_VOLUME,
                            FOCUS_DUCK_VOLUME,
                        )

                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        hasAudioFocus = false
                        runCatching {
                            player?.pause()
                        }
                    }
                }
            }
        }

    @Synchronized
    fun onAppForeground(
        context: Context,
    ) {
        appForeground = true
        sync(context.applicationContext)
    }

    @Synchronized
    fun onAppBackground() {
        appForeground = false
        // Stop active and queued SFX; callbacks from the old pool must not fire later.
        LudoPawsSoundPool.release()
        voiceDuckUntilMillis = 0L
        mainHandler.removeCallbacks(
            restoreVolumeRunnable,
        )
        runCatching {
            player?.pause()
        }
        abandonFocus()
    }

    @Synchronized
    fun onPreferenceChanged(
        context: Context,
    ) {
        sync(context.applicationContext)
    }

    /**
     * Temporarily lowers music under an animal call or spoken fallback without
     * surrendering the app's existing game-audio focus ownership.
     */
    @Synchronized
    fun duckForVoice(
        durationMillis: Long,
    ) {
        if (durationMillis <= 0L) {
            return
        }
        voiceDuckUntilMillis =
            max(
                voiceDuckUntilMillis,
                SystemClock.elapsedRealtime() +
                    durationMillis,
            )
        applyCurrentVolume()
        scheduleDuckRestore()
    }

    @Synchronized
    fun release() {
        playerGeneration += 1
        preparingPlayer = false
        mainHandler.removeCallbacks(
            restoreVolumeRunnable,
        )
        voiceDuckUntilMillis = 0L
        abandonFocus()
        runCatching {
            player?.release()
        }
        player = null
        audioManager = null
    }

    private fun sync(
        context: Context,
    ) {
        val enabled =
            GameSettingsStore(context)
                .snapshot()
                .musicEnabled

        if (
            !enabled ||
            !appForeground
        ) {
            runCatching {
                player?.pause()
            }
            if (!enabled) {
                runCatching {
                    player?.seekTo(0)
                }
            }
            abandonFocus()
            return
        }

        ensurePlayer(context)
        if (player == null) {
            return
        }

        if (!requestFocus(context)) {
            return
        }

        applyCurrentVolume()
        if (player?.isPlaying == false) {
            runCatching {
                player?.start()
            }
        }
    }

    private fun ensurePlayer(
        context: Context,
    ) {
        if (player != null || preparingPlayer) {
            return
        }
        preparingPlayer = true
        val generation = playerGeneration
        val appContext = context.applicationContext
        playerWorker.execute {
            val startedAt = SystemClock.elapsedRealtime()
            val result = runCatching {
                authoredBackgroundMusicPaths(appContext)
                    .firstNotNullOfOrNull { assetPath -> createAssetPlayer(appContext, assetPath) }
                    ?: MediaPlayer.create(appContext, R.raw.ludoproof_theme)
            }
            val preparedPlayer = result.getOrNull()
            mainHandler.post {
                synchronized(this) {
                    if (generation != playerGeneration) {
                        runCatching { preparedPlayer?.release() }
                        return@synchronized
                    }
                    preparingPlayer = false
                    player = runCatching {
                        preparedPlayer?.apply {
                            isLooping = true
                            setVolume(currentTargetVolume(), currentTargetVolume())
                        }
                    }.getOrElse {
                        runCatching { preparedPlayer?.release() }
                        null
                    }
                    result.onFailure { android.util.Log.w("LudoPawsLoad", "Music preparation failed", it) }
                    android.util.Log.d("LudoPawsLoad", "music prepare_ms=${SystemClock.elapsedRealtime() - startedAt} success=${player != null}")
                    if (player != null) sync(appContext)
                }
            }
        }
    }

    /**
     * Reads authored BGM from APK assets so filenames can stay exactly as
     * Windows saved them (for example, `ludoproof_theme (1).wav`). Android
     * `res/raw` cannot contain spaces or parentheses in resource filenames.
     */
    private fun authoredBackgroundMusicPaths(context: Context): List<String> {
        val appContext = context.applicationContext
        val directory = "audio/music"
        val filenamePattern =
            Regex(
                "^(?:ludoproof_theme(?: \\(\\d+\\))?|Firefly-audio-Title-Ludo-Paws-Ro)\\.(wav|ogg|mp3)$",
                RegexOption.IGNORE_CASE,
            )
        return runCatching {
            appContext.assets
                .list(directory)
                .orEmpty()
                .filter(filenamePattern::matches)
                .sortedWith(
                    compareBy<String>({ duplicateMusicFilenameIndex(it) }, { it }),
                )
                .map { "$directory/$it" }
                .filter { assetPath ->
                    runCatching { appContext.assets.openFd(assetPath).use { } }.isSuccess
                }
        }.getOrDefault(emptyList())
    }

    private fun createAssetPlayer(
        context: Context,
        assetPath: String,
    ): MediaPlayer? {
        var candidate: MediaPlayer? = null
        return runCatching {
            context.applicationContext.assets.openFd(assetPath).use { descriptor ->
                MediaPlayer().also { mediaPlayer ->
                    candidate = mediaPlayer
                    mediaPlayer.setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    mediaPlayer.setDataSource(
                        descriptor.fileDescriptor,
                        descriptor.startOffset,
                        descriptor.length,
                    )
                    mediaPlayer.isLooping = true
                    mediaPlayer.prepare()
                }
            }
        }.getOrElse {
            runCatching { candidate?.release() }
            null
        }
    }

    private fun duplicateMusicFilenameIndex(fileName: String): Int =
        Regex(""" \\((\\d+)\\)(?=\\.[^.]+$)""")
            .find(fileName)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0

    private fun requestFocus(
        context: Context,
    ): Boolean {
        if (hasAudioFocus) {
            return true
        }

        val manager =
            audioManager
                ?: (
                    context.getSystemService(
                        Context.AUDIO_SERVICE,
                    ) as? AudioManager
                    )
                    ?.also {
                        audioManager = it
                    }
                ?: return false

        val request =
            focusRequest
                ?: AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setOnAudioFocusChangeListener(focusListener)
                    .setWillPauseWhenDucked(false)
                    .build()
                    .also {
                        focusRequest = it
                    }

        hasAudioFocus =
            manager.requestAudioFocus(request) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun scheduleDuckRestore() {
        mainHandler.removeCallbacks(
            restoreVolumeRunnable,
        )
        val remaining =
            voiceDuckUntilMillis -
                SystemClock.elapsedRealtime()
        if (remaining > 0L) {
            mainHandler.postDelayed(
                restoreVolumeRunnable,
                remaining + 20L,
            )
        }
    }

    private fun currentTargetVolume(): Float =
        if (
            SystemClock.elapsedRealtime() <
            voiceDuckUntilMillis
        ) {
            VOICE_DUCK_VOLUME
        } else {
            MUSIC_VOLUME
        }

    private fun applyCurrentVolume() {
        val volume =
            currentTargetVolume()
        runCatching {
            player?.setVolume(
                volume,
                volume,
            )
        }
    }

    private fun abandonFocus() {
        val manager = audioManager
        val request = focusRequest
        if (
            manager != null &&
            request != null &&
            hasAudioFocus
        ) {
            runCatching {
                manager.abandonAudioFocusRequest(request)
            }
        }
        hasAudioFocus = false
    }

    private const val MUSIC_VOLUME = .16f
    private const val VOICE_DUCK_VOLUME = .075f
    private const val FOCUS_DUCK_VOLUME = .05f
}
