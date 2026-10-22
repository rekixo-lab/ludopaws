package com.rekixo.ludopaws.feature.characters.rendering

import com.rekixo.ludopaws.LudoPawsTeamColor

enum class CharacterMotion { IDLE, HOP, HOME }

/** A route carries its landing crouch into the next jump; only its final contact settles. */
enum class CharacterHopPlayback { ISOLATED, START, CHAIN, SETTLE }

/** Anatomy-independent pose exchanged between the scene and each character implementation. */
internal data class CharacterPose(
    val liftY: Float = 0f,
    val bodyScaleY: Float = 1f,
    val bodyYawDegrees: Float = 0f,
    val headTiltDegrees: Float = 0f,
    val primaryAppendageDegrees: Float = 0f,
    val secondaryAppendageDegrees: Float = 0f,
    val tertiaryAppendageDegrees: Float = 0f,
    val legSwingDegrees: Float = 0f,
    val legTuckDegrees: Float = 0f,
    val legPaddleDegrees: Float = 0f,
    val bodyPitchDegrees: Float = 0f,
    val bodyRollDegrees: Float = 0f,
    val neckPitchDegrees: Float = 0f,
    val bodyCrouch: Float = 0f,
    val jawOpen: Float = 0f,
    // Optional continuous locomotion phase; consumed by species with articulated foot paths.
    val stridePhase: Float = 0f,
    val strideWeight: Float = 0f,
    // Optional fore/hind pair channels; null preserves the other characters' gait.
    val frontLegTuckDegrees: Float? = null,
    val rearLegTuckDegrees: Float? = null,
    val frontLegSwingDegrees: Float? = null,
    val rearLegSwingDegrees: Float? = null,
)

internal interface CharacterRenderer {
    /** Remaps within one path step only; game route and total step timing stay authoritative. */
    fun stepTravelProgress(progress: Float): Float = progress
    fun sample(
        motion: CharacterMotion,
        progress: Float,
        hopPlayback: CharacterHopPlayback = CharacterHopPlayback.ISOLATED,
    ): CharacterPose
    fun draw(context: CharacterDrawContext, parent: FloatArray, pose: CharacterPose, teamColor: LudoPawsTeamColor)
}
