package com.rekixo.ludopaws

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import com.rekixo.ludopaws.ui.offline.gameplay.showGame
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rekixo.ludopaws.feature.offline.domain.ai.LudoPawsComputerMovePolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineFullMatchInstrumentedTest {
    @Test
    fun normalOfflineLaunchRecreationReturnsToTheActiveBoard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<OfflineGameActivity>(Intent(context, OfflineGameActivity::class.java)
            .putExtra(GameModeIntent.EXTRA_GAME_MODE, GameMode.PASS_AND_PLAY.wireValue)).use { scenario ->
            var matchId: String? = null
            scenario.onActivity { activity ->
                activity.session.clear()
                val state = activity.session.start(2, "RED")
                matchId = state.matchId
                activity.showGame(state)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(matchId, activity.session.snapshot()?.matchId)
                assertNotNull("recreation must render the board instead of setup", activity.boardView)
                assertTrue(requireNotNull(activity.boardView).isAttachedToWindow)
                activity.session.clear()
            }
        }
    }

    @Test
    fun newMatchesUseV5AndUnversionedSavesKeepTheirOriginalV3Contract() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = OfflineGameEngine(context, GameMode.PASS_AND_PLAY)
        engine.clear()
        try {
            assertEquals(OfflineLudoV5Binding.RULESET_ID,
                engine.start(playerCount = 2, preferredColor = "RED").rulesetId)
            val prefs = context.getSharedPreferences("ludoproof_offline_game", Context.MODE_PRIVATE)
            val saved = JSONObject(requireNotNull(prefs.getString("state", null)))
            saved.remove("rulesetId")
            prefs.edit().putString("state", saved.toString()).commit()
            val restored = OfflineGameEngine(context, GameMode.PASS_AND_PLAY)
            assertEquals(OfflineLudoV3Binding.RULESET_ID, restored.snapshot()?.rulesetId)
            assertEquals(OfflineLudoV3Binding.RULESET_ID, restored.roll().rulesetId)
        } finally { engine.clear() }
    }

    @Test
    fun opponentStacksAreNotCapturedAsAGroup() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()
        val preferences =
            context.getSharedPreferences(
                "ludoproof_offline_game",
                Context.MODE_PRIVATE,
            )

        for (stackSize in listOf(2, 3, 4)) {
            val session =
                LocalMatchSession(
                    context = context,
                    mode = GameMode.PASS_AND_PLAY,
                )
            session.clear()

            try {
                val rolled =
                    session.start(
                        playerCount = 2,
                        preferredColor = "RED",
                    ).let {
                        session.roll()
                    }
                val outcome =
                    requireNotNull(rolled.pendingRoll?.outcome)
                val destination =
                    requireNotNull(
                        LudoPathEncoding.destinationForRoll(
                            position = 1,
                            roll = outcome,
                        ),
                    )
                val targetGlobalCell = destination
                require(targetGlobalCell in 0..50)
                val opponentPosition =
                    (targetGlobalCell - 13 + 52) % 52

                val saved =
                    JSONObject(
                        requireNotNull(
                            preferences.getString(
                                "state",
                                null,
                            ),
                        ),
                    )
                val players =
                    saved.getJSONArray("players")
                players
                    .getJSONObject(0)
                    .put(
                        "tokens",
                        JSONArray(listOf(1, -1, -1, -1)),
                    )
                players
                    .getJSONObject(1)
                    .put(
                        "tokens",
                        JSONArray(
                            List(4) { index ->
                                if (index < stackSize) {
                                    opponentPosition
                                } else {
                                    -1
                                }
                            },
                        ),
                    )
                preferences.edit()
                    .putString("state", saved.toString())
                    .commit()

                val restored =
                    OfflineGameEngine(
                        context = context,
                        mode = GameMode.PASS_AND_PLAY,
                    )
                val moved =
                    restored.move(0)

                assertEquals(
                    destination,
                    moved.players[0].tokens[0],
                )
                assertEquals(
                    0,
                    moved.history.last().captures,
                )
                assertEquals(
                    List(stackSize) { opponentPosition },
                    moved.players[1].tokens.take(stackSize),
                )
            } finally {
                session.clear()
            }
        }
    }

    @Test
    fun twoAndFourPlayerMatchesFinishThroughProductionEngineAndCpuPolicy() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        for (playerCount in listOf(2, 4)) {
            runCompleteMatch(
                context = context,
                playerCount = playerCount,
            )
        }
    }

    private fun runCompleteMatch(
        context: Context,
        playerCount: Int,
    ) {
        val session =
            LocalMatchSession(
                context = context,
                mode = GameMode.COMPUTER,
            )
        session.clear()

        try {
            var state =
                session.start(
                    playerCount = playerCount,
                    preferredColor = "RED",
                )
            var transitions = 0

            while (
                state.status == "ACTIVE" &&
                transitions < MAX_TRANSITIONS
            ) {
                state =
                    if (state.pendingRoll == null) {
                        session.roll()
                    } else {
                        val pending = requireNotNull(state.pendingRoll)
                        val outcome = requireNotNull(pending.outcome)
                        val active = state.players[state.turnSeat]
                        val tokenIndex =
                            LudoPawsComputerMovePolicy.chooseToken(
                                state = state,
                                player = active,
                                legalTokenIndexes = pending.legalTokenIndexes,
                                outcome = outcome,
                            )
                        assertNotNull(
                            "CPU policy must choose from every authoritative legal set",
                            tokenIndex,
                        )
                        session.move(requireNotNull(tokenIndex))
                    }

                // The live LocalMatchSession already runs this policy. Calling it
                // explicitly here makes a future accidental bypass visible in the
                // end-to-end contract as well.
                OfflineMatchInvariantPolicy.requireValid(state)
                transitions += 1
            }

            assertTrue(
                "$playerCount-player local match exceeded $MAX_TRANSITIONS transitions",
                transitions < MAX_TRANSITIONS,
            )
            assertEquals(
                "$playerCount-player local match must finish",
                "FINISHED",
                state.status,
            )

            val finishedPlayers =
                state.players.count {
                    it.tokens.all { position ->
                        position == LudoPathEncoding.HOME_POSITION
                    }
                }
            assertTrue(
                "$playerCount-player match must finish only after all but the last player are placed",
                finishedPlayers >= playerCount - 1,
            )

            val winnerId = state.winnerPlayerId
            assertNotNull(
                "$playerCount-player finished match must expose a winner",
                winnerId,
            )
            val winner =
                state.players.firstOrNull {
                    it.playerId == winnerId
                }
            assertNotNull(
                "$playerCount-player winner must still be seated",
                winner,
            )
            assertTrue(
                "$playerCount-player winner must have four exact-home tokens",
                requireNotNull(winner).tokens.all {
                    it == LudoPathEncoding.HOME_POSITION
                },
            )
        } finally {
            session.clear()
        }
    }

    private companion object {
        const val MAX_TRANSITIONS = 6_000
    }
}
