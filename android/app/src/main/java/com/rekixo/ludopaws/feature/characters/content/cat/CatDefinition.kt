package com.rekixo.ludopaws.feature.characters.content.cat

import com.rekixo.ludopaws.feature.characters.domain.model.*

object CatDefinition {
    val content = CharacterDefinition(
        character = AnimalCharacter(
            id = "cat", displayName = "Cat", species = AnimalSpecies.CAT,
            personality = AnimalPersonality.SASSY, voiceSetId = "cat_default",
            animationSetId = "cat_default", fallbackDrawableName = "lp_3d_pawn_placeholder",
        ),
        voiceSet = VoiceSet("cat_default", AnimalSpecies.CAT),
        animationSet = AnimationSet("cat_default"),
        packId = "starter_paws",
        storeDisplayOrder = 0,
        reactions = CatReactionMotion,
        idleDurationMillis = Cat3DMotionTimeline.IDLE_DURATION_MILLIS,
        homeDurationMillis = Cat3DMotionTimeline.HOME_DURATION_MILLIS,
        groundPivotY = -.84f,
        rootYOffsetPerRadius = .02f, footBaselinePerRadius = 1.18f,
    )
}
