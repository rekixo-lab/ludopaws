package com.rekixo.ludopaws.feature.characters.domain.audio

import com.rekixo.ludopaws.feature.characters.domain.model.AnimalSpecies
import com.rekixo.ludopaws.feature.characters.domain.model.VoiceCue
import com.rekixo.ludopaws.feature.characters.domain.reaction.GameMomentType
import com.rekixo.ludopaws.feature.characters.domain.reaction.LudoPawsReaction

enum class LudoPawsProceduralVocal {
    DOG_YIP,
    DOG_WHINE,
    DOG_RUFF,
    GOAT_BLEAT,
    GOAT_SOFT_BLEAT,
    DUCK_QUACK,
    DUCK_SOFT_QUACK,
    CAT_CHIRP,
    CAT_MEW,
    CAT_PURR,
    CAT_HUFF,
    COCK_CROW,
    COCK_CLUCK,
    PIG_OINK,
    PIG_SQUEAL,
    ELEPHANT_TRUMPET,
    ELEPHANT_RUMBLE,
}

data class LudoPawsVocalPlan(
    val rawResourceNames: List<String>,
    val fallback: LudoPawsProceduralVocal,
    val volume: Float,
    val playbackRate: Float,
    val probabilityPercent: Int,
    val delayMillis: Long = 0L,
) {
    init {
        require(rawResourceNames.isNotEmpty())
        require(volume in 0f..1f)
        require(playbackRate in .65f..1.45f)
        require(probabilityPercent in 0..100)
        require(delayMillis >= 0L)
    }
}

/**
 * Pure contract for non-verbal animal vocals.
 *
 * Packaged overrides use the stable naming convention:
 *   lp_vocal_<character>_<cue>_01.ogg/.wav
 *   lp_vocal_<character>_<cue>_02.ogg/.wav
 *
 * Android resource names do not include the extension, so replacing or adding a
 * clip never requires gameplay changes. If no packaged clip exists, the Android
 * audio layer falls back to a small procedural, copyright-safe vocal.
 */
object LudoPawsReactionAudioProfile {
    private const val VARIANT_COUNT = 2

    fun plan(
        characterId: String,
        species: AnimalSpecies,
        reaction: LudoPawsReaction,
    ): LudoPawsVocalPlan? {
        val cue = reaction.voiceCue
        if (cue == VoiceCue.SILENT) {
            return null
        }

        val probability =
            probabilityPercent(
                cue = cue,
                momentType = reaction.momentType,
            )
        if (probability <= 0) {
            return null
        }

        return LudoPawsVocalPlan(
            rawResourceNames =
                resourceNames(
                    characterId = characterId,
                    cue = cue,
                ),
            fallback =
                fallbackFor(
                    species = species,
                    cue = cue,
                ),
            volume = volumeFor(cue),
            playbackRate = playbackRateFor(species, cue),
            probabilityPercent = probability,
            delayMillis = delayFor(reaction.momentType),
        )
    }

    fun shouldPlay(
        plan: LudoPawsVocalPlan,
        reactionKey: String,
    ): Boolean {
        if (plan.probabilityPercent >= 100) return true
        if (plan.probabilityPercent <= 0) return false

        val bucket =
            stablePositiveHash(reactionKey) % 100
        return bucket < plan.probabilityPercent
    }

    fun resourceNames(
        characterId: String,
        cue: VoiceCue,
    ): List<String> =
        (1..VARIANT_COUNT).map { variant ->
            "lp_vocal_${characterId}_${cue.wireName}_${variant.toString().padStart(2, '0')}"
        }

    fun deterministicVariantIndex(
        reactionKey: String,
        variantCount: Int,
    ): Int {
        if (variantCount <= 1) return 0
        return stablePositiveHash("variant:$reactionKey") % variantCount
    }

    private fun probabilityPercent(
        cue: VoiceCue,
        momentType: GameMomentType?,
    ): Int =
        when (cue) {
            VoiceCue.SILENT -> 0
            VoiceCue.SIX -> 68
            VoiceCue.YARD_EXIT -> 48
            VoiceCue.CAPTURE,
            VoiceCue.CAPTURED,
            VoiceCue.HOME,
            VoiceCue.THIRD_SIX,
            VoiceCue.VICTORY,
            VoiceCue.DEFEAT,
            -> 100

            VoiceCue.SAFE -> 36
            VoiceCue.HOME_LANE -> 24
            VoiceCue.FRUSTRATED ->
                when (momentType) {
                    GameMomentType.POOR_ROLL_STREAK -> 100
                    GameMomentType.EXACT_HOME_MISS -> 48
                    GameMomentType.NO_LEGAL_MOVE -> 34
                    else -> 45
                }

            VoiceCue.IDLE -> 28
            VoiceCue.NERVOUS -> 45
            VoiceCue.PROUD -> 18
        }

    private fun fallbackFor(
        species: AnimalSpecies,
        cue: VoiceCue,
    ): LudoPawsProceduralVocal =
        when (species) {
            AnimalSpecies.DOG ->
                when (cue) {
                    VoiceCue.CAPTURED,
                    VoiceCue.FRUSTRATED,
                    VoiceCue.DEFEAT,
                    -> LudoPawsProceduralVocal.DOG_WHINE

                    VoiceCue.THIRD_SIX,
                    VoiceCue.NERVOUS,
                    -> LudoPawsProceduralVocal.DOG_RUFF

                    else -> LudoPawsProceduralVocal.DOG_YIP
                }

            AnimalSpecies.GOAT,
            AnimalSpecies.SHEEP,
            ->
                when (cue) {
                    VoiceCue.CAPTURED,
                    VoiceCue.FRUSTRATED,
                    VoiceCue.DEFEAT,
                    VoiceCue.NERVOUS,
                    -> LudoPawsProceduralVocal.GOAT_SOFT_BLEAT

                    else -> LudoPawsProceduralVocal.GOAT_BLEAT
                }

            AnimalSpecies.DUCK,
            AnimalSpecies.CHICK,
            ->
                when (cue) {
                    VoiceCue.CAPTURED,
                    VoiceCue.FRUSTRATED,
                    VoiceCue.DEFEAT,
                    VoiceCue.NERVOUS,
                    -> LudoPawsProceduralVocal.DUCK_SOFT_QUACK

                    else -> LudoPawsProceduralVocal.DUCK_QUACK
                }

            AnimalSpecies.ELEPHANT ->
                when (cue) {
                    VoiceCue.SIX, VoiceCue.YARD_EXIT, VoiceCue.CAPTURE,
                    VoiceCue.CAPTURED, VoiceCue.HOME, VoiceCue.VICTORY -> LudoPawsProceduralVocal.ELEPHANT_TRUMPET
                    else -> LudoPawsProceduralVocal.ELEPHANT_RUMBLE
                }

            AnimalSpecies.PIG ->
                when (cue) {
                    VoiceCue.CAPTURED, VoiceCue.THIRD_SIX,
                    VoiceCue.VICTORY -> LudoPawsProceduralVocal.PIG_SQUEAL
                    else -> LudoPawsProceduralVocal.PIG_OINK
                }

            AnimalSpecies.COCK ->
                when (cue) {
                    VoiceCue.SIX, VoiceCue.YARD_EXIT, VoiceCue.HOME,
                    VoiceCue.PROUD, VoiceCue.VICTORY -> LudoPawsProceduralVocal.COCK_CROW
                    else -> LudoPawsProceduralVocal.COCK_CLUCK
                }

            AnimalSpecies.CAT ->
                when (cue) {
                    VoiceCue.CAPTURED,
                    VoiceCue.FRUSTRATED,
                    VoiceCue.DEFEAT,
                    -> LudoPawsProceduralVocal.CAT_MEW

                    VoiceCue.SAFE,
                    VoiceCue.HOME,
                    VoiceCue.VICTORY,
                    VoiceCue.PROUD,
                    -> LudoPawsProceduralVocal.CAT_PURR

                    VoiceCue.THIRD_SIX,
                    VoiceCue.NERVOUS,
                    -> LudoPawsProceduralVocal.CAT_HUFF

                    else -> LudoPawsProceduralVocal.CAT_CHIRP
                }

            else -> LudoPawsProceduralVocal.DOG_YIP
        }

    private fun volumeFor(cue: VoiceCue): Float =
        when (cue) {
            VoiceCue.IDLE,
            VoiceCue.PROUD,
            -> .34f

            VoiceCue.SAFE,
            VoiceCue.HOME_LANE,
            VoiceCue.NERVOUS,
            -> .42f

            VoiceCue.DEFEAT,
            VoiceCue.FRUSTRATED,
            -> .46f

            VoiceCue.SIX,
            VoiceCue.YARD_EXIT,
            VoiceCue.HOME,
            VoiceCue.VICTORY,
            -> .55f

            VoiceCue.CAPTURE,
            VoiceCue.CAPTURED,
            VoiceCue.THIRD_SIX,
            -> .58f

            VoiceCue.SILENT -> 0f
        }

    private fun playbackRateFor(
        species: AnimalSpecies,
        cue: VoiceCue,
    ): Float {
        val base =
            when (species) {
                AnimalSpecies.DUCK,
                AnimalSpecies.CHICK,
                -> 1.06f

                AnimalSpecies.CAT -> 1.03f
                AnimalSpecies.DOG -> 1.00f
                AnimalSpecies.GOAT,
                AnimalSpecies.SHEEP,
                -> .97f

                else -> 1.00f
            }
        return when (cue) {
            VoiceCue.VICTORY,
            VoiceCue.SIX,
            -> (base + .05f).coerceAtMost(1.45f)

            VoiceCue.DEFEAT,
            VoiceCue.FRUSTRATED,
            -> (base - .05f).coerceAtLeast(.65f)

            else -> base
        }
    }

    private fun delayFor(momentType: GameMomentType?): Long =
        when (momentType) {
            GameMomentType.CAPTURE_MADE -> 80L
            GameMomentType.TOKEN_CAPTURED -> 155L
            else -> 0L
        }

    private fun stablePositiveHash(value: String): Int {
        var hash = 17
        value.forEach { char ->
            hash = hash * 31 + char.code
        }
        return hash and Int.MAX_VALUE
    }
}
