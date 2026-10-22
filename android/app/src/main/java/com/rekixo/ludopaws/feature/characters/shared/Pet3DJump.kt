package com.rekixo.ludopaws.feature.characters.shared

import kotlin.math.*

internal object Pet3DJump {
    fun height(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return when {
            p < .13f -> -.065f * sin(p / .13f * PI).toFloat()
            p < .82f -> {
                val t = (p - .13f) / .69f
                4f * t * (1f - t)
            }
            else -> {
                val t = (p - .82f) / .18f
                -.075f * sin(t * PI * 2).toFloat() * (1f - t)
            }
        }
    }
}
