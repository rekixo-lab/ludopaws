package com.rekixo.ludopaws

/** Do not retry authentication, removed-room or upgrade failures every few seconds. */
internal object RealtimeConnectionFailurePolicy {
    fun isTerminalHttp(status: Int): Boolean =
        status in 400..499 && status !in setOf(408, 425, 429)

    fun userMessage(status: Int): String = when (status) {
        401, 403 -> "Online session is unavailable. Please check access or sign in again."
        404 -> "This match is no longer available."
        410, 426 -> "Please update Ludo Paws to keep playing online."
        else -> "This online connection was rejected. Return Home and try again."
    }
}
