package com.rekixo.ludopaws.feature.characters.content.rabbit

import com.rekixo.ludopaws.feature.characters.domain.model.*

object RabbitDefinition {
    val content = CharacterDefinition(
        character = AnimalCharacter(
            id = "rabbit", displayName = "Rabbit", species = AnimalSpecies.RABBIT,
            personality = AnimalPersonality.CURIOUS, voiceSetId = "rabbit_default",
            animationSetId = "rabbit_default", fallbackDrawableName = "lp_3d_pawn_placeholder",
        ),
        voiceSet = VoiceSet("rabbit_default", AnimalSpecies.RABBIT),
        animationSet = AnimationSet("rabbit_default"),
        packId = "starter_paws", storeDisplayOrder = 11,
        reactions = RabbitReactionMotion,
        idleDurationMillis = 2_400L, homeDurationMillis = 1_350L,
        groundPivotY = -.73f,
        rootYOffsetPerRadius = .06f, footBaselinePerRadius = 1.02f,
    )
}
