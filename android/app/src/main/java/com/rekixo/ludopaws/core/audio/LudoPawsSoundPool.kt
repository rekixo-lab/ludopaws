package com.rekixo.ludopaws.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RawRes

/**
 * Shared low-latency clip renderer for short Ludo Paws game/animal sounds.
 *
 * Samples are loaded lazily and a small first-play queue prevents a freshly
 * loaded clip from being silently lost. This object is presentation-only and
 * owns no gameplay or proof state.
 *
 * Authored audio can live in the repository-level /audio tree. Gradle mirrors
 * that tree into APK assets and this class loads those clips through the same
 * bounded SoundPool used by res/raw fallbacks.
 */
object LudoPawsSoundPool {
    private data class Sample(
        val soundId: Int,
        var ready: Boolean = false,
        var lastUsedAtMillis: Long = SystemClock.elapsedRealtime(),
    )

    private data class PendingPlay(
        val volume: Float,
        val rate: Float,
        val priority: Int,
        val queuedAtMillis: Long,
    )

    private var pool: SoundPool? = null
    private val samplesByKey = mutableMapOf<String, Sample>()
    private val sourceKeyBySoundId = mutableMapOf<Int, String>()
    private val readySoundIds = mutableSetOf<Int>()
    // A completion can race the return from SoundPool.load(), before the source key exists.
    private val failedSoundIds = mutableSetOf<Int>()
    private val pendingByKey = mutableMapOf<String, MutableList<PendingPlay>>()

    @Synchronized
    fun play(
        context: Context,
        @RawRes resourceId: Int,
        volume: Float = 1f,
        rate: Float = 1f,
        priority: Int = DEFAULT_PRIORITY,
    ): Boolean =
        playSource(
            context = context,
            key = "res:$resourceId",
            loader = { soundPool ->
                soundPool.load(
                    context.applicationContext,
                    resourceId,
                    1,
                )
            },
            volume = volume,
            rate = rate,
            priority = priority,
        )

    @Synchronized
    fun playAsset(
        context: Context,
        assetPath: String,
        volume: Float = 1f,
        rate: Float = 1f,
        priority: Int = DEFAULT_PRIORITY,
    ): Boolean =
        playSource(
            context = context,
            key = "asset:$assetPath",
            loader = { soundPool ->
                context.applicationContext.assets
                    .openFd(assetPath)
                    .use { descriptor ->
                        soundPool.load(
                            descriptor,
                            1,
                        )
                    }
            },
            volume = volume,
            rate = rate,
            priority = priority,
        )

    /**
     * Starts loading an authored asset without scheduling playback.
     *
     * Calling this before the first interactive move removes the one-time
     * SoundPool load latency from the movement audio clock. A successful return
     * means the clip was already cached or the asynchronous load was accepted.
     */
    @Synchronized
    fun preloadAsset(
        context: Context,
        assetPath: String,
    ): Boolean =
        preloadSource(
            context = context,
            key = "asset:$assetPath",
            loader = { soundPool ->
                context.applicationContext.assets
                    .openFd(assetPath)
                    .use { descriptor ->
                        soundPool.load(
                            descriptor,
                            1,
                        )
                    }
            },
        )

    /**
     * Starts loading a packaged raw-resource clip without scheduling playback.
     */
    @Synchronized
    fun preload(
        context: Context,
        @RawRes resourceId: Int,
    ): Boolean =
        preloadSource(
            context = context,
            key = "res:$resourceId",
            loader = { soundPool ->
                soundPool.load(
                    context.applicationContext,
                    resourceId,
                    1,
                )
            },
        )

    /** Drop requests that have not started yet (mute, screen exit or owner switch). */
    @Synchronized
    fun cancelPending() {
        pendingByKey.clear()
    }

    @Synchronized
    fun release() {
        runCatching {
            pool?.release()
        }
        pool = null
        samplesByKey.clear()
        sourceKeyBySoundId.clear()
        readySoundIds.clear()
        failedSoundIds.clear()
        pendingByKey.clear()
    }

    private fun preloadSource(
        context: Context,
        key: String,
        loader: (SoundPool) -> Int,
    ): Boolean {
        val soundPool =
            ensurePool()
                ?: return false

        if (samplesByKey.containsKey(key)) {
            return true
        }

        return load(
            soundPool = soundPool,
            sourceKey = key,
            loader = loader,
        ) != null
    }

    private fun playSource(
        context: Context,
        key: String,
        loader: (SoundPool) -> Int,
        volume: Float,
        rate: Float,
        priority: Int,
    ): Boolean {
        val soundPool =
            ensurePool()
                ?: return false
        val sample =
            samplesByKey[key]
                ?: load(
                    soundPool = soundPool,
                    sourceKey = key,
                    loader = loader,
                ) ?: return false
        sample.lastUsedAtMillis = SystemClock.elapsedRealtime()

        val safeVolume =
            volume.coerceIn(
                0f,
                1f,
            )
        val safeRate =
            rate.coerceIn(
                MIN_RATE,
                MAX_RATE,
            )
        val safePriority =
            priority.coerceIn(
                MIN_PRIORITY,
                MAX_PRIORITY,
            )

        if (
            sample.ready ||
            sample.soundId in readySoundIds
        ) {
            sample.ready = true
            // Let the caller fall back when SoundPool cannot start a stream.
            return playNow(
                soundPool = soundPool,
                soundId = sample.soundId,
                volume = safeVolume,
                rate = safeRate,
                priority = safePriority,
            )
        }

        val pending =
            pendingByKey.getOrPut(key) {
                mutableListOf()
            }
        val request =
            PendingPlay(
                volume = safeVolume,
                rate = safeRate,
                priority = safePriority,
                queuedAtMillis = SystemClock.elapsedRealtime(),
            )

        // Don't replay a backlog of tiny footsteps after an asynchronous
        // sample load. Critical events may displace lower-priority pending
        // feedback when a particular sample has already reached its bound.
        if (safePriority <= LOW_PRIORITY_COALESCE_THRESHOLD) {
            if (pending.isEmpty()) pending += request
        } else if (pending.size < MAX_PENDING_PER_SAMPLE) {
            pending += request
        } else {
            val lowestIndex =
                pending.indices.minByOrNull { pending[it].priority }
            if (
                lowestIndex != null &&
                pending[lowestIndex].priority < safePriority
            ) {
                pending[lowestIndex] = request
            }
        }
        Log.d(TAG, "PLAY_QUEUED key=" + key + " ready=false queued=" + pendingByKey[key]?.size)
        return true
    }

    private fun ensurePool(): SoundPool? {
        pool?.let {
            return it
        }

        val created =
            runCatching {
                SoundPool
                    .Builder()
                    .setMaxStreams(MAX_STREAMS)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(
                                AudioAttributes.USAGE_GAME,
                            )
                            .setContentType(
                                AudioAttributes.CONTENT_TYPE_SONIFICATION,
                            )
                            .build(),
                    )
                    .build()
            }
                .getOrNull()
                ?: return null

        created.setOnLoadCompleteListener { soundPool, soundId, status ->
            synchronized(this) {
                if (soundPool !== pool) return@synchronized
                val sourceKey = sourceKeyBySoundId[soundId]
                if (status != 0) {
                    val queuedCount = sourceKey?.let { pendingByKey[it]?.size } ?: 0
                    if (sourceKey == null) {
                        // Completion raced load(); load() will see the failed ID.
                        failedSoundIds += soundId
                    } else {
                        if (samplesByKey[sourceKey]?.soundId == soundId) {
                            samplesByKey.remove(sourceKey)
                        }
                        sourceKeyBySoundId.remove(soundId)
                        pendingByKey.remove(sourceKey)
                    }
                    readySoundIds.remove(soundId)
                    runCatching { soundPool.unload(soundId) }
                    Log.e(
                        TAG,
                        "LOAD_FAILED soundId=" + soundId + " key=" + (sourceKey ?: "unknown") +
                            " status=" + status + " pending_discarded=" + queuedCount,
                    )
                    return@synchronized
                }
                readySoundIds += soundId
                if (sourceKey == null) {
                    Log.w(TAG, "LOAD_COMPLETE_WITHOUT_KEY soundId=" + soundId)
                    return@synchronized
                }
                val sample = samplesByKey[sourceKey]
                if (sample == null) {
                    Log.w(TAG, "LOAD_COMPLETE_WITHOUT_SAMPLE soundId=" + soundId + " key=" + sourceKey)
                    return@synchronized
                }
                sample.ready = true
                val queuedCount = pendingByKey[sourceKey]?.size ?: 0
                Log.d(TAG, "LOAD_COMPLETE soundId=" + soundId + " key=" + sourceKey + " queued=" + queuedCount)
                flushPending(
                    soundPool = soundPool,
                    sourceKey = sourceKey,
                    sample = sample,
                )
            }
        }

        pool = created
        return created
    }

    private fun load(
        soundPool: SoundPool,
        sourceKey: String,
        loader: (SoundPool) -> Int,
    ): Sample? {
        if (!makeRoomForSample(soundPool)) {
            Log.w(TAG, "CACHE_FULL key=$sourceKey count=${samplesByKey.size}")
            return null
        }
        val loadResult = runCatching { loader(soundPool) }
        val soundId =
            loadResult.getOrNull()
                ?.takeIf { it > 0 }
                ?: run {
                    val error = loadResult.exceptionOrNull()
                    if (error != null) {
                        Log.e(TAG, "LOAD_REQUEST_FAILED key=" + sourceKey, error)
                    } else {
                        Log.e(TAG, "LOAD_REQUEST_FAILED key=" + sourceKey +
                            " invalidSoundId=" + loadResult.getOrNull())
                    }
                    return null
                }

        if (failedSoundIds.remove(soundId)) {
            // SoundPool reported failure before load() returned.
            pendingByKey.remove(sourceKey)
            runCatching { soundPool.unload(soundId) }
            return null
        }

        val sample =
            Sample(
                soundId = soundId,
                ready = soundId in readySoundIds,
            )
        samplesByKey[sourceKey] = sample
        sourceKeyBySoundId[soundId] = sourceKey
        if (sample.ready) {
            flushPending(
                soundPool = soundPool,
                sourceKey = sourceKey,
                sample = sample,
            )
        } else {
            Log.d(TAG, "LOAD_REQUESTED soundId=" + soundId + " key=" + sourceKey)
        }
        return sample
    }

    private fun makeRoomForSample(soundPool: SoundPool): Boolean {
        if (samplesByKey.size < MAX_CACHED_SAMPLES) return true
        val oldestReady =
            samplesByKey.entries
                .asSequence()
                .filter { (_, sample) -> sample.ready }
                .filter { (key, _) -> pendingByKey[key].isNullOrEmpty() }
                .minByOrNull { (_, sample) -> sample.lastUsedAtMillis }
                ?: return false
        samplesByKey.remove(oldestReady.key)
        sourceKeyBySoundId.remove(oldestReady.value.soundId)
        readySoundIds.remove(oldestReady.value.soundId)
        runCatching { soundPool.unload(oldestReady.value.soundId) }
        return true
    }

    private fun flushPending(
        soundPool: SoundPool,
        sourceKey: String,
        sample: Sample,
    ) {
        val pending =
            pendingByKey
                .remove(sourceKey)
                .orEmpty()
        val now = SystemClock.elapsedRealtime()
        pending
            .filter {
                LudoPawsAudioQueuePolicy.isFresh(
                    queuedAtMillis = it.queuedAtMillis,
                    nowMillis = now,
                    priority = it.priority,
                )
            }
            .sortedByDescending { it.priority }
            .forEach { play ->
                playNow(
                    soundPool = soundPool,
                    soundId = sample.soundId,
                    volume = play.volume,
                    rate = play.rate,
                    priority = play.priority,
                )
            }
    }

    private fun playNow(
        soundPool: SoundPool,
        soundId: Int,
        volume: Float,
        rate: Float,
        priority: Int,
    ): Boolean {
        val streamId = runCatching {
            soundPool.play(
                soundId,
                volume,
                volume,
                priority,
                0,
                rate,
            )
        }.getOrDefault(0)
        if (streamId == 0) {
            Log.w(TAG, "PLAY_REFUSED soundId=" + soundId + " priority=" + priority)
            return false
        }
        Log.d(TAG, "PLAY_STARTED soundId=" + soundId + " streamId=" + streamId + " priority=" + priority)
        return true
    }

    private const val TAG = "LudoPawsAudio"
    private const val MAX_STREAMS = 8
    // Bounds native SoundPool sample allocations as more cosmetics are installed.
    private const val MAX_CACHED_SAMPLES = 96
    private const val MAX_PENDING_PER_SAMPLE = 3
    private const val LOW_PRIORITY_COALESCE_THRESHOLD = 2
    private const val DEFAULT_PRIORITY = 1
    private const val MIN_PRIORITY = 0
    private const val MAX_PRIORITY = 10
    private const val MIN_RATE = .65f
    private const val MAX_RATE = 1.45f
}
