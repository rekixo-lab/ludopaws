package com.rekixo.ludopaws.feature.characters.content.goat

import com.rekixo.ludopaws.feature.characters.domain.model.*

object GoatDefinition {
    val content = CharacterDefinition(
        character = AnimalCharacter(
            id = "goat", displayName = "Goat", species = AnimalSpecies.GOAT,
            personality = AnimalPersonality.CURIOUS, voiceSetId = "goat_default",
            animationSetId = "goat_default", fallbackDrawableName = "lp_3d_pawn_placeholder",
        ),
        voiceSet = VoiceSet("goat_default", AnimalSpecies.GOAT),
        animationSet = AnimationSet("goat_default"),
        packId = "starter_paws",
        storeDisplayOrder = 2,
        reactions = GoatReactionMotion,
        idleDurationMillis = Goat3DMotionTimeline.IDLE_DURATION_MILLIS,
        homeDurationMillis = Goat3DMotionTimeline.HOME_DURATION_MILLIS,
        groundPivotY = -.954f,
        rootYOffsetPerRadius = .02f, footBaselinePerRadius = 1.17f,
    )
}
