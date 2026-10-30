package com.rekixo.ludopaws.feature.characters.shared

import com.rekixo.ludopaws.feature.characters.rendering.CharacterPose
import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import kotlin.math.*

/** Presentation-only, bounded secondary gestures. Never changes feet, route or turn timing. */
internal object CharacterLivingBehavior {
    private data class Profile(
        val period: Float, val head: Float, val neck: Float,
        val primary: Float, val secondary: Float, val tertiary: Float,
    )

    // Channels are anatomical: ears/wings/arms, tail/beard/trunk, secondary tail.
    private val profiles = mapOf(
        "dog" to Profile(9.5f, 7f, 7f, 12f, 24f, 0f),
        "goat" to Profile(10.8f, 8f, 8f, 12f, 5f, 13f),
        "goat_lumi" to Profile(12.7f, 6f, 5f, 18f, 0f, 10f),
        "duck" to Profile(10.2f, 6f, 11f, 16f, 0f, 0f),
        "cat" to Profile(12.1f, 9f, 6f, 9f, 24f, 0f),
        "donkey" to Profile(13.3f, 7f, 8f, 14f, 21f, 0f),
        "cock" to Profile(8.8f, 8f, 14f, 20f, 8f, 0f),
        "pig" to Profile(11.3f, 7f, 11f, 10f, 20f, 0f),
        "elephant" to Profile(15.1f, 4f, 3f, 12f, 27f, 12f),
        "horse" to Profile(11.8f, 6f, 9f, 13f, 25f, 0f),
        "rabbit" to Profile(9.1f, 8f, 10f, 19f, 7f, 0f),
        "panda" to Profile(14.3f, 6f, 6f, 22f, 9f, 0f),
        "monkey" to Profile(8.3f, 10f, 8f, 28f, 16f, 30f),
    )

    val characterIds: Set<String> get() = profiles.keys

    fun idleDurationMillis(base: Long, seed: Int): Long {
        val speed = when (Math.floorMod(seed, 4)) { 0 -> .89; 1 -> 1.03; 2 -> 1.14; else -> .96 }
        return (base * speed).toLong().coerceAtLeast(1L)
    }

    fun blendIdle(pose: CharacterPose, characterId: String, seconds: Float, seed: Int, weight: Float): CharacterPose {
        val profile = profiles[characterId] ?: return pose
        val strength = weight.coerceIn(0f, 1f)
        if (strength <= 0f) return pose
        val t = seconds + Math.floorMod(seed, 97) * .173f
        val cycle = floor(t / profile.period).toInt()
        val phase = (t / profile.period - floor(t / profile.period))
        // Quiet holds between gestures; endpoints have zero velocity.
        val window = (phase / .42f).coerceIn(0f, 1f)
        val envelope = sin(window * PI.toFloat()).pow(2) * strength
        val wave = sin(window * PI.toFloat() * 2f)
        val kind = Math.floorMod(cycle + seed, 3)
        val glance = if (kind == 0) envelope else envelope * .35f
        val listen = if (kind == 1) envelope else envelope * .25f
        val flourish = if (kind == 2) envelope else envelope * .18f
        return pose.copy(
            headTiltDegrees = pose.headTiltDegrees + profile.head * wave * glance,
            neckPitchDegrees = pose.neckPitchDegrees + profile.neck * (if (kind == 0) 1f else -.35f) * glance,
            primaryAppendageDegrees = pose.primaryAppendageDegrees + profile.primary * wave * listen,
            secondaryAppendageDegrees = pose.secondaryAppendageDegrees + profile.secondary * wave * flourish,
            tertiaryAppendageDegrees = pose.tertiaryAppendageDegrees + profile.tertiary * wave * flourish,
        )
    }

    fun accentReaction(base: LudoPaws3DReactionPose, characterId: String, cue: AnimationCue, progress: Float, variant: Int): LudoPaws3DReactionPose {
        val profile = profiles[characterId] ?: return base
        val p = progress.coerceIn(0f, 1f)
        val envelope = sin(p * PI.toFloat()).coerceAtLeast(0f)
        val wave = sin(p * PI.toFloat() * (if (variant % 2 == 0) 2f else 4f)) * envelope
        val positive = cue == AnimationCue.HAPPY || cue == AnimationCue.EXCITED ||
            cue == AnimationCue.HOME || cue == AnimationCue.VICTORY || cue == AnimationCue.SAFE
        val lively = if (positive) .32f else .14f
        return base.copy(
            primaryAppendageDegrees = base.primaryAppendageDegrees + profile.primary * wave * lively,
            secondaryAppendageDegrees = base.secondaryAppendageDegrees + profile.secondary * wave * lively,
            tertiaryAppendageDegrees = base.tertiaryAppendageDegrees + profile.tertiary * wave * lively,
            neckPitchDegrees = base.neckPitchDegrees + profile.neck * envelope * (if (positive) -.25f else .25f),
        )
    }
}
