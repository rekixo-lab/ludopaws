package com.rekixo.ludopaws.feature.characters.content.horse

import com.rekixo.ludopaws.feature.characters.domain.model.*

object HorseDefinition {
    val content = CharacterDefinition(
        character = AnimalCharacter(
            id = "horse", displayName = "Horse", species = AnimalSpecies.HORSE,
            personality = AnimalPersonality.BRAVE, voiceSetId = "horse_default",
            animationSetId = "horse_default", fallbackDrawableName = "lp_3d_pawn_placeholder",
        ),
        voiceSet = VoiceSet("horse_default", AnimalSpecies.HORSE),
        animationSet = AnimationSet("horse_default"),
        packId = "starter_paws", storeDisplayOrder = 10,
        reactions = HorseReactionMotion,
        idleDurationMillis = 2_800L, homeDurationMillis = 1_450L,
        groundPivotY = -.855f,
        rootYOffsetPerRadius = .04f, footBaselinePerRadius = 1.04f,
    )
}
