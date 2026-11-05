package com.rekixo.ludopaws.feature.characters.content.giraffe

import com.rekixo.ludopaws.feature.characters.content.donkey.DonkeyLegRig
import com.rekixo.ludopaws.feature.characters.domain.model.*

object GiraffeDefinition {
    internal const val MODEL_SCALE = .84f
    internal const val LEG_Y_SCALE = 1.24f
    internal const val LEG_Y_ANCHOR = -.22f
    internal const val HOOF_BOTTOM = -.09f
    internal const val HOOF_OFFSET = -.02f
    private const val GROUND_Y = LEG_Y_ANCHOR + (DonkeyLegRig.GROUND_ANKLE_Y - LEG_Y_ANCHOR) * LEG_Y_SCALE +
        HOOF_OFFSET + HOOF_BOTTOM

    // Measured from grounded hooves to the tips of the ossicones in the neutral rig.
    internal const val SILHOUETTE_HEIGHT = (2.07f - GROUND_Y) * MODEL_SCALE

    val content = CharacterDefinition(
        character = AnimalCharacter(
            id = "giraffe", displayName = "Giraffe", species = AnimalSpecies.GIRAFFE,
            personality = AnimalPersonality.CURIOUS, voiceSetId = "giraffe_default",
            animationSetId = "giraffe_default", fallbackDrawableName = "lp_3d_pawn_placeholder",
        ),
        voiceSet = VoiceSet("giraffe_default", AnimalSpecies.GIRAFFE),
        animationSet = AnimationSet("giraffe_default"),
        packId = "starter_paws", storeDisplayOrder = 14,
        reactions = GiraffeReactionMotion,
        idleDurationMillis = 3_400L, homeDurationMillis = 1_500L,
        rootYOffsetPerRadius = .04f, footBaselinePerRadius = 1.06f,
        groundPivotY = GROUND_Y * MODEL_SCALE,
    )
}
