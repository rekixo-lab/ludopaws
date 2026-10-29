package com.rekixo.ludopaws.feature.characters.content.panda

import com.rekixo.ludopaws.feature.characters.domain.model.*

object PandaDefinition {
    val content = CharacterDefinition(
        character = AnimalCharacter(
            id = "panda", displayName = "Panda", species = AnimalSpecies.PANDA,
            personality = AnimalPersonality.GENTLE, voiceSetId = "panda_default",
            animationSetId = "panda_default", fallbackDrawableName = "lp_3d_pawn_placeholder",
        ),
        voiceSet = VoiceSet("panda_default", AnimalSpecies.PANDA),
        animationSet = AnimationSet("panda_default"),
        packId = "starter_paws", storeDisplayOrder = 12,
        reactions = PandaReactionMotion,
        idleDurationMillis = 3_200L, homeDurationMillis = 1_650L,
        groundPivotY = -.72f,
        rootYOffsetPerRadius = .06f, footBaselinePerRadius = 1.02f,
    )
}
