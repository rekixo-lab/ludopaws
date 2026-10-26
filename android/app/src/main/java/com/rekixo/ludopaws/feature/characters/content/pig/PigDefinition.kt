package com.rekixo.ludopaws.feature.characters.content.pig

import com.rekixo.ludopaws.feature.characters.domain.model.*

object PigDefinition {
    val content = CharacterDefinition(
        character = AnimalCharacter(
            id = "pig", displayName = "Pig", species = AnimalSpecies.PIG,
            personality = AnimalPersonality.PLAYFUL, voiceSetId = "pig_default",
            animationSetId = "pig_default", fallbackDrawableName = "lp_3d_pawn_placeholder",
        ),
        voiceSet = VoiceSet("pig_default", AnimalSpecies.PIG),
        animationSet = AnimationSet("pig_default"),
        packId = "starter_paws", storeDisplayOrder = 8,
        reactions = PigReactionMotion,
        idleDurationMillis = 2_600L, homeDurationMillis = 1_450L,
        groundPivotY = -.76f,
        rootYOffsetPerRadius = .08f, footBaselinePerRadius = 1.02f,
    )
}
