package com.rekixo.ludopaws

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rekixo.ludopaws.feature.online.applyResponse
import com.rekixo.ludopaws.feature.online.updateControls
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemoteMatchUiInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearRemoteStores()
    }

    @After
    fun tearDown() {
        clearRemoteStores()
    }

    @Test
    fun teamUpPartnerHandoffUsesActingSeatAndNetworkLossDisablesActions() {
        launchTeamUp().use { scenario ->
            scenario.onActivity { activity ->
                // An authoritative response must arrive while online. Applying it
                // offline intentionally invalidates action authority, even if the
                // UI is later toggled back online without a fresh server snapshot.
                activity.isOnline = true
                activity.applyResponse(
                    validTeamUpEnvelope(),
                    announce = false,
                )

                assertEquals(
                    "YOUR TEAM TURN • PARTNER HANDOFF",
                    activity.turnText.text.toString(),
                )
                assertEquals(2, activity.currentState?.actingSeat)

                activity.updateControls(requireNotNull(activity.currentState))
                assertTrue(activity.rollButton.isEnabled)
                assertTrue(activity.refreshButton.isEnabled)

                activity.isOnline = false
                activity.updateControls(requireNotNull(activity.currentState))
                assertFalse(activity.rollButton.isEnabled)
                assertFalse(activity.refreshButton.isEnabled)

                // Reconnection alone must not reactivate a cached roll.
                activity.isOnline = true
                activity.updateControls(requireNotNull(activity.currentState))
                assertFalse(activity.rollButton.isEnabled)

                // Only a new valid authoritative revision re-enables the turn.
                val refreshed = validTeamUpEnvelope()
                refreshed.getJSONObject("state").put("revision", 8)
                activity.applyResponse(refreshed, announce = false)
                activity.updateControls(requireNotNull(activity.currentState))
                assertTrue(activity.rollButton.isEnabled)
            }
        }
    }

    @Test
    fun malformedAuthoritativeRefreshKeepsLastKnownGoodState() {
        launchTeamUp().use { scenario ->
            scenario.onActivity { activity ->
                val valid = validTeamUpEnvelope()
                activity.applyResponse(valid, announce = false)
                val safeState = requireNotNull(activity.currentState)

                val malformed = JSONObject(valid.toString())
                malformed
                    .getJSONObject("state")
                    .put("turnSeat", JSONObject.NULL)

                activity.applyResponse(malformed, announce = false)

                assertEquals(safeState, activity.currentState)
                assertTrue(
                    activity.statusText.text
                        .toString()
                        .contains("last verified state was kept"),
                )
            }
        }
    }

    @Test
    fun replayingSameSnapshotDoesNotChangeTurnOwnership() {
        launchTeamUp().use { scenario ->
            scenario.onActivity { activity ->
                val response = validTeamUpEnvelope()

                activity.applyResponse(response, announce = false)
                val firstState = requireNotNull(activity.currentState)
                val firstTurnText = activity.turnText.text.toString()

                activity.applyResponse(
                    JSONObject(response.toString()),
                    announce = false,
                )

                assertEquals(firstState, activity.currentState)
                assertEquals(firstTurnText, activity.turnText.text.toString())
            }
        }
    }

    private fun launchTeamUp(): ActivityScenario<MainActivity> {
        val scenario: ActivityScenario<MainActivity> =
            ActivityScenario.launch<MainActivity>(
                Intent(context, MainActivity::class.java)
                    .putExtra(
                        GameModeIntent.EXTRA_GAME_MODE,
                        GameMode.TEAM_UP.wireValue,
                    )
                    .putExtra(
                        GameModeIntent.EXTRA_RESUME_SAVED_MATCH,
                        false,
                    ),
            )
        scenario.onActivity { activity: MainActivity ->
            // These tests verify authoritative-state handling and action gating.
            // Skip the GL-heavy board/portrait composition so headless SwiftShader
            // load cannot turn a UI contract test into an emulator stability test.
            activity.onlinePresentationRendererOverrideForTests = { _, _ -> }
        }
        return scenario
    }

    private fun validTeamUpEnvelope(): JSONObject {
        val players =
            JSONArray()
                .put(player("p1", "Red", 0, "RED", "A", "duck"))
                .put(player("p2", "Green", 1, "GREEN", "B", "squirrel"))
                .put(player("p3", "Yellow", 2, "YELLOW", "A", "hedgehog"))
                .put(player("p4", "Blue", 3, "BLUE", "B", "sheep"))

        val state =
            JSONObject()
                .put("schemaVersion", 1)
                .put("matchId", MATCH_ID)
                .put("status", "ACTIVE")
                .put("createdAt", 1_700_000_000_000L)
                .put("updatedAt", 1_700_000_000_500L)
                .put("revision", 7)
                .put("hostPlayerId", "p1")
                .put("targetPlayerCount", 4)
                .put("matchMode", "TEAM_UP")
                .put("teamAssignments", JSONArray(listOf("A", "B", "A", "B")))
                .put("actingSeat", 2)
                .put("winnerTeamId", JSONObject.NULL)
                .put("players", players)
                .put("turnSeat", 0)
                .put("randomEventIndex", 0)
                .put("consecutiveSixes", JSONArray(listOf(0, 0, 0, 0)))
                .put("pendingRoll", JSONObject.NULL)
                .put("winnerPlayerId", JSONObject.NULL)
                .put("rulesetId", "ludoproof-team-v1")
                .put("history", JSONArray())

        return JSONObject()
            .put("playerId", "p3")
            .put("state", state)
    }

    private fun player(
        id: String,
        name: String,
        seat: Int,
        color: String,
        teamId: String,
        characterId: String,
    ): JSONObject =
        JSONObject()
            .put("playerId", id)
            .put("displayName", name)
            .put("seat", seat)
            .put("color", color)
            .put("teamId", teamId)
            .put("characterId", characterId)
            .put("tokens", JSONArray(listOf(-1, -1, -1, -1)))

    private fun clearRemoteStores() {
        SecureSessionStore(context).clear()
        CachedMatchStore(context).clear()
        PublicMatchmakingStore(context).clear()
    }

    private companion object {
        const val MATCH_ID = "LPABCDEFGH"
    }
}
