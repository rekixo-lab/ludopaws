package com.rekixo.ludopaws

/** Field-compatible copy of the data class from LudoPaws3DReactionMotion.kt. */
data class LudoPaws3DReactionPose(
    val liftY: Float = 0f, val bodyYawDegrees: Float = 0f, val headTiltDegrees: Float = 0f,
    val primaryAppendageDegrees: Float = 0f, val secondaryAppendageDegrees: Float = 0f,
    val tertiaryAppendageDegrees: Float = 0f, val scale: Float = 1f, val bodyPitchDegrees: Float = 0f,
    val neckPitchDegrees: Float = 0f, val jawOpen: Float = 0f,
)
