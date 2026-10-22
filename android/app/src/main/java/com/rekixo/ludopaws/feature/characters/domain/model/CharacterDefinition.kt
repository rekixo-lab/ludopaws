package com.rekixo.ludopaws.feature.characters.domain.model

import com.rekixo.ludopaws.feature.characters.shared.CharacterReactionProfile

/** Stable content identity and presentation metadata. Store pricing/ownership live elsewhere. */
data class CharacterDefinition(
    val character: AnimalCharacter,
    val packId: String,
    val storeDisplayOrder: Int = 0,
    val voiceSet: VoiceSet,
    val animationSet: AnimationSet,
    val reactions: CharacterReactionProfile,
    val idleDurationMillis: Long,
    val homeDurationMillis: Long,
    val rootYOffsetPerRadius: Float,
    val footBaselinePerRadius: Float,
    val groundPivotY: Float = -.72f,
) {
    init {
        require(packId.isNotBlank())
        require(voiceSet.id == character.voiceSetId && voiceSet.species == character.species)
        require(animationSet.id == character.animationSetId)
        require(idleDurationMillis > 0L && homeDurationMillis > 0L)
        require(groundPivotY.isFinite())
        require(rootYOffsetPerRadius.isFinite() && footBaselinePerRadius.isFinite() && footBaselinePerRadius > 0f)
        require(reactions.durationScale.isFinite() && reactions.durationScale > 0f)
    }
}
