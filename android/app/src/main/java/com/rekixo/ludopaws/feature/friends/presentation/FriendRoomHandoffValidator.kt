package com.rekixo.ludopaws.feature.friends.presentation

import java.util.Locale
import org.json.JSONObject
import com.rekixo.ludopaws.GameJson

/**
 * Validate the server-authoritative Friends match handoff before persisting tokens.
 * No UI or Activity state is required; caller supplies expected authenticated identity.
 */
internal fun isValidFriendRoomHandoff(
        response: JSONObject,
        expectedMatchId: String,
        expectedPlayerId: String,
        expectedPlayerCount: Int?,
    ): Boolean {
        if (
            expectedMatchId.isBlank() ||
            expectedPlayerId.isBlank() ||
            response.optString("playerToken").isBlank() ||
            response.optString("matchId").trim().uppercase(Locale.ROOT) != expectedMatchId
        ) {
            return false
        }

        val state = runCatching { GameJson.envelope(response).state }.getOrNull()
            ?: return false
        return state.matchId == expectedMatchId &&
            state.matchMode == "FRIENDS" &&
            state.status in setOf("WAITING", "ACTIVE") &&
            state.targetPlayerCount != null &&
            (expectedPlayerCount == null || state.targetPlayerCount == expectedPlayerCount) &&
            state.players.any { it.playerId == expectedPlayerId }
    }
