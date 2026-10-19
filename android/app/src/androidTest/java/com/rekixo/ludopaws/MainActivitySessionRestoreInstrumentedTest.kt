package com.rekixo.ludopaws

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySessionRestoreInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearRemoteStores()
        setDeviceNetworkEnabled(false)
        assertTrue(
            "test requires an offline device so restored credentials cannot hit production",
            waitForValidatedNetwork(expected = false),
        )
    }

    @After
    fun tearDown() {
        clearRemoteStores()
        setDeviceNetworkEnabled(true)
        waitForValidatedNetwork(expected = true)
    }

    @Test
    fun savedSessionSurvivesRecreateAndColdActivityRelaunch() {
        val expectedSession =
            PlayerSession(
                matchId = MATCH_ID,
                playerId = PLAYER_ID,
                playerToken = PLAYER_TOKEN,
                modeWire = GameMode.ONLINE.wireValue,
            )
        SecureSessionStore(context).save(expectedSession)

        launchResumeScenario().use { scenario ->
            assertRestored(scenario, expectedSession)

            scenario.recreate()

            assertRestored(scenario, expectedSession)
        }

        launchResumeScenario().use { coldScenario ->
            assertRestored(
                coldScenario,
                expectedSession,
            )
        }
    }

    private fun launchResumeScenario(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(
            Intent(context, MainActivity::class.java)
                .putExtra(
                    GameModeIntent.EXTRA_GAME_MODE,
                    GameMode.ONLINE.wireValue,
                )
                .putExtra(
                    GameModeIntent.EXTRA_RESUME_SAVED_MATCH,
                    true,
                ),
        )

    private fun assertRestored(
        scenario: ActivityScenario<MainActivity>,
        expectedSession: PlayerSession,
    ) {
        assertTrue(
            "the activity must observe the disabled emulator network before validating offline restore",
            waitForActivityOnline(scenario, expected = false),
        )

        scenario.onActivity { activity ->
            assertEquals(expectedSession.matchId, activity.matchId)
            assertEquals(expectedSession.playerId, activity.playerId)
            assertEquals(expectedSession.playerToken, activity.playerToken)
            assertEquals(GameMode.ONLINE, activity.gameMode)
            assertTrue(
                "the resume-saved-match intent flag must survive recreate/cold launch",
                activity.shouldRestoreSavedSession,
            )
            assertEquals(
                "ROLL DICE",
                activity.rollButton.text.toString(),
            )
            assertFalse(
                "restoring saved online credentials while the emulator is offline must not mark the activity online",
                activity.isOnline,
            )
        }

        assertEquals(
            expectedSession,
            SecureSessionStore(context).load(),
        )
    }

    private fun waitForActivityOnline(
        scenario: ActivityScenario<MainActivity>,
        expected: Boolean,
    ): Boolean {
        repeat(40) {
            var online: Boolean? = null
            scenario.onActivity { activity ->
                online = activity.isOnline
            }
            if (online == expected) return true
            // Do not sleep on the main thread: connectivity callbacks update
            // activity state through MainActivity's main-handler queue.
            SystemClock.sleep(100L)
        }
        return false
    }

    private fun setDeviceNetworkEnabled(enabled: Boolean) {
        val commands =
            if (enabled) {
                listOf(
                    "cmd connectivity airplane-mode disable",
                    "svc data enable",
                    "svc wifi enable",
                )
            } else {
                listOf(
                    "svc wifi disable",
                    "svc data disable",
                    "cmd connectivity airplane-mode enable",
                )
            }
        commands.forEach(::executeShellCommand)
        SystemClock.sleep(350L)
    }

    private fun waitForValidatedNetwork(
        expected: Boolean,
    ): Boolean {
        repeat(40) {
            if (hasValidatedNetwork() == expected) {
                return true
            }
            SystemClock.sleep(100L)
        }
        return hasValidatedNetwork() == expected
    }

    private fun hasValidatedNetwork(): Boolean {
        val manager =
            context.getSystemService(
                ConnectivityManager::class.java,
            )
                ?: return false
        val active =
            manager.activeNetwork
                ?: return false
        val capabilities =
            manager.getNetworkCapabilities(active)
                ?: return false
        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED,
        )
    }

    private fun executeShellCommand(command: String) {
        val descriptor =
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .executeShellCommand(command)
        ParcelFileDescriptor
            .AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { reader ->
                val output = reader.readText()
                assertFalse(
                    "shell command failed: $command -> $output",
                    output.contains("unknown command", ignoreCase = true) ||
                        output.contains("permission denial", ignoreCase = true) ||
                        output.contains("not found", ignoreCase = true),
                )
            }
    }

    private fun clearRemoteStores() {
        SecureSessionStore(context).clear()
        CachedMatchStore(context).clear()
        PublicMatchmakingStore(context).clear()
    }

    private companion object {
        const val MATCH_ID = "LPABCDEFGH"
        const val PLAYER_ID = "player-session-restore"
        const val PLAYER_TOKEN =
            "lp_abcdefghijklmnopqrstuvwxyzABCDEF"
    }
}
