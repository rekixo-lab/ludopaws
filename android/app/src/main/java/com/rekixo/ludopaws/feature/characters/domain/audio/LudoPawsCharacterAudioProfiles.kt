package com.rekixo.ludopaws.feature.characters.domain.audio

import com.rekixo.ludopaws.core.audio.LudoPawsAudioCatalog

enum class LudoPawsFootstepStyle { PAW, HOOF, WEB }

/**
 * Character audio registration. Keep this catalog in parity with CharacterDefinitions.
 * Names are stable save/store IDs, not display labels. No gameplay state is stored here.
 *
 * Existing starter paths and fallbacks stay unchanged. Later characters may supply
 * their own /audio/sfx/gameplay/movement/step/<id>/ files without Kotlin switches.
 */
data class LudoPawsCharacterAudioProfile(
    val characterId: String,
    val voiceAssetOwnerId: String?,
    val footstepStyle: LudoPawsFootstepStyle,
    val fallbackStepPaths: List<String>,
    val stepVolume: Float = .30f,
) {
    fun stepAssetFamilies(): List<List<String>> {
        val primary = when (characterId) {
            "dog", "goat", "duck", "cat" -> fallbackStepPaths
            else -> (1..3).map { variant ->
                val suffix = variant.toString().padStart(2, '0')
                "audio/sfx/gameplay/movement/step/$characterId/lp_sfx_step_${characterId}_$suffix.wav"
            }
        }
        return if (primary == fallbackStepPaths) {
            listOf(primary)
        } else {
            listOf(primary, fallbackStepPaths)
        }
    }
}

object LudoPawsCharacterAudioProfiles {
    private val paw = LudoPawsAudioCatalog.Sfx.MOVE_STEP_DOG
    private val hoof = LudoPawsAudioCatalog.Sfx.MOVE_STEP_GOAT
    private val web = LudoPawsAudioCatalog.Sfx.MOVE_STEP_DUCK
    private val catPaw = LudoPawsAudioCatalog.Sfx.MOVE_STEP_CAT

    val all: Map<String, LudoPawsCharacterAudioProfile> = GeneratedCharacterAudioProfiles.all

    val supportedVoiceAssetIds: Set<String> = all.values.mapNotNull { it.voiceAssetOwnerId }.toSet()

    fun forCharacter(characterId: String?): LudoPawsCharacterAudioProfile =
        all[characterId] ?: all.getValue("dog")
}
