package com.rekixo.ludopaws.feature.profile.domain

/** Finite, cosmetic milestones. No currency, expiry, streak reset or gameplay bonuses. */
object CharacterMasteryPolicy {
    val milestones = listOf(1, 3, 5, 10, 20)
    fun nextGoal(matches: Int): Int? = milestones.firstOrNull { it > matches.coerceAtLeast(0) }
    fun tier(matches: Int): String = when {
        matches >= 20 -> "Legendary pal"
        matches >= 10 -> "Best buddy"
        matches >= 5 -> "Good friend"
        matches >= 3 -> "Playful pal"
        matches >= 1 -> "New friend"
        else -> "Ready to play"
    }
    fun frameColor(matches: Int): Int = when {
        matches >= 20 -> 0xFFFFCD52.toInt()
        matches >= 10 -> 0xFFCB8CFF.toInt()
        matches >= 5 -> 0xFF54E6A1.toInt()
        matches >= 3 -> 0xFF65CFFF.toInt()
        else -> 0xFFE9D5A7.toInt()
    }
}
