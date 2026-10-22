package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.domain.catalog.CharacterDefinitions
import com.rekixo.ludopaws.feature.characters.domain.catalog.LudoPawsCharacterCatalog
import com.rekixo.ludopaws.feature.characters.shared.CharacterReactionFrame
import com.rekixo.ludopaws.feature.characters.shared.CharacterReactionProfile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

data class LudoPaws3DReactionPose(
    val liftY: Float = 0f,
    val bodyYawDegrees: Float = 0f,
    val headTiltDegrees: Float = 0f,
    val primaryAppendageDegrees: Float = 0f,
    val secondaryAppendageDegrees: Float = 0f,
    val tertiaryAppendageDegrees: Float = 0f,
    val scale: Float = 1f,
    val bodyPitchDegrees: Float = 0f,
    val neckPitchDegrees: Float = 0f,
    val jawOpen: Float = 0f,
)

internal data class LudoPaws3DActiveReaction(
    val cue: AnimationCue,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val priority: Int,
)

/**
 * Pure additive body-language layer for code-generated 3D animal pawns.
 *
 * primary/secondary/tertiary are intentionally generic so the renderer maps
 * them to each animal's anatomy: Dog ears/tail, Goat ears/beard/tail, Duck
 * wings and Cat ears/tail. HOME never adds another full spin because the base
 * movement timeline already owns the home-arrival rotation.
 */
object LudoPaws3DReactionMotion {
    fun durationMillis(species: LudoPaws3DSpecies, cue: AnimationCue): Long =
        durationMillis(species.name.lowercase(), cue)

    fun durationMillis(characterId: String, cue: AnimationCue): Long {
        val base =
            when (cue) {
                AnimationCue.IDLE -> 520L
                AnimationCue.EXCITED -> 620L
                AnimationCue.HAPPY -> 680L
                AnimationCue.SAD -> 760L
                AnimationCue.ANGRY -> 650L
                AnimationCue.NERVOUS -> 720L
                AnimationCue.CAPTURE -> 700L
                AnimationCue.CAPTURED -> 620L
                AnimationCue.SAFE -> 640L
                AnimationCue.HOME -> 900L
                AnimationCue.VICTORY -> 1_250L
                AnimationCue.DEFEAT -> 950L
            }
        return (base * profile(characterId).durationScale).toLong()
    }

    fun sample(species: LudoPaws3DSpecies, cue: AnimationCue, progress: Float): LudoPaws3DReactionPose =
        sample(species.name.lowercase(), cue, progress)

    fun sample(characterId: String, cue: AnimationCue, progress: Float): LudoPaws3DReactionPose {
        val raw = progress.coerceIn(0f, 1f)
        val p = if (cue == AnimationCue.CAPTURED || cue == AnimationCue.NERVOUS) {
            if (raw < .18f) .5f * (raw / .18f) else .5f + .5f * (raw - .18f) / .82f
        } else raw
        val arc = sin(p * PI).toFloat().coerceAtLeast(0f)
        val wave = sin(p * PI * 2.0).toFloat()
        val fast = sin(p * PI * 5.0).toFloat()
        val bounce = abs(sin(p * PI * 2.0).toFloat()) * (1f - p * .20f)

        return profile(characterId).sample(cue, CharacterReactionFrame(p, arc, wave, fast, bounce))
    }

    private val profiles by lazy {
        CharacterDefinitions.all.associate { it.character.id to it.reactions }
    }

    private fun profile(characterId: String): CharacterReactionProfile =
        profiles[LudoPawsCharacterCatalog.canonicalCharacterId(characterId)]
            ?: profiles.getValue(LudoPawsCharacterCatalog.DEFAULT_CHARACTER_ID)
}
