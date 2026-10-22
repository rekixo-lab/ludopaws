package com.rekixo.ludopaws.feature.characters.content.dog

import com.rekixo.ludopaws.feature.characters.domain.model.*

object DogDefinition {
    val content = CharacterDefinition(
        character = AnimalCharacter(
            id = "dog", displayName = "Dog", species = AnimalSpecies.DOG,
            personality = AnimalPersonality.PLAYFUL, voiceSetId = "dog_default",
            animationSetId = "dog_default", fallbackDrawableName = "lp_3d_pawn_placeholder",
        ),
        voiceSet = VoiceSet("dog_default", AnimalSpecies.DOG),
        animationSet = AnimationSet("dog_default"),
        packId = "starter_paws",
        storeDisplayOrder = 1,
        reactions = DogReactionMotion,
        idleDurationMillis = Dog3DMotionTimeline.IDLE_DURATION_MILLIS,
        homeDurationMillis = Dog3DMotionTimeline.HOME_DURATION_MILLIS,
        groundPivotY = -.84f,
        rootYOffsetPerRadius = .01f, footBaselinePerRadius = 1.19f,
    )
}
