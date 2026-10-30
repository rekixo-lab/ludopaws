package com.rekixo.ludopaws

internal object FirstMatchCoachPolicy {
    fun hint(firstMatch: Boolean, enabled: Boolean, active: Boolean, localTurn: Boolean, choices: Int?): String? {
        if (!firstMatch || !enabled || !active || !localTurn) return null
        return if (choices != null) {
            if (choices > 0) "Choose a glowing paw. Shield cells are safe." else null
        } else "Tap your character's dice to begin."
    }
}
