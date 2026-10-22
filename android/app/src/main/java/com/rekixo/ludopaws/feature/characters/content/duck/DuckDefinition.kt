package com.rekixo.ludopaws.feature.characters.content.duck

import com.rekixo.ludopaws.feature.characters.domain.model.*

object DuckDefinition {
    val content = CharacterDefinition(
        character = AnimalCharacter(
            id = "duck", displayName = "Duck", species = AnimalSpecies.DUCK,
            personality = AnimalPersonality.CHEERFUL, voiceSetId = "duck_default",
            animationSetId = "duck_default", fallbackDrawableName = "lp_3d_pawn_placeholder",
        ),
        voiceSet = VoiceSet("duck_default", AnimalSpecies.DUCK),
        animationSet = AnimationSet("duck_default"),
        packId = "starter_paws",
        storeDisplayOrder = 3,
        reactions = DuckReactionMotion,
        idleDurationMillis = Duck3DMotionTimeline.IDLE_DURATION_MILLIS,
        homeDurationMillis = Duck3DMotionTimeline.HOME_DURATION_MILLIS,
        groundPivotY = -.755f,
        rootYOffsetPerRadius = .14f, footBaselinePerRadius = .99f,
    )
}
