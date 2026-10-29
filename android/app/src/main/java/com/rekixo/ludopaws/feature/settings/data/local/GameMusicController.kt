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
import com.rekixo.ludopaws.feature.boards.domain.BoardRegistry
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory
import kotlin.math.max

object GameMusicController {
    private var appForeground = false
    private var player: MediaPlayer? = null
    private var preparingPlayer = false
    private var playerGeneration = 0L
    private var activeBoardMusicId: String? = null
    // Ambient playback is a distinct looping MediaPlayer, never the BGM player.
    private var ambientPlayer: MediaPlayer? = null
    private var preparingAmbient = false
    private var ambientGeneration = 0L
    private var unavailableAmbientBoardId: String? = null
    private var focusDucked = false
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
                        focusDucked = false
                        applyCurrentVolume()
                        if (appForeground) {
                            runCatching { if (player?.isPlaying == false) player?.start() }
                            runCatching { if (ambientPlayer?.isPlaying == false) ambientPlayer?.start() }
                        }
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        focusDucked = true
                        applyCurrentVolume()
                    }

                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        hasAudioFocus = false
                        focusDucked = false
                        runCatching { player?.pause() }
                        runCatching { ambientPlayer?.pause() }
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
        releaseAmbient()
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
        releaseAmbient()
        mainHandler.removeCallbacks(
            restoreVolumeRunnable,
        )
        voiceDuckUntilMillis = 0L
        abandonFocus()
        runCatching {
            player?.release()
        }
        player = null
        activeBoardMusicId = null
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
            runCatching { player?.pause() }
            releaseAmbient()
            if (!enabled) {
                runCatching {
                    player?.seekTo(0)
                }
            }
            abandonFocus()
            return
        }

        val selectedBoardId =
            CosmeticInventoryStore(context).selectedId(CosmeticCategory.BOARD)
        if (activeBoardMusicId != selectedBoardId) {
            // A rapid cosmetic change invalidates an outstanding background prepare.
            activeBoardMusicId = selectedBoardId
            playerGeneration += 1
            preparingPlayer = false
            runCatching { player?.release() }
            player = null
            releaseAmbient()
        }
        ensurePlayer(context)
        if (player == null) {
            return
        }

        if (!requestFocus(context)) {
            return
        }

        ensureAmbientPlayer(
            context = context,
            boardId = selectedBoardId,
            paths = BoardRegistry.resolve(selectedBoardId).ambientSoundPaths,
        )
        applyCurrentVolume()
        if (player?.isPlaying == false) {
            runCatching { player?.start() }
        }
        if (ambientPlayer?.isPlaying == false) {
            runCatching { ambientPlayer?.start() }
        }
    }

    private fun releaseAmbient() {
        ambientGeneration += 1
        preparingAmbient = false
        unavailableAmbientBoardId = null
        runCatching { ambientPlayer?.release() }
        ambientPlayer = null
    }

    /**
     * Prepares the selected board's soundscape separately from the soundtrack.
     * Only the newest selected board can install an asynchronous prepared player.
     * Missing ambient clips mean silence, not a borrowed sound from another board.
     */
    private fun ensureAmbientPlayer(
        context: Context,
        boardId: String,
        paths: List<String>,
    ) {
        if (paths.isEmpty() || ambientPlayer != null || preparingAmbient ||
            unavailableAmbientBoardId == boardId) return
        preparingAmbient = true
        val generation = ++ambientGeneration
        val appContext = context.applicationContext
        playerWorker.execute {
            val prepared = paths.firstNotNullOfOrNull { path ->
                createAssetPlayer(appContext, path)
            }
            mainHandler.post {
                synchronized(this) {
                    if (generation != ambientGeneration) {
                        runCatching { prepared?.release() }
                        return@synchronized
                    }
                    preparingAmbient = false
                    if (prepared == null) {
                        unavailableAmbientBoardId = boardId
                    } else {
                        ambientPlayer = prepared
                        applyCurrentVolume()
                        if (appForeground &&
                            GameSettingsStore(appContext).snapshot().musicEnabled &&
                            hasAudioFocus
                        ) {
                            runCatching { ambientPlayer?.start() }
                        }
                    }
                }
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
        val boardMusicPaths = BoardRegistry.resolve(activeBoardMusicId ?: "board_classic").backgroundMusicPaths
        playerWorker.execute {
            val startedAt = SystemClock.elapsedRealtime()
            val result = runCatching {
                (boardMusicPaths + authoredBackgroundMusicPaths(appContext))
                    .distinct()
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
        when {
            focusDucked -> FOCUS_DUCK_VOLUME
            SystemClock.elapsedRealtime() < voiceDuckUntilMillis -> VOICE_DUCK_VOLUME
            else -> MUSIC_VOLUME
        }

    private fun currentAmbientVolume(): Float =
        when {
            focusDucked -> FOCUS_AMBIENT_VOLUME
            SystemClock.elapsedRealtime() < voiceDuckUntilMillis -> VOICE_AMBIENT_VOLUME
            else -> AMBIENT_VOLUME
        }

    private fun applyCurrentVolume() {
        val musicVolume = currentTargetVolume()
        val ambienceVolume = currentAmbientVolume()
        runCatching { player?.setVolume(musicVolume, musicVolume) }
        runCatching { ambientPlayer?.setVolume(ambienceVolume, ambienceVolume) }
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
        focusDucked = false
    }

    private const val AMBIENT_VOLUME = .08f
    private const val VOICE_AMBIENT_VOLUME = .025f
    private const val FOCUS_AMBIENT_VOLUME = .012f
    private const val MUSIC_VOLUME = .16f
    private const val VOICE_DUCK_VOLUME = .075f
    private const val FOCUS_DUCK_VOLUME = .05f
}
