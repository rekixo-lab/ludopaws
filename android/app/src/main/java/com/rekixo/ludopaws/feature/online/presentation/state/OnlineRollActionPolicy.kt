package com.rekixo.ludopaws.feature.online

enum class OnlineRollActionKind { NEW_ROLL, MOVE_REQUIRED, WAIT_FOR_SYNC, DISABLED }

data class OnlineRollActionDecision(
    val kind: OnlineRollActionKind,
    val enabled: Boolean,
    val label: String,
)

/** Cached snapshots never authorize a command. Resolved dice must be consumed first. */
object OnlineRollActionPolicy {
    fun resolve(
        isOnline: Boolean,
        hasAuthoritativeState: Boolean,
        matchStatus: String,
        myTurn: Boolean,
        pendingStatus: String?,
    ): OnlineRollActionDecision = when {
        matchStatus != "ACTIVE" || !myTurn ->
            OnlineRollActionDecision(OnlineRollActionKind.DISABLED, false, "ROLL DICE")
        !isOnline ->
            OnlineRollActionDecision(OnlineRollActionKind.DISABLED, false, "OFFLINE • WAITING")
        !hasAuthoritativeState ->
            OnlineRollActionDecision(OnlineRollActionKind.DISABLED, false, "SYNCING MATCH")
        pendingStatus == "RESOLVED" ->
            OnlineRollActionDecision(OnlineRollActionKind.MOVE_REQUIRED, false, "MOVE A TOKEN")
        pendingStatus != null ->
            OnlineRollActionDecision(OnlineRollActionKind.WAIT_FOR_SYNC, false, "SYNCING MATCH")
        else ->
            OnlineRollActionDecision(OnlineRollActionKind.NEW_ROLL, true, "ROLL DICE")
    }
}
