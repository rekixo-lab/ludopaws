package com.rekixo.ludopaws.feature.characters.domain.model

enum class AnimalSpecies {
    DUCK,
    SQUIRREL,
    HEDGEHOG,
    SHEEP,
    DEER,
    GIRAFFE,
    ZEBRA,
    HIPPO,
    TIGER,
    COW,
    HORSE,
    PIG,
    CHICK,
    OWL,
    KOALA,
    TURTLE,
    PANDA,
    MONKEY,
    FOX,
    BEAR,
    ELEPHANT,
    DONKEY,
    RABBIT,
    FROG,
    DOG,
    GOAT,
    CAT,
    COCK,
}

enum class AnimalPersonality {
    CHEERFUL,
    MISCHIEVOUS,
    SHY,
    GENTLE,
    BOLD,
    PLAYFUL,
    CURIOUS,
    CALM,
    PROUD,
    NERVOUS,
    BRAVE,
    SASSY,
}

/** Semantic non-verbal vocal intent. SILENT keeps visual reactions voice-free. */
enum class VoiceCue(
    val wireName: String,
) {
    SILENT("silent"),
    SIX("six"),
    YARD_EXIT("yard_exit"),
    CAPTURE("capture"),
    CAPTURED("captured"),
    SAFE("safe"),
    HOME_LANE("home_lane"),
    HOME("home"),
    FRUSTRATED("frustrated"),
    THIRD_SIX("third_six"),
    IDLE("idle"),
    NERVOUS("nervous"),
    PROUD("proud"),
    VICTORY("victory"),
    DEFEAT("defeat"),
}

enum class AnimationCue {
    IDLE,
    EXCITED,
    HAPPY,
    SAD,
    ANGRY,
    NERVOUS,
    CAPTURE,
    CAPTURED,
    SAFE,
    HOME,
    VICTORY,
    DEFEAT,
}

data class VoiceSet(
    val id: String,
    val species: AnimalSpecies,
    val supportedCues: Set<VoiceCue> = VoiceCue.entries.toSet(),
) {
    init {
        require(LudoPawsDomainIds.isSafe(id)) {
            "Invalid voice-set id: $id"
        }
        require(supportedCues.isNotEmpty()) {
            "Voice set $id must support at least one cue"
        }
    }
}

data class AnimationSet(
    val id: String,
    val supportedCues: Set<AnimationCue> = AnimationCue.entries.toSet(),
) {
    init {
        require(LudoPawsDomainIds.isSafe(id)) {
            "Invalid animation-set id: $id"
        }
        require(supportedCues.isNotEmpty()) {
            "Animation set $id must support at least one cue"
        }
    }
}

data class AnimalCharacter(
    val id: String,
    val displayName: String,
    val species: AnimalSpecies,
    val personality: AnimalPersonality,
    val voiceSetId: String,
    val animationSetId: String,
    val fallbackDrawableName: String,
) {
    init {
        require(LudoPawsDomainIds.isSafe(id)) {
            "Invalid character id: $id"
        }
        require(displayName.isNotBlank()) {
            "Character $id must have a display name"
        }
        require(LudoPawsDomainIds.isSafe(voiceSetId)) {
            "Invalid voice-set id for $id: $voiceSetId"
        }
        require(LudoPawsDomainIds.isSafe(animationSetId)) {
            "Invalid animation-set id for $id: $animationSetId"
        }
        require(FALLBACK_DRAWABLE.matches(fallbackDrawableName)) {
            "Invalid fallback drawable for $id: $fallbackDrawableName"
        }
    }

    private companion object {
        val FALLBACK_DRAWABLE =
            Regex("^lp_[a-z0-9_]+$")
    }
}

data class AnimalPack(
    val id: String,
    val displayName: String,
    val characterIds: List<String>,
    val starter: Boolean = false,
) {
    init {
        require(LudoPawsDomainIds.isSafe(id)) {
            "Invalid pack id: $id"
        }
        require(displayName.isNotBlank()) {
            "Pack $id must have a display name"
        }
        require(characterIds.isNotEmpty()) {
            "Pack $id must contain at least one character"
        }
        require(characterIds.distinct().size == characterIds.size) {
            "Pack $id contains duplicate character ids"
        }
        require(characterIds.all(LudoPawsDomainIds::isSafe)) {
            "Pack $id contains an invalid character id"
        }
    }
}

data class CharacterSelection(
    val packId: String,
    val characterId: String,
)

internal object LudoPawsDomainIds {
    private val pattern =
        Regex("^[a-z][a-z0-9_]{1,31}$")

    fun isSafe(
        value: String,
    ): Boolean =
        pattern.matches(value)
}
