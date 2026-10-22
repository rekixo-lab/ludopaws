package com.rekixo.ludopaws.feature.characters.shared

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue

/** Pure input shared by character-specific reaction profiles. */
data class CharacterReactionFrame(
    val p: Float, val arc: Float, val wave: Float, val fast: Float, val bounce: Float,
)
interface CharacterReactionProfile {
    val durationScale: Float
    fun sample(cue: AnimationCue, frame: CharacterReactionFrame): LudoPaws3DReactionPose
}
