package com.rekixo.ludopaws
// Minimal field-compatible stubs of GameModels.kt (only fields the policy reads).
data class PlayerSnapshot(val playerId: String, val displayName: String, val color: String, val seat: Int, val tokens: List<Int>, val teamId: String? = null)
data class PendingRollSnapshot(val comebackBonusApplied: Boolean = false)
data class MatchSnapshot(val players: List<PlayerSnapshot>, val pendingRoll: PendingRollSnapshot?)
