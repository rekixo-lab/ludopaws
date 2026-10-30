package com.rekixo.ludopaws.feature.characters.content.panda

/** Smooth stand -> four planted paws -> stand cycle, independent of render frame rate. */
internal object PandaPosture {
    private fun ease(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return (t * t * t * (t * (t * 6f - 15f) + 10f)).coerceIn(0f, 1f)
    }

    fun allFours(timeSeconds: Float, idleWeight: Float): Float {
        val p = ((timeSeconds % 12f) + 12f) % 12f
        val lower = ease((p - 1.2f) / 1.8f)
        val rise = ease((p - 6f) / 1.8f)
        return lower * (1f - rise) * idleWeight.coerceIn(0f, 1f)
    }
}
