package com.rekixo.ludopaws.feature.characters.content.cock

import com.rekixo.ludopaws.feature.characters.domain.model.*

object CockDefinition {
    val content = CharacterDefinition(
        character = AnimalCharacter(
            id = "cock", displayName = "Cock", species = AnimalSpecies.COCK,
            personality = AnimalPersonality.PROUD, voiceSetId = "cock_default",
            animationSetId = "cock_default", fallbackDrawableName = "lp_3d_pawn_placeholder",
        ),
        voiceSet = VoiceSet("cock_default", AnimalSpecies.COCK),
        animationSet = AnimationSet("cock_default"),
        packId = "starter_paws",
        storeDisplayOrder = 7,
        reactions = CockReactionMotion,
        idleDurationMillis = 2_400L,
        homeDurationMillis = 1_350L,
        groundPivotY = -.78f,
        rootYOffsetPerRadius = .14f, footBaselinePerRadius = .99f,
    )
}
