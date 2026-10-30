package com.rekixo.ludopaws.feature.characters.content.donkey

import com.rekixo.ludopaws.feature.characters.domain.model.*

object DonkeyDefinition {
    val content=CharacterDefinition(
        character=AnimalCharacter(id="donkey",displayName="Donkey",species=AnimalSpecies.DONKEY,
            personality=AnimalPersonality.PLAYFUL,voiceSetId="donkey_default",animationSetId="donkey_default",
            fallbackDrawableName="lp_3d_pawn_placeholder"),
        packId="starter_paws",storeDisplayOrder=4,
        // Cues accept authored Donkey clips; the audio profile has no borrowed fallback.
        voiceSet=VoiceSet("donkey_default",AnimalSpecies.DONKEY),
        animationSet=AnimationSet("donkey_default"),reactions=DonkeyReactionMotion,
        idleDurationMillis=Donkey3DMotionTimeline.IDLE_DURATION_MILLIS,
        homeDurationMillis=Donkey3DMotionTimeline.HOME_DURATION_MILLIS,
        rootYOffsetPerRadius=.02f,footBaselinePerRadius=1.028f,groundPivotY=-.84f,
    )
}
