package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import kotlin.math.PI
import kotlin.math.sin

/** Stable per-pawn choices. No randomness from this policy enters gameplay or audio. */
internal object LudoPawsPawnReactionVariation {
    data class Choice(val cue: AnimationCue, val delayMillis: Long, val variant: Int, val durationScale: Float)

    fun choose(cue: AnimationCue, tokenIndex: Int, seed: Int, group: Boolean): Choice {
        val rank = Math.floorMod(tokenIndex - Math.floorMod(seed, 4), 4)
        val variedCue = if (!group) cue else when (cue) {
            AnimationCue.VICTORY -> when (rank) {
                1 -> AnimationCue.HAPPY
                2 -> AnimationCue.EXCITED
                else -> AnimationCue.VICTORY
            }
            AnimationCue.DEFEAT -> if (rank % 2 == 0) AnimationCue.DEFEAT else AnimationCue.SAD
            else -> cue
        }
        return Choice(
            variedCue, if (group) rank * 90L else 0L, rank,
            if (group) when (rank) { 0 -> .94f; 1 -> 1.06f; 2 -> 1.13f; else -> 1f } else 1f,
        )
    }

    fun pose(base: LudoPaws3DReactionPose, progress: Float, variant: Int): LudoPaws3DReactionPose {
        val v = Math.floorMod(variant, 4)
        val envelope = sin(progress.coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)
        val strength = when (v) { 0 -> .88f; 1 -> 1.04f; 2 -> .96f; else -> 1.12f }
        val direction = if (v % 2 == 0) 1f else -1f
        return base.copy(
            liftY = base.liftY * strength,
            headTiltDegrees = base.headTiltDegrees * strength + direction * (3f + v) * envelope,
            primaryAppendageDegrees = base.primaryAppendageDegrees * strength,
            secondaryAppendageDegrees = base.secondaryAppendageDegrees * (1.12f - v * .07f),
            tertiaryAppendageDegrees = base.tertiaryAppendageDegrees * strength,
            neckPitchDegrees = base.neckPitchDegrees + direction * 3f * envelope,
            // Full-turn yaw stays intact; no fractional-spin snap when a cue finishes.
        )
    }
}
