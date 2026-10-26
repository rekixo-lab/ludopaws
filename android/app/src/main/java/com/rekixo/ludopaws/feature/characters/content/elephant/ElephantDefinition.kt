package com.rekixo.ludopaws.feature.characters.content.elephant

import com.rekixo.ludopaws.feature.characters.domain.model.*

object ElephantDefinition {
    val content = CharacterDefinition(
        character = AnimalCharacter(
            id = "elephant", displayName = "Elephant", species = AnimalSpecies.ELEPHANT,
            personality = AnimalPersonality.GENTLE, voiceSetId = "elephant_default",
            animationSetId = "elephant_default", fallbackDrawableName = "lp_3d_pawn_placeholder",
        ),
        voiceSet = VoiceSet("elephant_default", AnimalSpecies.ELEPHANT),
        animationSet = AnimationSet("elephant_default"),
        packId = "starter_paws", storeDisplayOrder = 9,
        reactions = ElephantReactionMotion,
        idleDurationMillis = 3_000L, homeDurationMillis = 1_600L,
        groundPivotY = -.84f,
        rootYOffsetPerRadius = .06f, footBaselinePerRadius = 1.04f,
    )
}
