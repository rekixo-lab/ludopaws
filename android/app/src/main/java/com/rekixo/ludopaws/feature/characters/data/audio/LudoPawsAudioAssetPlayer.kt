package com.rekixo.ludopaws.feature.characters.data.audio

import android.content.Context
import android.content.res.AssetManager
import com.rekixo.ludopaws.core.audio.LudoPawsAudioCatalog
import com.rekixo.ludopaws.core.audio.LudoPawsSoundPool
import com.rekixo.ludopaws.feature.characters.domain.audio.LudoPawsProceduralVocal
import com.rekixo.ludopaws.feature.characters.domain.audio.LudoPawsCharacterAudioProfiles

/**
 * Resolves authored assets before raw-resource and procedural fallbacks.
 *
 * Vocal lookup has three tiers:
 * 1. exact situation override,
 * 2. packaged species primitive,
 * 3. generated copyright-safe emergency fallback.
 *
 * Physical SFX lookup has two authored tiers:
 * 1. repository-owned /audio asset paths,
 * 2. stable res/raw resource names,
 * followed by the generated situation-specific fallback.
 */
internal object LudoPawsAudioAssetPlayer {
    private val authoredAssetAvailability = mutableMapOf<String, Boolean>()
    private val resolvedAssetsByExpectedPath = mutableMapOf<String, List<String>>()
    private val nextSfxVariantByFamily = mutableMapOf<String, Int>()

    fun playVocal(
        context: Context,
        rawResourceNames: List<String>,
        variantIndex: Int,
        fallback: LudoPawsProceduralVocal,
        volume: Float,
        playbackRate: Float,
        priority: Int = 5,
        fallbackResourceNames: List<String> = emptyList(),
        allowFallback: Boolean = true,
    ) {
        val orderedNames =
            rotate(
                values = rawResourceNames,
                startIndex = variantIndex,
            ) + rotate(fallbackResourceNames, variantIndex)
        // Prefer character-specific audio placed in the repository audio tree.
        // The named raw resources remain the compatibility fallback.
        val vocalAssetPaths =
            LudoPawsCharacterAudioProfiles.vocalAssetPaths(orderedNames)
        val authoredVocalPath =
            availableAssetPaths(context, vocalAssetPaths).firstOrNull()
        if (
            authoredVocalPath != null &&
            LudoPawsSoundPool.playAsset(
                context = context,
                assetPath = authoredVocalPath,
                volume = volume,
                rate = playbackRate,
                priority = priority,
            )
        ) {
            return
        }

        // Authored-only characters must not fall through to another animal's voice.
        if (!allowFallback) return

        val packagedFallback =
            packagedFallbackResourceName(fallback)
        val resourceId =
            (orderedNames + packagedFallback)
                .asSequence()
                .map {
                    rawResourceId(
                        context = context,
                        resourceName = it,
                    )
                }
                .firstOrNull { it != 0 }

        if (
            resourceId != null &&
            LudoPawsSoundPool.play(
                context = context,
                resourceId = resourceId,
                volume = volume,
                rate = playbackRate,
                priority = priority,
            )
        ) {
            return
        }

        LudoPawsProceduralAudio.playVocal(
            preset = fallback,
            volume = volume,
            playbackRate = playbackRate,
        )
    }

    fun hasAuthoredVocal(context: Context, characterId: String, names: List<String>): Boolean =
        availableAssetPaths(context, names.flatMap {
            listOf("audio/sfx/voices/$characterId/$it.wav", "audio/sfx/voices/$characterId/$it.ogg")
        }).isNotEmpty()

    /**
     * Prewarms the same authored/raw SFX resolution chain used by playSfx.
     *
     * All existing numbered takes are preloaded so variant rotation stays
     * low-latency during movement and other short presentation events.
     */
    @Synchronized
    fun preloadSfx(
        context: Context,
        rawResourceNames: List<String>,
        assetPaths: List<String> = emptyList(),
    ) {
        // Preload all authored takes and every packaged fallback. The fallback
        // chain can be selected after an asset-open/play failure, and rotating
        // numbered takes must not introduce a new first-play load delay.
        for (assetPath in availableAssetPaths(context, assetPaths)) {
            LudoPawsSoundPool.preloadAsset(
                context = context,
                assetPath = assetPath,
            )
        }

        rawResourceNames
            .asSequence()
            .map { resourceName ->
                rawResourceId(
                    context = context,
                    resourceName = resourceName,
                )
            }
            .filter { it != 0 }
            .distinct()
            .forEach { resourceId ->
                LudoPawsSoundPool.preload(
                    context = context,
                    resourceId = resourceId,
                )
            }
    }

    @Synchronized
    fun playAuthoredSfx(
        context: Context,
        assetPaths: List<String>,
        volume: Float,
        playbackRate: Float = 1f,
        priority: Int = 1,
    ): Boolean {
        val available = availableAssetPaths(context, assetPaths)
        if (available.isEmpty()) return false

        val familyKey = assetFamilyKey(assetPaths)
        val startIndex = (nextSfxVariantByFamily[familyKey] ?: 0).mod(available.size)
        for (offset in available.indices) {
            val index = (startIndex + offset) % available.size
            if (
                LudoPawsSoundPool.playAsset(
                    context = context,
                    assetPath = available[index],
                    volume = volume,
                    rate = playbackRate,
                    priority = priority,
                )
            ) {
                nextSfxVariantByFamily[familyKey] = (index + 1) % available.size
                return true
            }
        }
        return false
    }

    /**
     * Select a stable event take from paths actually packaged in the APK instead of
     * indexing the expected catalog first and possibly selecting a missing file.
     */
    @Synchronized
    fun playAuthoredSfxForEvent(
        context: Context,
        assetPaths: List<String>,
        eventIndex: Int,
        volume: Float,
        playbackRate: Float = 1f,
        priority: Int = 1,
    ): Boolean {
        val available = availableAssetPaths(context, assetPaths)
        val selectedPath =
            LudoPawsAudioCatalog.takeAvailableForEvent(
                availableAssetPaths = available,
                eventIndex = eventIndex,
            ) ?: return false
        return LudoPawsSoundPool.playAsset(
            context = context,
            assetPath = selectedPath,
            volume = volume,
            rate = playbackRate,
            priority = priority,
        )
    }

    fun playSfx(
        context: Context,
        rawResourceNames: List<String>,
        fallback: LudoPawsProceduralAudio.Sfx,
        volume: Float,
        playbackRate: Float = 1f,
        priority: Int = 1,
        assetPaths: List<String> = emptyList(),
    ) {
        if (
            playAuthoredSfx(
                context = context,
                assetPaths = assetPaths,
                volume = volume,
                playbackRate = playbackRate,
                priority = priority,
            )
        ) {
            return
        }

        val resourceId =
            rawResourceNames
                .asSequence()
                .map {
                    rawResourceId(
                        context = context,
                        resourceName = it,
                    )
                }
                .firstOrNull { it != 0 }

        if (
            resourceId != null &&
            LudoPawsSoundPool.play(
                context = context,
                resourceId = resourceId,
                volume = volume,
                rate = playbackRate,
                priority = priority,
            )
        ) {
            return
        }

        LudoPawsProceduralAudio.playSfx(
            preset = fallback,
            volume = volume,
            playbackRate = playbackRate,
        )
    }

    /**
     * Resolves both the canonical asset name and Windows copy names such as
     * `lp_sfx_capture_01 (1).wav` without renaming the author's audio files.
     * Android assets may contain spaces and parentheses; only res/raw is
     * restricted to Android resource-safe names.
     */
    @Synchronized
    private fun availableAssetPaths(
        context: Context,
        assetPaths: List<String>,
    ): List<String> {
        val assets = context.applicationContext.assets
        return assetPaths
            .distinct()
            .flatMap { expectedPath ->
                resolvedAssetsByExpectedPath[expectedPath]
                    ?: resolveAssetPaths(assets, expectedPath).also {
                        resolvedAssetsByExpectedPath[expectedPath] = it
                    }
            }
            .distinct()
    }

    private fun resolveAssetPaths(
        assets: AssetManager,
        expectedPath: String,
    ): List<String> {
        val directory = expectedPath.substringBeforeLast('/', "")
        val fileName = expectedPath.substringAfterLast('/')
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
        return if (extension.isBlank()) {
            emptyList()
        } else {
            val stem = fileName.removeSuffix(".$extension")
            val filenamePattern =
                Regex(
                    "^${Regex.escape(stem)}(?: \\(\\d+\\))?\\.${Regex.escape(extension)}$",
                    RegexOption.IGNORE_CASE,
                )
            runCatching {
                assets.list(directory)
                    .orEmpty()
                    .filter { actualName ->
                        // Preserve the author's downloads verbatim, including repeated extensions
                        // and the display-name spelling used for Lumi's footstep recording.
                        val normalized = actualName.replace("Lumi Goat", "goat_lumi", ignoreCase = true)
                        val singleExtension = if (normalized.endsWith(".wav.wav", ignoreCase = true)) {
                            normalized.dropLast(4)
                        } else normalized
                        filenamePattern.matches(singleExtension)
                    }
                    .sortedWith(
                        compareBy<String>({ duplicateFilenameIndex(it) }, { it }),
                    )
                    .map { actualFileName ->
                        if (directory.isBlank()) actualFileName else "$directory/$actualFileName"
                    }
                    .filter { actualPath ->
                        authoredAssetAvailability.getOrPut(actualPath) {
                            runCatching { assets.openFd(actualPath).use { } }.isSuccess
                        }
                    }
            }.getOrDefault(emptyList())
        }

    }

    private fun duplicateFilenameIndex(fileName: String): Int =
        Regex(""" \((\d+)\)(?=\.[^.]+$)""")
            .find(fileName)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0

    private fun assetFamilyKey(assetPaths: List<String>): String =
        assetPaths.firstOrNull()
            .orEmpty()
            .replace(Regex("_\\d{2}\\.(wav|ogg)$"), "_variant.$1")

    internal fun packagedFallbackResourceName(
        preset: LudoPawsProceduralVocal,
    ): String =
        when (preset) {
            LudoPawsProceduralVocal.DOG_YIP -> "lp_vocal_dog_yip"
            LudoPawsProceduralVocal.DOG_WHINE -> "lp_vocal_dog_whine"
            LudoPawsProceduralVocal.DOG_RUFF -> "lp_vocal_dog_ruff"
            LudoPawsProceduralVocal.GOAT_BLEAT -> "lp_vocal_goat_bleat"
            LudoPawsProceduralVocal.GOAT_SOFT_BLEAT -> "lp_vocal_goat_soft_bleat"
            LudoPawsProceduralVocal.DUCK_QUACK -> "lp_vocal_duck_quack"
            LudoPawsProceduralVocal.DUCK_SOFT_QUACK -> "lp_vocal_duck_soft_quack"
            LudoPawsProceduralVocal.CAT_CHIRP -> "lp_vocal_cat_chirp"
            LudoPawsProceduralVocal.CAT_MEW -> "lp_vocal_cat_mew"
            LudoPawsProceduralVocal.CAT_PURR -> "lp_vocal_cat_purr"
            LudoPawsProceduralVocal.CAT_HUFF -> "lp_vocal_cat_huff"
            LudoPawsProceduralVocal.COCK_CROW -> "lp_vocal_cock_crow"
            LudoPawsProceduralVocal.COCK_CLUCK -> "lp_vocal_cock_cluck"
            LudoPawsProceduralVocal.PIG_OINK -> "lp_vocal_pig_oink"
            LudoPawsProceduralVocal.PIG_SQUEAL -> "lp_vocal_pig_squeal"
            LudoPawsProceduralVocal.ELEPHANT_TRUMPET -> "lp_vocal_elephant_trumpet"
            LudoPawsProceduralVocal.ELEPHANT_RUMBLE -> "lp_vocal_elephant_rumble"
            LudoPawsProceduralVocal.HORSE_NEIGH -> "lp_vocal_horse_neigh"
            LudoPawsProceduralVocal.HORSE_SNORT -> "lp_vocal_horse_snort"
            LudoPawsProceduralVocal.RABBIT_SNUFFLE -> "lp_vocal_rabbit_snuffle"
        }

    @Suppress("DEPRECATION")
    private fun rawResourceId(
        context: Context,
        resourceName: String,
    ): Int =
        context.resources.getIdentifier(
            resourceName,
            "raw",
            context.packageName,
        )

    private fun <T> rotate(
        values: List<T>,
        startIndex: Int,
    ): List<T> {
        if (values.size <= 1) return values
        val safe = startIndex.mod(values.size)
        return values.drop(safe) + values.take(safe)
    }
}
