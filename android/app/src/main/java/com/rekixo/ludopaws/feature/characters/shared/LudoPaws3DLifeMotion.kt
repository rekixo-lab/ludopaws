package com.rekixo.ludopaws.feature.characters.shared

import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.feature.characters.rendering.CharacterPose

import kotlin.math.*

/** Renderer-owned presentation state; never changes gameplay timing or positions. */
internal class LudoPaws3DLifeMotion {
    private class Spring {
        var value = 0f
        private var velocity = 0f
        fun step(target: Float, dt: Float, stiffness: Float = 110f): Float {
            // Substeps keep secondary motion stable at both 30 and 60 fps.
            val count = ceil(dt / .008f).toInt().coerceAtLeast(1)
            val h = dt / count
            repeat(count) {
                velocity += ((target - value) * stiffness - velocity * sqrt(stiffness) * 1.65f) * h
                value += velocity * h
            }
            return value
        }
    }
    private val channels = Array(10) { Spring() }
    private val primary = Spring()
    private val secondary = Spring()
    private val tertiary = Spring()
    private val stance = Spring()
    private var frameDt = 1f / 60f
    fun followPrimary(target: Float) = primary.step(target, frameDt, 120f)
    fun followSecondary(target: Float) = secondary.step(target, frameDt, 72f)
    fun followTertiary(target: Float) = tertiary.step(target, frameDt, 90f)
    // Separate from appendages: changing support from two paws to four takes time.
    fun followStance(target: Float) = stance.step(target, frameDt, 24f).coerceIn(0f, 1f)
    private val gaze = Spring()
    private val gazeVertical = Spring()
    private var lastMillis = 0L
    var timeSeconds = 0f; private set
    var isIdle = false; private set
    var idleWeight = 0f; private set
    var blink = 1f; private set
    var breath = 0f; private set
    var headTilt = 0f; private set
    var headPitch = 0f; private set
    var mouthOpen = 0f; private set
    var shake = 0f; private set
    var gazeX = 0f; private set
    var gazeY = 0f; private set
    private var behaviorSeed = 0
    var reducedMotion = false

    fun idlePose(pose: CharacterPose, characterId: String? = null): CharacterPose {
        val faded = pose.copy(
            bodyYawDegrees = pose.bodyYawDegrees * idleWeight,
            headTiltDegrees = pose.headTiltDegrees * idleWeight,
            bodyPitchDegrees = pose.bodyPitchDegrees * idleWeight,
            bodyRollDegrees = pose.bodyRollDegrees * idleWeight,
            neckPitchDegrees = pose.neckPitchDegrees * idleWeight,
            primaryAppendageDegrees = pose.primaryAppendageDegrees * idleWeight,
            secondaryAppendageDegrees = pose.secondaryAppendageDegrees * idleWeight,
            tertiaryAppendageDegrees = pose.tertiaryAppendageDegrees * idleWeight,
        )
        return if (characterId == null) faded else CharacterLivingBehavior.blendIdle(
            faded, characterId, timeSeconds, behaviorSeed, idleWeight,
        )
    }

    fun update(now: Long, seed: Int, idle: Boolean, targetX: Float, targetY: Float,
               target: LudoPaws3DReactionPose): LudoPaws3DReactionPose {
        val dt = if (lastMillis == 0L) 1f / 60f else ((now - lastMillis) / 1000f).coerceIn(0f, .064f)
        lastMillis = now
        frameDt = dt
        behaviorSeed = seed
        val t = if (reducedMotion) 0f else (now % 3_600_000L) / 1000f + (seed and 65535) * .0137f
        timeSeconds = t
        isIdle = idle
        // Landing must not enable a random mid-gesture head/body shake in one frame.
        val idleTarget=if(idle && !reducedMotion) 1f else 0f
        val idleTau=if(idle) .25f else .08f
        idleWeight += (idleTarget-idleWeight)*(1f-exp(-dt/idleTau))
        breath = sin(t * 2.1f) * .010f + sin(t * 4.2f + .7f) * .002f
        // Unequal intervals and occasional double blinks, independently phased per pawn.
        val blinkTime = t % (3.7f + (seed and 7) * .17f)
        fun closure(p: Float) = if (p in 0f..1f) sin(PI.toFloat() * p).pow(2) else 0f
        blink = (1f - max(closure(blinkTime / .19f),
            if (seed and 3 == 0) closure((blinkTime - .31f) / .16f) else 0f) * .97f)
        val cycle = floor(t / 8.7f).toInt()
        val p = (t % 8.7f) / 8.7f
        val gesture = sin((p * PI).toFloat()).pow(12)*idleWeight
        val kind = (cycle + seed).mod(4)
        headTilt = sin(t * .67f) * 1.8f * idleWeight
        headPitch = when (kind) {
            0 -> gesture * (9f + sin(t * 16f) * 3f) // sniff
            1 -> -gesture * 12f // yawn
            else -> 0f
        }
        mouthOpen = if (kind == 1) gesture else 0f
        shake = if (kind == 2) sin(t * 26f) * gesture * 5f else 0f
        headTilt += if (kind == 3) gesture * 9f else 0f
        gazeX = gaze.step(targetX.coerceIn(-1f, 1f) * .038f, dt, 75f)
        gazeY = gazeVertical.step(targetY.coerceIn(-1f, 1f) * .026f, dt, 75f)
        // Normalize full-turn reactions before blending so finishing a spin cannot rewind it.
        val yaw = target.bodyYawDegrees
        val yawTarget = channels[1].value + ((yaw - channels[1].value + 540f) % 360f - 180f)
        return LudoPaws3DReactionPose(
            liftY = channels[0].step(target.liftY, dt, 155f),
            bodyYawDegrees = channels[1].step(yawTarget, dt, 145f),
            headTiltDegrees = channels[2].step(target.headTiltDegrees, dt),
            primaryAppendageDegrees = channels[3].step(target.primaryAppendageDegrees, dt, 80f),
            secondaryAppendageDegrees = channels[4].step(target.secondaryAppendageDegrees, dt, 65f),
            tertiaryAppendageDegrees = channels[5].step(target.tertiaryAppendageDegrees, dt, 70f),
            scale = 1f + channels[6].step(target.scale - 1f, dt, 130f),
            bodyPitchDegrees = channels[7].step(target.bodyPitchDegrees, dt, 120f),
            neckPitchDegrees = channels[8].step(target.neckPitchDegrees, dt, 90f),
            jawOpen = channels[9].step(target.jawOpen, dt, 100f),
        )
    }
}
