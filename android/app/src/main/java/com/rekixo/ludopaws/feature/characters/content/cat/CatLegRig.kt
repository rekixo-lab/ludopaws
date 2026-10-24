package com.rekixo.ludopaws.feature.characters.content.cat

import kotlin.math.*

/** Analytic two-bone IK in the sagittal plane. Paws stay planted in idle instead of skating. */
internal object CatLegRig {
    const val UPPER_LENGTH = .34f
    const val LOWER_LENGTH = .34f
    const val GROUND_ANKLE_Y = -.74f

    // Output: hipY/Z, kneeY/Z, ankleY/Z. Caller reuses storage, including on every render frame.
    fun solve(rear: Boolean, side: Float, strideDegrees: Float, tuckDegrees: Float, out: FloatArray, crouch: Float = 0f, pairedStride: Boolean = false) {
        require(out.size >= 6)
        val hipY = (if (rear) -.09f else -.07f) - crouch.coerceIn(0f, .14f)
        val hipZ = if (rear) -.30f else .12f
        val tuck = (tuckDegrees / 38f).coerceIn(0f, 1f)
        val diagonal = if (pairedStride) 1f else if (rear) -side else side
        val ankleY = GROUND_ANKLE_Y + tuck * .22f
        val ankleZ = hipZ + (if (rear) -.045f else .035f) +
            sin(strideDegrees * diagonal * PI.toFloat() / 180f) * .12f + tuck * (if (rear) -.08f else .08f)
        val dy = ankleY - hipY; val dz = ankleZ - hipZ
        val rawDistance = sqrt(dy * dy + dz * dz).coerceAtLeast(.0001f)
        val distance = rawDistance.coerceIn(.05f, UPPER_LENGTH + LOWER_LENGTH - .0001f)
        val along = (UPPER_LENGTH.pow(2) - LOWER_LENGTH.pow(2) + distance.pow(2)) / (2f * distance)
        val height = sqrt((UPPER_LENGTH.pow(2) - along.pow(2)).coerceAtLeast(0f))
        val uy = dy / rawDistance; val uz = dz / rawDistance
        val bend = if (rear) -1f else 1f
        out[0] = hipY; out[1] = hipZ
        out[2] = hipY + uy * along - uz * height * bend
        out[3] = hipZ + uz * along + uy * height * bend
        // The requested poses are within reach; this clamp also bounds future exaggerated cues.
        out[4] = hipY + uy * distance; out[5] = hipZ + uz * distance
    }
}
