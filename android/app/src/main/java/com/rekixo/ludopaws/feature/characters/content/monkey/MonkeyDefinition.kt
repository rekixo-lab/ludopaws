package com.rekixo.ludopaws.feature.characters.content.monkey

import com.rekixo.ludopaws.feature.characters.domain.model.*

object MonkeyDefinition {
    val content = CharacterDefinition(
        character = AnimalCharacter(
            id = "monkey", displayName = "Monkey", species = AnimalSpecies.MONKEY,
            personality = AnimalPersonality.MISCHIEVOUS, voiceSetId = "monkey_default",
            animationSetId = "monkey_default", fallbackDrawableName = "lp_3d_pawn_placeholder",
        ),
        voiceSet = VoiceSet("monkey_default", AnimalSpecies.MONKEY),
        animationSet = AnimationSet("monkey_default"),
        packId = "starter_paws", storeDisplayOrder = 13,
        reactions = MonkeyReactionMotion,
        idleDurationMillis = 2_800L, homeDurationMillis = 1_450L,
        groundPivotY = -.72f,
        rootYOffsetPerRadius = .06f, footBaselinePerRadius = 1.02f,
    )
}
