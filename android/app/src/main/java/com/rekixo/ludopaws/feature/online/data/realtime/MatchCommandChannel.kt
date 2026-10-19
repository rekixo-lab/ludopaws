package com.rekixo.ludopaws

import org.json.JSONObject

/** Returns null when REST should retry the exact same idempotent command. */
fun interface MatchCommandChannel {
    fun executeCommand(
        matchId: String,
        playerToken: String,
        action: String,
        body: JSONObject,
    ): JSONObject?
}
